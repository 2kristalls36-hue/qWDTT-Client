package main

// Запросы приложения к HTTP API сервера (срок подписки, устройства, перенос)
// через уже поднятый RAW-туннель.
//
// Приложение исключено из собственного VPN (иначе трафик клиента до TURN
// зациклился бы в туннель), поэтому напрямую до API оно доходит не всегда:
// мобильные сети с белыми списками режут прямой путь к VPS. Здесь клиент сам
// открывает TCP-соединение внутри туннеля: маленький сетевой стек gVisor
// с IP туннеля и отдельным диапазоном портов (Android свои эфемерные порты
// берёт из 32768–60999, пересечений нет). Приложение подключается к
// локальному порту 127.0.0.1, всё остальное делает клиент.

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"log"
	"net"
	"net/netip"
	"strings"
	"sync"

	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
)

const (
	apiPortLo = 61000
	apiPortHi = 61999
)

type apiTunnel struct {
	ip    [4]byte
	ep    *channel.Endpoint
	stack *stack.Stack
}

// newAPITunnel создаёт стек с адресом туннеля. up получает каждый исходящий
// IP-пакет стека (его нужно отправить в туннель как обычный трафик).
func newAPITunnel(ctx context.Context, tunIP netip.Addr, mtu int, up func([]byte)) (*apiTunnel, error) {
	if !tunIP.Is4() {
		return nil, fmt.Errorf("нужен IPv4-адрес туннеля, получен %v", tunIP)
	}
	if mtu <= 0 {
		mtu = 1280
	}
	s := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol},
	})
	ep := channel.New(256, uint32(mtu), "")
	if err := s.CreateNIC(1, ep); err != nil {
		return nil, fmt.Errorf("CreateNIC: %v", err)
	}
	if err := s.AddProtocolAddress(1, tcpip.ProtocolAddress{
		Protocol:          ipv4.ProtocolNumber,
		AddressWithPrefix: tcpip.AddrFrom4(tunIP.As4()).WithPrefix(),
	}, stack.AddressProperties{}); err != nil {
		return nil, fmt.Errorf("AddProtocolAddress: %v", err)
	}
	s.AddRoute(tcpip.Route{Destination: header.IPv4EmptySubnet, NIC: 1})
	if err := s.SetPortRange(apiPortLo, apiPortHi); err != nil {
		return nil, fmt.Errorf("SetPortRange: %v", err)
	}
	t := &apiTunnel{ip: tunIP.As4(), ep: ep, stack: s}
	go func() {
		for {
			pkt := ep.ReadContext(ctx)
			if pkt == nil {
				return
			}
			view := pkt.ToView()
			pkt.DecRef()
			up(view.AsSlice())
			view.Release()
		}
	}()
	return t, nil
}

// owns — ответ на соединение стека: TCP на адрес туннеля в диапазон портов стека.
func (t *apiTunnel) owns(pkt []byte) bool {
	if len(pkt) < 20 || pkt[0]>>4 != 4 || pkt[9] != 6 {
		return false
	}
	ihl := int(pkt[0]&0x0f) * 4
	if ihl < 20 || len(pkt) < ihl+4 {
		return false
	}
	if pkt[16] != t.ip[0] || pkt[17] != t.ip[1] || pkt[18] != t.ip[2] || pkt[19] != t.ip[3] {
		return false
	}
	port := binary.BigEndian.Uint16(pkt[ihl+2 : ihl+4])
	return port >= apiPortLo && port <= apiPortHi
}

// deliver отдаёт пакет стеку (данные копируются, буфер можно освобождать).
func (t *apiTunnel) deliver(pkt []byte) {
	pkb := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(pkt)})
	t.ep.InjectInbound(header.IPv4ProtocolNumber, pkb)
	pkb.DecRef()
}

func (t *apiTunnel) dial(ctx context.Context, target netip.AddrPort) (net.Conn, error) {
	return gonet.DialContextTCP(ctx, t.stack, tcpip.FullAddress{
		NIC:  1,
		Addr: tcpip.AddrFrom4(target.Addr().As4()),
		Port: target.Port(),
	}, ipv4.ProtocolNumber)
}

func (t *apiTunnel) close() {
	t.stack.Close()
	t.ep.Close()
}

// serveAPIProxy принимает соединения на listenAddr (только 127.0.0.1) и
// пробрасывает каждое внутри туннеля на target (адрес HTTP API сервера).
func serveAPIProxy(ctx context.Context, listenAddr string, target netip.AddrPort, t *apiTunnel) error {
	if !strings.HasPrefix(listenAddr, "127.0.0.1:") {
		return fmt.Errorf("адрес прокси API должен быть 127.0.0.1:порт, получен %q", listenAddr)
	}
	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		return err
	}
	go func() {
		<-ctx.Done()
		ln.Close()
	}()
	log.Printf("[API] Запросы к серверу через туннель: %s → %s", listenAddr, target)
	for {
		c, err := ln.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return err
		}
		go func(local net.Conn) {
			defer local.Close()
			remote, err := t.dial(ctx, target)
			if err != nil {
				log.Printf("[API] Соединение через туннель не установлено: %v", err)
				return
			}
			defer remote.Close()
			var wg sync.WaitGroup
			wg.Add(2)
			go func() { defer wg.Done(); io.Copy(remote, local); closeWrite(remote) }()
			go func() { defer wg.Done(); io.Copy(local, remote); closeWrite(local) }()
			wg.Wait()
		}(c)
	}
}

func closeWrite(c net.Conn) {
	if cw, ok := c.(interface{ CloseWrite() error }); ok {
		_ = cw.CloseWrite()
	}
}
