package main

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const workersPerGroup = 9

const allocateGateInterval = 100 * time.Millisecond

// quotaRetryDelay — пауза после «Allocation Quota Reached» (486). Раньше
// было 30-60 с с первого же раза: при массовом переподключении (сервер
// вернулся, сменилась сеть) один поток, наткнувшийся на квоту, выпадал на
// полминуты, хотя слоты освобождаются за секунды. Теперь 2 → 4 → 8 → 16 →
// 30 с плюс разброс; со второго раза берутся свежие креды.
func quotaRetryDelay(streak int) time.Duration {
	if streak < 1 {
		streak = 1
	}
	d := 2 * time.Second
	for i := 1; i < streak && d < 30*time.Second; i++ {
		d *= 2
	}
	if d > 30*time.Second {
		d = 30 * time.Second
	}
	return d + time.Duration(rand.Intn(1000))*time.Millisecond
}

// workerRetryDelay — пауза перед повтором упавшего потока: 0,5 с с
// удвоением до 8 с плюс разброс, чтобы потоки не ломились разом. Раньше
// было 5-15 с на любую ошибку, и поток, споткнувшийся на старте, выпадал
// из набора на десяток секунд.
func workerRetryDelay(attempt int) time.Duration {
	if attempt < 1 {
		attempt = 1
	}
	d := 500 * time.Millisecond
	for i := 1; i < attempt && d < 8*time.Second; i++ {
		d *= 2
	}
	if d > 8*time.Second {
		d = 8 * time.Second
	}
	return d + time.Duration(rand.Intn(500))*time.Millisecond
}

// seatCooldown — минимальная пауза перед новой TURN-аллокацией потока после
// конца его сессии. VK держит место освобождённой аллокации на квоте ещё около
// секунды (замеры anton48/vk-turn-proxy-ios, pkg/proxy/seatcool.go): повтор
// раньше получает 486 «квота исчерпана» на собственного «призрака».
const seatCooldown = 1200 * time.Millisecond

// credsProvenAfter — сессия на этих кредах продержалась столько: аллокация
// на них точно была, креды рабочие (см. credsProven в WorkerGroup).
const credsProvenAfter = 10 * time.Second

// groupStartBarrier — сколько следующая группа ждёт, пока эта получит доступ
// к звонку. Раньше ждала только успеха: группа, не получившая доступ, выходила
// и не передавала эстафету — все следующие группы ждали вечно, а без первой и
// конфиг туннеля не приходил. Теперь эстафета уходит по успеху или по этому
// таймауту, что раньше; запросы к VK всё равно идут по одному (vkRequestMu).
const groupStartBarrier = 20 * time.Second

// configRelay — получение конфига туннеля (RAWCONF/WG) на все группы сразу:
// его может получить любой поток любой группы, а не только первая группа.
// sent — конфиг доставлен; inFlight — его прямо сейчас запрашивает один поток.
type configRelay struct {
	ch       chan<- string
	sent     atomic.Bool
	inFlight atomic.Bool
}

// isVKLimitError — VK ограничил запросы (flood control, rate limit, error 29)
// или действует пауза после капчи: повторять сразу бессмысленно и вредно.
func isVKLimitError(err error) bool {
	if err == nil {
		return false
	}
	t := strings.ToLower(err.Error())
	return strings.Contains(t, "flood") ||
		strings.Contains(t, "rate limit") ||
		strings.Contains(t, "too many requests") ||
		strings.Contains(t, "error_code:29") ||
		strings.Contains(t, "error_code: 29") ||
		strings.Contains(t, "error 29") ||
		strings.Contains(t, "captcha_wait_required")
}

// vkLimitDelay — пауза после ограничения со стороны VK: 1 → 2 → 4 → 8 → 10 мин
// плюс разброс. Частые повторы только продлевают ограничение и ведут к капче.
func vkLimitDelay(streak int) time.Duration {
	if streak < 1 {
		streak = 1
	}
	d := time.Minute
	for i := 1; i < streak && d < 10*time.Minute; i++ {
		d *= 2
	}
	if d > 10*time.Minute {
		d = 10 * time.Minute
	}
	return d + time.Duration(rand.Intn(10_000))*time.Millisecond
}

