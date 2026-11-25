package main

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

// resetCredentialCaches clears the process-wide TURN credential caches and the store.
func resetCredentialCaches(t *testing.T) {
	t.Helper()
	credentialsStore.mu.Lock()
	credentialsStore.caches = make(map[int]*StreamCredentialsCache)
	credentialsStore.mu.Unlock()
	setupCredsStore("", "", time.Now())
	t.Cleanup(func() {
		credentialsStore.mu.Lock()
		credentialsStore.caches = make(map[int]*StreamCredentialsCache)
		credentialsStore.mu.Unlock()
		setupCredsStore("", "", time.Now())
	})
}

func putCreds(cacheID int, link string, expires time.Time) {
	cache := getStreamCache(cacheID * streamsPerCache)
	cache.mutex.Lock()
	cache.creds = TurnCredentials{Username: "u" + link, Password: "p", ServerAddrs: []string{"203.0.113.1:19302"}, ExpiresAt: expires, Link: link}
	cache.mutex.Unlock()
}

func TestCredsStoreReusedOnlyOnTheSameNetwork(t *testing.T) {
	resetCredentialCaches(t)
	path := filepath.Join(t.TempDir(), "vk_turn_cache.json")
	now := time.Now()

	setupCredsStore(path, "net-A", now)
	putCreds(10, "hash1", now.Add(8*time.Minute)) // group 1: kept
	putCreds(20, "hash1", now.Add(time.Minute))   // group 2: too little left to reuse
	putCreds(99, "hash1", now.Add(8*time.Minute)) // diagnostics/ping: never saved
	persistCreds()

	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("store not written: %v", err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("store mode %v, want 0600", info.Mode().Perm())
	}

	// A new process on the same network: group 1 comes from the store.
	resetCredentialCaches(t)
	if n := setupCredsStore(path, "net-A", now); n != 1 {
		t.Fatalf("loaded %d entries on the same network, want 1", n)
	}
	if !credsPreloaded(10) || credsPreloaded(20) || credsPreloaded(99) {
		t.Fatal("wrong caches marked as preloaded")
	}
	u, _, addrs, err := getVkCredsCached(t.Context(), "hash1", 100)
	if err != nil || u != "uhash1" || len(addrs) != 1 {
		t.Fatalf("group 1 did not get the stored credentials: %q %v %v", u, addrs, err)
	}

	// Another network: nothing is reused (VK binds them to the address).
	resetCredentialCaches(t)
	if n := setupCredsStore(path, "net-B", now); n != 0 || credsPreloaded(10) {
		t.Fatalf("loaded %d entries on another network, want 0", n)
	}

	// No network key from the app: the store is off.
	resetCredentialCaches(t)
	if n := setupCredsStore(path, "", now); n != 0 {
		t.Fatalf("loaded %d entries without a network key, want 0", n)
	}
}

func TestCredsStoreDropsInvalidatedCredentials(t *testing.T) {
	resetCredentialCaches(t)
	path := filepath.Join(t.TempDir(), "vk_turn_cache.json")
	now := time.Now()
	setupCredsStore(path, "net-A", now)
	putCreds(10, "hash1", now.Add(8*time.Minute))
	persistCreds()

	getStreamCache(100).invalidate(100)

	resetCredentialCaches(t)
	if n := setupCredsStore(path, "net-A", now); n != 0 {
		t.Fatalf("invalidated credentials still in the store: %d", n)
	}
}

func TestCredsStoreIgnoresForeignFiles(t *testing.T) {
	resetCredentialCaches(t)
	dir := t.TempDir()
	path := filepath.Join(dir, "vk_turn_cache.json")
	for _, content := range []string{"", "not json", `{"version":99,"entries":[]}`} {
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
		if n := setupCredsStore(path, "net-A", time.Now()); n != 0 {
			t.Fatalf("%q: loaded %d entries", content, n)
		}
	}
	if n := setupCredsStore(filepath.Join(dir, "missing.json"), "net-A", time.Now()); n != 0 {
		t.Fatalf("missing file: loaded %d entries", n)
	}
}
