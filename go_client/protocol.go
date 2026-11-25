package main

import (
	"fmt"
	"net"
	"strconv"
	"strings"
	"time"
)

// configRetryMarker — начало ошибки сессии, не получившей конфиг туннеля:
// поток сразу повторяет запрос (см. fastRetry в group.go).
const configRetryMarker = "конфиг не получен"

// rawConfigReadTimeout — сколько ждать ответ на GETCONF_RAW. Запрос и ответ —
// по одному UDP-пакету через TURN; потерянный ответ раньше ждали 45 с, дольше
// таймаута шага в приложении (25 с), и повтор не успевал. Сервер отвечает за
// доли секунды, 8 с — с запасом на медленный relay.
const rawConfigReadTimeout = 8 * time.Second

// RequestConfig запрашивает WireGuard конфиг через DTLS-соединение.
func RequestConfig(conn net.Conn, localPort, deviceID, password string) (string, error) {
	payload := fmt.Sprintf("GETCONF:%s|%s|%s", localPort, deviceID, password)
	if _, err := conn.Write([]byte(payload)); err != nil {
		return "", fmt.Errorf("отправка GETCONF: %w", err)
	}

	b := make([]byte, 4096)
	if err := conn.SetReadDeadline(time.Now().Add(45 * time.Second)); err != nil {
		return "", fmt.Errorf("установка дедлайна: %w", err)
	}
	n, err := conn.Read(b)
	_ = conn.SetReadDeadline(time.Time{})
	if err != nil {
		return "", fmt.Errorf("чтение ответа конфига: %w", err)
	}

	resp := string(b[:n])
	if resp == "NOCONF" {
		return "", nil
	}

	if strings.HasPrefix(resp, "DENIED:") {
		reason := strings.TrimPrefix(resp, "DENIED:")
		switch reason {
		case "wrong_password":
			return "", fmt.Errorf("FATAL_AUTH: неверный пароль подключения")
		case "expired":
			return "", fmt.Errorf("FATAL_AUTH: срок действия пароля истёк")
		case "device_mismatch":
			return "", fmt.Errorf("FATAL_AUTH: пароль привязан к другому устройству")
		default:
			return "", fmt.Errorf("FATAL_AUTH: доступ запрещён (%s)", reason)
		}
	}

	return resp, nil
}

// connGeneration — поколение подключения: время запуска движка в мс. При смене
// сети приложение перезапускает движок, поколение растёт, и сервер qWDTT с
// поддержкой поколений сразу снимает потоки прошлого запуска того же
// устройства, а не раздаёт в них трафик до своего таймаута простоя
// (SpaceNeuroX/proxy-turn-vk-android#52, docs/ideas/server-session-generations.md).
// Серверы без поддержки именованное поле не распознают и работают как раньше.
var connGeneration = time.Now().UnixMilli()

// helloFields — поля после префикса AUTH:/GETCONF_RAW: в raw-режиме. Поколение —
// именованным полем gen=: в сервере WDTT-панели третье поле занято под MTU.
func helloFields(deviceID, password string, gen int64) string {
	if gen > 0 {
		return fmt.Sprintf("%s|%s|gen=%d", deviceID, password, gen)
	}
	return fmt.Sprintf("%s|%s", deviceID, password)
}

// SendAuth отправляет команду авторизации, чтобы сервер мог связать соединение с устройством.
// gen > 0 — поколение подключения (только raw-режим, см. connGeneration).
func SendAuth(conn net.Conn, deviceID, password string, gen int64) error {
	payload := "AUTH:" + helloFields(deviceID, password, gen)
	if _, err := conn.Write([]byte(payload)); err != nil {
		return fmt.Errorf("отправка AUTH: %w", err)
	}

	return nil
}

// RequestRawConfig запрашивает у сервера конфигурацию для raw-IP режима
// (без WireGuard) — сервер отвечает "RAWCONF:ip|dns|mtu" (см. server/raw.go
// handleConnRaw). ip пусто на первый вызов, если сервер ещё не назначил его.
func RequestRawConfig(conn net.Conn, deviceID, password string, gen int64) (ip, dnsCSV string, mtu int, err error) {
	payload := "GETCONF_RAW:" + helloFields(deviceID, password, gen)
	if _, err = conn.Write([]byte(payload)); err != nil {
		return "", "", 0, fmt.Errorf("отправка GETCONF_RAW: %w", err)
	}

	b := make([]byte, 4096)
	if err = conn.SetReadDeadline(time.Now().Add(rawConfigReadTimeout)); err != nil {
		return "", "", 0, fmt.Errorf("установка дедлайна: %w", err)
	}
	n, readErr := conn.Read(b)
	_ = conn.SetReadDeadline(time.Time{})
	if readErr != nil {
		return "", "", 0, fmt.Errorf("чтение ответа RAWCONF: %w", readErr)
	}

	resp := string(b[:n])
	if resp == "NOCONF" {
		return "", "", 0, nil
	}
	if strings.HasPrefix(resp, "DENIED:") {
		reason := strings.TrimPrefix(resp, "DENIED:")
		switch reason {
		case "wrong_password":
			return "", "", 0, fmt.Errorf("FATAL_AUTH: неверный пароль подключения")
		case "expired":
			return "", "", 0, fmt.Errorf("FATAL_AUTH: срок действия пароля истёк")
		case "device_mismatch":
			return "", "", 0, fmt.Errorf("FATAL_AUTH: пароль привязан к другому устройству")
		default:
			return "", "", 0, fmt.Errorf("FATAL_AUTH: доступ запрещён (%s)", reason)
		}
	}
	if !strings.HasPrefix(resp, "RAWCONF:") {
		return "", "", 0, fmt.Errorf("неожиданный ответ RAWCONF: %q", resp)
	}

	parts := strings.Split(strings.TrimPrefix(resp, "RAWCONF:"), "|")
	if len(parts) != 3 {
		return "", "", 0, fmt.Errorf("некорректный формат RAWCONF: %q", resp)
	}
	mtuVal, convErr := strconv.Atoi(strings.TrimSpace(parts[2]))
	if convErr != nil {
		return "", "", 0, fmt.Errorf("некорректный MTU в RAWCONF: %q", parts[2])
	}
	return strings.TrimSpace(parts[0]), strings.TrimSpace(parts[1]), mtuVal, nil
}
