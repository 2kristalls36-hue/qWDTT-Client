package main

import (
	"errors"
	"testing"
	"time"
)

func TestWorkerRetryDelay(t *testing.T) {
	cases := []struct {
		attempt int
		base    time.Duration
	}{
		{0, 500 * time.Millisecond},
		{1, 500 * time.Millisecond},
		{2, time.Second},
		{3, 2 * time.Second},
		{5, 8 * time.Second},
		{50, 8 * time.Second},
	}
	for _, c := range cases {
		for i := 0; i < 20; i++ {
			d := workerRetryDelay(c.attempt)
			if d < c.base || d >= c.base+500*time.Millisecond {
				t.Fatalf("attempt %d: delay %v, want [%v, %v)", c.attempt, d, c.base, c.base+500*time.Millisecond)
			}
		}
	}
}

func TestQuotaRetryDelay(t *testing.T) {
	cases := []struct {
		streak int
		base   time.Duration
	}{
		{0, 2 * time.Second},
		{1, 2 * time.Second},
		{2, 4 * time.Second},
		{3, 8 * time.Second},
		{4, 16 * time.Second},
		{5, 30 * time.Second},
		{20, 30 * time.Second},
	}
	for _, c := range cases {
		for i := 0; i < 20; i++ {
			d := quotaRetryDelay(c.streak)
			if d < c.base || d >= c.base+time.Second {
				t.Fatalf("streak %d: delay %v, want [%v, %v)", c.streak, d, c.base, c.base+time.Second)
			}
		}
	}
}

func TestIsVKLimitError(t *testing.T) {
	limited := []string{
		"vk api error: Flood control",
		"error_code:29 Rate limit reached",
		"error_code: 29",
		"TURN allocate: error 29",
		"CAPTCHA_WAIT_REQUIRED: global lockout active",
	}
	for _, m := range limited {
		if !isVKLimitError(errors.New(m)) {
			t.Errorf("%q: not seen as a VK limit", m)
		}
	}
	other := []string{"dial udp: i/o timeout", "no such host", "Call not found (9000)", "TURN квота: 486"}
	for _, m := range other {
		if isVKLimitError(errors.New(m)) {
			t.Errorf("%q: seen as a VK limit", m)
		}
	}
	if isVKLimitError(nil) {
		t.Error("nil seen as a VK limit")
	}
}

func TestVKLimitDelay(t *testing.T) {
	cases := []struct {
		streak int
		base   time.Duration
	}{
		{0, time.Minute},
		{1, time.Minute},
		{2, 2 * time.Minute},
		{3, 4 * time.Minute},
		{4, 8 * time.Minute},
		{5, 10 * time.Minute},
		{30, 10 * time.Minute},
	}
	for _, c := range cases {
		for i := 0; i < 20; i++ {
			d := vkLimitDelay(c.streak)
			if d < c.base || d >= c.base+10*time.Second {
				t.Fatalf("streak %d: delay %v, want [%v, %v)", c.streak, d, c.base, c.base+10*time.Second)
			}
		}
	}
}

func TestCredsRetryDelay(t *testing.T) {
	network := errors.New("dial tcp: i/o timeout")
	for _, c := range []struct {
		attempt int
		base    time.Duration
	}{{1, 2 * time.Second}, {2, 4 * time.Second}, {5, 32 * time.Second}, {6, time.Minute}, {40, time.Minute}} {
		d := credsRetryDelay(network, c.attempt, 0, time.Time{})
		if d < c.base || d >= c.base+time.Second {
			t.Fatalf("network, attempt %d: delay %v, want [%v, %v)", c.attempt, d, c.base, c.base+time.Second)
		}
	}

	flood := errors.New("Flood control")
	if d := credsRetryDelay(flood, 1, 1, time.Time{}); d < time.Minute || d >= time.Minute+10*time.Second {
		t.Fatalf("flood: delay %v, want about a minute", d)
	}
	// A captcha lockout longer than the backoff is waited out to its end.
	until := time.Now().Add(3 * time.Minute)
	if d := credsRetryDelay(errors.New("CAPTCHA_WAIT_REQUIRED"), 1, 1, until); d < 3*time.Minute-time.Second || d > 3*time.Minute+4*time.Second {
		t.Fatalf("lockout: delay %v, want the rest of the lockout", d)
	}
}