// credsRetryDelay — пауза перед повтором получения доступа к звонку группой:
// после ограничения VK — vkLimitDelay (но не раньше конца паузы после капчи),
// после прочих ошибок (сеть, DNS) — 2 → 4 → … → 60 с плюс разброс.
func credsRetryDelay(err error, attempt, limitStreak int, lockoutUntil time.Time) time.Duration {
	if isVKLimitError(err) {
		d := vkLimitDelay(limitStreak)
		if until := time.Until(lockoutUntil); until > d {
			d = until + time.Duration(rand.Intn(3000))*time.Millisecond
		}
		return d
	}
	if attempt < 1 {
		attempt = 1
	}
	d := 2 * time.Second
	for i := 1; i < attempt && d < time.Minute; i++ {
		d *= 2
	}
	if d > time.Minute {
		d = time.Minute
	}
	return d + time.Duration(rand.Intn(1000))*time.Millisecond
}

// announceVKWait сообщает приложению, что движок выжидает ограничение VK
// (VK_WAIT|секунды) или что ожидание закончилось (VK_WAIT|0): приложение
// не считает это зависанием и пишет об ожидании под кнопкой.
var vkWaitAnnounced atomic.Bool

func announceVKWait(d time.Duration) {
	if d <= 0 {
		if vkWaitAnnounced.Swap(false) {
			fmt.Println("VK_WAIT|0")
		}
		return
	}
	vkWaitAnnounced.Store(true)
	fmt.Printf("VK_WAIT|%d\n", int(d.Seconds()))
}

// waitWhilePaused — ждёт, пока снята пауза (PAUSE из stdin). false — ctx отменён.
func waitWhilePaused(ctx context.Context, pauseFlag *int32) bool {
	for atomic.LoadInt32(pauseFlag) != 0 {
		select {
		case <-ctx.Done():
			return false
		case <-time.After(time.Second):
		}
	}
	return ctx.Err() == nil
}

