package main

import (
	"sync/atomic"
	"time"
)

// Пинг до сервера — время между отправкой keepalive и ответом сервера на него.
// Отдельных пакетов не шлём: keepalive и так уходит каждые несколько секунд.
// За окно статистики берём минимум по всем потокам: он меньше всего искажён
// очередью данных перед keepalive.

var pingWindowMinUs atomic.Int64 // минимум за текущее окно, мкс; 0 — замеров не было

// pingStaleAfter — сколько показывать последний замер, если новых нет.
const pingStaleAfter = 20 * time.Second

func notePingSample(rtt time.Duration) {
	us := rtt.Microseconds()
	if us <= 0 || rtt > 10*time.Second {
		return
	}
	for {
		cur := pingWindowMinUs.Load()
		if cur != 0 && cur <= us {
			return
		}
		if pingWindowMinUs.CompareAndSwap(cur, us) {
			return
		}
	}
}

// pingReporter хранит последний показанный пинг между окнами статистики.
type pingReporter struct {
	lastMs int64
	lastAt time.Time
}

// next закрывает окно и возвращает пинг в мс для строки статистики (0 — нет данных).
func (r *pingReporter) next(now time.Time) int64 {
	if us := pingWindowMinUs.Swap(0); us > 0 {
		r.lastMs = (us + 500) / 1000
		if r.lastMs == 0 {
			r.lastMs = 1
		}
		r.lastAt = now
	}
	if r.lastMs > 0 && now.Sub(r.lastAt) > pingStaleAfter {
		r.lastMs = 0
	}
	return r.lastMs
}
