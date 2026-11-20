package main

import (
	"testing"
	"time"
)

func TestPingReporterTakesWindowMinimum(t *testing.T) {
	pingWindowMinUs.Store(0)
	notePingSample(80 * time.Millisecond)
	notePingSample(45 * time.Millisecond)
	notePingSample(120 * time.Millisecond)
	var r pingReporter
	now := time.Now()
	if got := r.next(now); got != 45 {
		t.Fatalf("ping = %d, want 45", got)
	}
	// No new samples: keep showing the last value for a while, then drop it.
	if got := r.next(now.Add(5 * time.Second)); got != 45 {
		t.Fatalf("ping after quiet window = %d, want 45", got)
	}
	if got := r.next(now.Add(pingStaleAfter + time.Second)); got != 0 {
		t.Fatalf("stale ping = %d, want 0", got)
	}
}

func TestPongWithoutKeepaliveIsIgnored(t *testing.T) {
	pingWindowMinUs.Store(0)
	l := newSessionLiveness(time.Now())
	l.notePong(time.Now())
	if v := pingWindowMinUs.Load(); v != 0 {
		t.Fatalf("pong without a sent keepalive produced a sample: %d", v)
	}
	sent := time.Now()
	l.noteKeepaliveSent(sent)
	l.notePong(sent.Add(30 * time.Millisecond))
	if v := pingWindowMinUs.Load(); v != 30000 {
		t.Fatalf("sample = %d us, want 30000", v)
	}
	// A second pong for the same keepalive must not count again.
	pingWindowMinUs.Store(0)
	l.notePong(sent.Add(90 * time.Millisecond))
	if v := pingWindowMinUs.Load(); v != 0 {
		t.Fatalf("duplicate pong produced a sample: %d", v)
	}
}