// WorkerGroup:
// Запускает 9 потоков с одними кредами. Ротации нет — работает до смерти воркеров.
func WorkerGroup(
	ctx context.Context,
	groupID int,
	hashIndex int,
	tp *TurnParams,
	peer *net.UDPAddr,
	d *Dispatcher,
	localPort string,
	cfg *configRelay,
	workerIDs []int,
	pauseFlag *int32,
	deviceID, password string,
	stats *Stats,
	waitReady <-chan struct{},
	signalReady chan<- struct{},
) {
	// Каскадный запуск: ждем свою очередь
	if waitReady != nil {
		log.Printf("[ГРУППА #%d] Ожидание сигнала от предыдущей группы...", groupID)
		select {
		case <-waitReady:
		case <-ctx.Done():
			return
		}
	}

	// Эстафета следующей группе — ровно один раз: по успеху (ниже), по
	// таймауту groupStartBarrier или если эта группа остановлена насовсем.
	var passOnce sync.Once
	passOn := func(reason string) {
		if signalReady == nil {
			return
		}
		passOnce.Do(func() {
			close(signalReady)
			log.Printf("[ГРУППА #%d] Передача эстафеты следующей группе (%s)", groupID, reason)
		})
	}
	if signalReady != nil {
		go func() {
			select {
			case <-time.After(groupStartBarrier):
				passOn("ожидание доступа дольше " + groupStartBarrier.String())
			case <-ctx.Done():
			}
		}()
	}

	hash := tp.Hashes[hashIndex%len(tp.Hashes)]
	shortHash := hash
	if len(shortHash) > 8 {
		shortHash = shortHash[:8]
	}
	credStreamID := groupID * 100

	// Доступ к звонку: при ошибке группа не выходит, а повторяет — с паузой
	// по виду ошибки (ограничение VK — минуты, сеть — секунды). Насовсем
	// останавливает только недоступный звонок: повтор с той же ссылкой не
	// поможет, приложение переключит хеш или остановит подключение само.
	var creds *Credentials
	limitStreak := 0
	for attempt := 1; ; attempt++ {
		// Doze-mode пауза
		if !waitWhilePaused(ctx, pauseFlag) {
			return
		}
		log.Printf("[ГРУППА #%d] Запрос кредов (хеш: %s...)", groupID, shortHash)
		user, pass, turnURLs, err := GetCreds(ctx, hash, credStreamID)
		if err == nil {
			creds = &Credentials{User: user, Pass: pass, TurnURLs: turnURLs, CacheStreamID: credStreamID}
			break
		}
		if ctx.Err() != nil {
			return
		}
		log.Printf("[ГРУППА #%d] Ошибка кредов: %v", groupID, err)
		markDiagCredsFailed()
		if _, dead := asCallUnavailableError(err); dead {
			log.Printf("[ГРУППА #%d] Звонок недоступен — группа остановлена", groupID)
			passOn("звонок недоступен")
			return
		}
		if isVKLimitError(err) {
			limitStreak++
		} else {
			limitStreak = 0
		}
		wait := credsRetryDelay(err, attempt, limitStreak, time.Unix(globalCaptchaLockout.Load(), 0))
		if isVKLimitError(err) {
			announceVKWait(wait)
			log.Printf("[ГРУППА #%d] VK ограничил запросы — повтор через %v", groupID, wait.Truncate(time.Second))
		} else {
			log.Printf("[ГРУППА #%d] Повтор запроса кредов через %v", groupID, wait.Truncate(time.Second))
		}
		select {
		case <-time.After(wait):
		case <-ctx.Done():
			return
		}
	}

	announceVKWait(0)
	log.Printf("[ГРУППА #%d] Креды OK, TURN: %v, %d воркеров", groupID, creds.TurnURLs, len(workerIDs))
	markDiagCredsReady()

	var wg sync.WaitGroup
	var credsMu sync.RWMutex
	var refreshMu sync.Mutex
	var lastCredRefresh atomic.Int64
	// credsProven — на текущих кредах уже работал хотя бы один поток. Отказ
	// 486 на свежих кредах, на которых ничего не получилось, — отказ самого
	// relay, а не квота личности: новые креды (новый вход в звонок) его не
	// лечат и только приближают капчу (anton48, pkg/proxy/quotabreaker.go).
	var credsProven atomic.Bool
	// Креды прошлого запуска уже работали; 486 на них — места держат аллокации
	// прошлого процесса, и лекарство как раз свежие креды.
	if credsPreloaded(getCacheID(credStreamID)) {
		credsProven.Store(true)
		log.Printf("[ГРУППА #%d] Креды из прошлого запуска, повторного входа в звонок нет", groupID)
	}

	refreshCreds := func(reason string) bool {
		refreshMu.Lock()
		defer refreshMu.Unlock()

		now := time.Now().Unix()
		last := lastCredRefresh.Load()
		if last > 0 && now-last < 15 {
			log.Printf("[TURN] Креды уже обновлялись %d сек назад, ждём следующий retry (%s)", now-last, reason)
			return true
		}

		getStreamCache(credStreamID).invalidate(credStreamID)
		if getVkAuthMode() == "account" {
			invalidateInjectedTurnCreds(hash)
		}
		u, p, urls, refreshErr := GetCreds(ctx, hash, credStreamID)
		if refreshErr != nil {
			log.Printf("[TURN] Не удалось обновить креды после %s: %v", reason, refreshErr)
			return false
		}

		credsMu.Lock()
		creds = &Credentials{User: u, Pass: p, TurnURLs: urls, CacheStreamID: credStreamID}
		credsMu.Unlock()
		credsProven.Store(false)
		lastCredRefresh.Store(time.Now().Unix())
		log.Printf("[TURN] Креды обновлены после %s, TURN urls=%d", reason, len(urls))
		return true
	}

	// Сигнализируем следующей группе, что мы успешно запустились (креды получены + фора)
	if signalReady != nil {
		go func() {
			delayMs := 500 + rand.Intn(250)
			time.Sleep(time.Duration(delayMs) * time.Millisecond)
			passOn("успешный старт")
		}()
	}

	// Общий rate-limit на TURN Allocate по всей группе: не более одной новой
	// аллокации за тик, независимо от того, сколько воркеров сейчас готовы
	// её выполнить (стартовый stagger — отдельная вещь, см. workerDelay ниже —
	// он размазывает старт горутин, но не сами ретраи Allocate внутри уже
	// запущенных). Без этого на нестабильной сети несколько воркеров всё
	// равно накладываются друг на друга и вместе выжигают VK-квоту (error
	// 486) быстрее, чем должны. См. RunSession(allocateGate) в session.go и
	// комментарий там про free-turn-proxy — тот же приём.
	allocateTicker := time.NewTicker(allocateGateInterval)
	defer allocateTicker.Stop()

	for i, wid := range workerIDs {
		wg.Add(1)

		workerDelay := time.Duration(i) * 75 * time.Millisecond

		go func(wid int, delay time.Duration) {
			defer wg.Done()

			if delay > 0 {
				select {
				case <-time.After(delay):
				case <-ctx.Done():
					return
				}
			}

			attempt := 0
			quotaStreak := 0 // сколько раз подряд VK ответил «квота исчерпана»

			for {
				if ctx.Err() != nil {
					return
				}

				getConf := false
				if cfg != nil && !cfg.sent.Load() {
					getConf = cfg.inFlight.CompareAndSwap(false, true)
				}
				var cc chan<- string
				if getConf {
					cc = cfg.ch
				}

				credsMu.RLock()
				credsSnapshot := *creds
				credsSnapshot.TurnURLs = cloneStringSlice(creds.TurnURLs)
				credsMu.RUnlock()

				sessStarted := time.Now()
				configDelivered, sessErr := RunSession(ctx, tp, peer, d, localPort,
					getConf, cc, wid, &credsSnapshot, deviceID, password, stats, allocateTicker.C)

				quotaRetry := false
				fastRetry := false
				vkLimited := false
				if getConf {
					if configDelivered {
						cfg.sent.Store(true)
					}
					cfg.inFlight.Store(false)
				}

				if sessErr != nil {
					if ctx.Err() != nil {
						return
					}
					errStr := sessErr.Error()
					errStrLower := strings.ToLower(errStr)
					fastRetry = strings.Contains(errStrLower, "broken pipe") ||
						strings.Contains(errStrLower, "connection reset by peer") ||
						strings.Contains(errStrLower, "unexpected eof") ||
						// Конфиг не получен (потерян ответ, сервер ещё не назначил
						// адрес): поток уже освободил запрос, повтор — сразу.
						strings.Contains(errStr, configRetryMarker)

					turnAllocAttrMissing := strings.Contains(errStrLower, "turn allocate") &&
						strings.Contains(errStrLower, "attribute not found")
					isTurnQuota := strings.Contains(errStrLower, "quota") || strings.Contains(errStr, "486")
					quotaRetry = isTurnQuota
					// ip mismatch: креды VK привязаны к адресу, а он сменился (смена
					// сети) — лечится только свежими кредами.
					turnCredRefreshNeeded := !isTurnQuota && (turnAllocAttrMissing ||
						strings.Contains(errStrLower, "ip mismatch") ||
						strings.Contains(errStrLower, "turn allocate auth") ||
						strings.Contains(errStrLower, "invalid credential") ||
						strings.Contains(errStrLower, "stale nonce") ||
						strings.Contains(errStrLower, "allocation mismatch") ||
						strings.Contains(errStrLower, "error 508"))

					if hint := workerErrorHint(sessErr); hint != "" {
						errStr += " | " + hint
					} else if strings.Contains(errStrLower, "rate limit") ||
						strings.Contains(errStrLower, "flood control") ||
						strings.Contains(errStrLower, "ip mismatch") ||
						strings.Contains(errStrLower, "error 29") {
						errStr += " (ошибка со стороны ВК)"
					}

					if strings.Contains(errStr, "хеш мёртв") ||
						strings.Contains(errStr, "FATAL_AUTH") {
						log.Printf("[ВОРКЕР #%d] Фатальная ошибка: %s", wid, errStr)
						return
					}

					attempt++
					if isTurnQuota {
						quotaStreak++
						log.Printf("[ВОРКЕР #%d] [TURN] Квота relay исчерпана (%d-й раз подряд), повтор через %v: %s", wid, quotaStreak, quotaRetryDelay(quotaStreak).Truncate(time.Second), errStr)
						// Повторный отказ: на этих кредах слотов нет — берём
						// свежие (у них своя квота). В режиме аккаунта VK креды
						// выдаёт приложение через авторизацию — его не дёргаем.
						if quotaStreak >= 2 && getVkAuthMode() != "account" {
							if credsProven.Load() {
								refreshCreds("TURN quota")
							} else {
								log.Printf("[ВОРКЕР #%d] [TURN] Квота на свежих кредах, на которых ещё ничего не работало: relay отказывает сам, новые креды не запрашиваем", wid)
							}
						}
					} else if turnAllocAttrMissing {
						log.Printf("[ВОРКЕР #%d] [TURN] Allocate вернул неполный ответ, обновляем TURN-креды и повторяем (попытка %d): %s", wid, attempt, errStr)
						refreshCreds("TURN Allocate attribute-not-found")
					} else if turnCredRefreshNeeded {
						log.Printf("[ВОРКЕР #%d] [TURN] Ошибка allocation/кредов, обновляем TURN-креды и повторяем (попытка %d): %s", wid, attempt, errStr)
						refreshCreds("TURN allocation error")
					} else {
						log.Printf("[ВОРКЕР #%d] Ошибка (попытка %d): %s", wid, attempt, errStr)
					}

					// Раньше «error 29» и «cannot create socket» завершали поток насовсем,
					// и за долгую сессию группа тихо теряла потоки. Это ограничение VK
					// и временная нехватка сети: поток ждёт и повторяет.
					if isVKLimitError(sessErr) {
						vkLimited = true
						log.Printf("[ВОРКЕР #%d] VK ограничил запросы — пауза перед повтором", wid)
					}
				}

				if ctx.Err() != nil {
					return
				}

				if time.Since(sessStarted) > credsProvenAfter {
					credsProven.Store(true)
				}
				// Поток поработал и упал — это новый сбой, а не серия неудач.
				if time.Since(sessStarted) > 60*time.Second {
					attempt = 1
				}
				if !quotaRetry {
					quotaStreak = 0
				}
				retryDelay := workerRetryDelay(attempt)
				if quotaRetry {
					retryDelay = quotaRetryDelay(quotaStreak)
				} else if vkLimited {
					retryDelay = 30*time.Second + time.Duration(rand.Intn(30_000))*time.Millisecond
				} else if fastRetry {
					retryDelay = time.Duration(1+rand.Intn(3)) * time.Second
				}
				if retryDelay < seatCooldown {
					retryDelay = seatCooldown + time.Duration(rand.Intn(300))*time.Millisecond
				}
				select {
				case <-time.After(retryDelay):
				case <-ctx.Done():
					return
				}
			}
		}(wid, workerDelay)
	}

	wg.Wait()
	log.Printf("[ГРУППА #%d] Все воркеры группы завершились.", groupID)
}

