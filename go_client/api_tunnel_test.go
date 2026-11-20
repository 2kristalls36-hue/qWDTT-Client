package main

import (
	"context"
	"encoding/binary"
	"io"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
)

// A fake «server side of the tunnel»: its own stack with an HTTP API on 10.9.0.1:56000.
// Packets of the client stack go straight to it; its replies come back through owns/deliver,
// the same way the dispatcher routes them in rawtun.
func TestAPIProxyReachesServerThroughTunnel(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	srvStack := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol},
	})
	srvEP := channel.New(256, 1280, "")
	if err := srvStack.CreateNIC(1, srvEP); err != nil {
		t.Fatal(err)
	}
	srvStack.AddProtocolAddress(1, tcpip.ProtocolAddress{
		Protocol:          ipv4.ProtocolNumber,
		AddressWithPrefix: tcpip.AddrFrom4([4]byte{10, 9, 0, 1}).WithPrefix(),
	}, stack.AddressProperties{})
	srvStack.AddRoute(tcpip.Route{Destination: header.IPv4EmptySubnet, NIC: 1})
	ln, err := gonet.ListenTCP(srvStack, tcpip.FullAddress{NIC: 1, Port: 56000}, ipv4.ProtocolNumber)
	if err != nil {
		t.Fatal(err)
	}
	go http.Serve(ln, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, `{"error":"Method not allowed"}`, http.StatusMethodNotAllowed)
	}))

	var client *apiTunnel
	var sawPort uint16
	// Client → server.
	up := func(b []byte) {
		ihl := int(b[0]&0x0f) * 4
		sawPort = binary.BigEndian.Uint16(b[ihl : ihl+2])
		pkb := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(b)})
		srvEP.InjectInbound(header.IPv4ProtocolNumber, pkb)
		pkb.DecRef()
	}
	client, err = newAPITunnel(ctx, netip.MustParseAddr("10.9.0.2"), 1280, up)
	if err != nil {
		t.Fatal(err)
	}
	// Server → client, filtered like the dispatcher does.
	go func() {
		for {
			pkt := srvEP.ReadContext(ctx)
			if pkt == nil {
				return
			}
			v := pkt.ToView()
			pkt.DecRef()
			b := v.AsSlice()
			if client.owns(b) {
				client.deliver(b)
			} else {
				t.Errorf("reply not recognised as API traffic")
			}
			v.Release()
		}
	}()

	go serveAPIProxy(ctx, "127.0.0.1:19123", netip.MustParseAddrPort("10.9.0.1:56000"), client)
	time.Sleep(200 * time.Millisecond)

	hc := &http.Client{Timeout: 5 * time.Second}
	resp, err := hc.Get("http://127.0.0.1:19123/api/profile/challenge")
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != 405 || !strings.Contains(string(body), "Method not allowed") {
		t.Fatalf("got %d %q", resp.StatusCode, body)
	}
	if sawPort < apiPortLo || sawPort > apiPortHi {
		t.Fatalf("client source port %d is outside %d-%d", sawPort, apiPortLo, apiPortHi)
	}
}

func TestOwnsIgnoresOtherTraffic(t *testing.T) {
	a := &apiTunnel{ip: [4]byte{10, 9, 0, 2}}
	pkt := make([]byte, 40)
	pkt[0] = 0x45
	pkt[9] = 6
	copy(pkt[16:20], []byte{10, 9, 0, 2})
	binary.BigEndian.PutUint16(pkt[22:24], 61500)
	if !a.owns(pkt) {
		t.Fatal("API port not recognised")
	}
	binary.BigEndian.PutUint16(pkt[22:24], 45000) // an Android app's port
	if a.owns(pkt) {
		t.Fatal("app traffic taken by the API stack")
	}
	pkt[9] = 17 // UDP
	binary.BigEndian.PutUint16(pkt[22:24], 61500)
	if a.owns(pkt) {
		t.Fatal("UDP taken by the API stack")
	}
	copy(pkt[16:20], []byte{10, 9, 0, 3})
	pkt[9] = 6
	if a.owns(pkt) {
		t.Fatal("other address taken by the API stack")
	}
	_ = net.IPv4len
}
