package main

// Сохранение TURN-кредов анонимного пути между запусками движка.
//
// Каждый запуск раньше заново входил в звонок от каждой группы: пять запросов
// к VK и риск капчи на каждый. Приложение перезапускает движок чаще, чем
// меняется сеть (сторож, пробуждение, повторное подключение пользователем),
// и в той же сети креды прошлого запуска ещё живы — их срок около 10 минут.
//
// Креды берутся только для той же сети, в которой получены (ключ сети даёт
// приложение, -net-key): VK привязывает их к адресу («ip mismatch»), а после
// смены сети аллокации прошлого процесса вне досягаемости и держат места на
// квоте этой личности до истечения (замеры anton48/vk-turn-proxy-ios,
// pkg/proxy/outofreach.go) — там лекарство как раз новая личность.

import (
	"encoding/json"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const credsStoreVersion = 1

// credsReuseMinRemaining — креды с меньшим запасом срока не берутся: первая
// же аллокация не успела бы до истечения, а потом всё равно новый вход.
const credsReuseMinRemaining = 90 * time.Second

type persistedCreds struct {
	CacheID   int      `json:"cache_id"`
	Link      string   `json:"link"`
	User      string   `json:"user"`
	Pass      string   `json:"pass"`
	Addrs     []string `json:"addrs"`
	ExpiresAt int64    `json:"expires_at"` // unix ms
	Net       string   `json:"net"`
}

type credsStoreFile struct {
	Version int              `json:"version"`
	Entries []persistedCreds `json:"entries"`
}

var credsStoreState struct {
	mu        sync.Mutex
	path      string
	net       string
	preloaded map[int]bool
}

// isGroupCacheID — кэш группы (credStreamID = groupID*100 → cacheID кратен 10);
// кэши диагностики и пинга (990, 999 → 99) не сохраняются.
func isGroupCacheID(cacheID int) bool { return cacheID > 0 && cacheID%10 == 0 }

// setupCredsStore включает сохранение кредов в path для сети netKey и
// подгружает подходящие креды прошлого запуска. Возвращает число подгруженных.
// Пустой path или netKey — сохранение выключено.
func setupCredsStore(path, netKey string, now time.Time) int {
	credsStoreState.mu.Lock()
	credsStoreState.path = path
	credsStoreState.net = netKey
	credsStoreState.preloaded = make(map[int]bool)
	credsStoreState.mu.Unlock()
	if path == "" || netKey == "" {
		return 0
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return 0
	}
	var file credsStoreFile
	if json.Unmarshal(raw, &file) != nil || file.Version != credsStoreVersion {
		return 0
	}
	loaded := 0
	for _, e := range file.Entries {
		expires := time.UnixMilli(e.ExpiresAt)
		if e.Net != netKey || !isGroupCacheID(e.CacheID) || e.Link == "" || e.User == "" ||
			len(e.Addrs) == 0 || expires.Sub(now) < credsReuseMinRemaining {
			continue
		}
		cache := getStreamCache(e.CacheID * streamsPerCache)
		cache.mutex.Lock()
		cache.creds = TurnCredentials{
			Username:    e.User,
			Password:    e.Pass,
			ServerAddrs: cloneStringSlice(e.Addrs),
			ExpiresAt:   expires,
			Link:        e.Link,
		}
		cache.mutex.Unlock()
		credsStoreState.mu.Lock()
		credsStoreState.preloaded[e.CacheID] = true
		credsStoreState.mu.Unlock()
		loaded++
	}
	if loaded > 0 {
		log.Printf("[VK Auth] Креды прошлого запуска в той же сети: %d", loaded)
	}
	return loaded
}

// credsPreloaded — креды этого кэша взяты из прошлого запуска.
func credsPreloaded(cacheID int) bool {
	credsStoreState.mu.Lock()
	defer credsStoreState.mu.Unlock()
	return credsStoreState.preloaded[cacheID]
}

// persistCreds записывает живые креды групп в файл (атомарно, 0600). Вызывается
// после получения и после сброса кредов; в режиме аккаунта VK не пишет —
// там креды выдаёт приложение.
func persistCreds() {
	credsStoreState.mu.Lock()
	path, netKey := credsStoreState.path, credsStoreState.net
	credsStoreState.mu.Unlock()
	if path == "" || netKey == "" || getVkAuthMode() == "account" {
		return
	}
	now := time.Now()
	file := credsStoreFile{Version: credsStoreVersion}
	credentialsStore.mu.RLock()
	for cacheID, cache := range credentialsStore.caches {
		if !isGroupCacheID(cacheID) {
			continue
		}
		cache.mutex.RLock()
		c := cache.creds
		cache.mutex.RUnlock()
		if c.Username == "" || len(c.ServerAddrs) == 0 || !c.ExpiresAt.After(now) {
			continue
		}
		file.Entries = append(file.Entries, persistedCreds{
			CacheID: cacheID, Link: c.Link, User: c.Username, Pass: c.Password,
			Addrs: cloneStringSlice(c.ServerAddrs), ExpiresAt: c.ExpiresAt.UnixMilli(), Net: netKey,
		})
	}
	credentialsStore.mu.RUnlock()

	raw, err := json.Marshal(file)
	if err != nil {
		return
	}
	persistMu.Lock()
	defer persistMu.Unlock()
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, raw, 0o600); err != nil {
		log.Printf("[VK Auth] Креды не сохранены: %v", err)
		return
	}
	if err := os.Rename(tmp, filepath.Clean(path)); err != nil {
		_ = os.Remove(tmp)
		log.Printf("[VK Auth] Креды не сохранены: %v", err)
	}
}

// persistMu — запись файла по одной (сохранения идут из разных горутин).
var persistMu sync.Mutex