// ParseHashes — парсит строку хешей
func ParseHashes(raw string) []string {
	var result []string
	seen := make(map[string]struct{})
	for _, h := range strings.FieldsFunc(raw, func(r rune) bool {
		return r == ',' || r == ';' || r == '\n' || r == '\r' || r == '\t' || r == ' '
	}) {
		h = normalizeVKJoinHash(h)
		if h != "" {
			if _, exists := seen[h]; exists {
				continue
			}
			seen[h] = struct{}{}
			result = append(result, h)
		}
	}
	return result
}

func normalizeVKJoinHash(input string) string {
	s := strings.Trim(strings.TrimSpace(input), "<>\"'")
	if s == "" {
		return ""
	}

	lower := strings.ToLower(s)
	if idx := strings.Index(lower, "/call/join/"); idx >= 0 {
		s = s[idx+len("/call/join/"):]
	} else if strings.HasPrefix(lower, "http://") || strings.HasPrefix(lower, "https://") {
		return ""
	}

	if idx := strings.IndexAny(s, "?#/"); idx != -1 {
		s = s[:idx]
	}
	return strings.Trim(strings.TrimSpace(s), "/")
}

// TurnParams — конфигурация TURN
type TurnParams struct {
	// Hosts: свои TURN-адреса (-turn), заменяют хост VK по кругу между
	// сессиями — см. selectTurnTarget.
	Hosts    []string
	Port     string
	Hashes   []string
	WrapKey  []byte // Password-derived WRAP key (32 bytes), nil = disabled
	ObfsMode string // "audio" or "video" — RTP masking mode
	// NoDTLS: пропустить DTLS и идти RTP-obfs AEAD напрямую поверх TURN relay.
	// Требует сервер, который умеет принимать прямые (без DTLS) сессии на
	// отдельном порту/слушателе — см. server/main.go -listen-direct.
	NoDTLS bool
	// RawMode: raw-IP без WireGuard (см. server/main.go -listen-raw, handleConnRaw).
	// Подразумевает NoDTLS — сервер на -listen-raw DTLS не понимает.
	RawMode bool
	// Transport: как подключаться к TURN-relay VK — udp или tcp (см.
	// turn_endpoint.go). На некоторых сетях (замечено на Ростелекоме) UDP до
	// TURN душится провайдером агрессивнее, чем TCP на тот же relay.
	Transport string
}

// Credentials — учетные данные TURN
type Credentials struct {
	User          string
	Pass          string
	TurnURLs      []string
	CacheStreamID int
}
