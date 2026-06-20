//go:build windows || linux

package lumine

import (
	"errors"
	"time"

	"github.com/elastic/go-freelru"
	E "github.com/moi-si/lumine/internal/errors"
	log "github.com/moi-si/mylog"
	"golang.org/x/sync/singleflight"
)

var (
	calcTTL         func(int) (int, error)
	ttlCache        *freelru.ShardedLRU[string, int]
	ttlCacheTTL     time.Duration
	ttlSingleflight *singleflight.Group
)

func loadTTLRules(conf string) error {
	rules, err := parseTTLRules(conf)
	if err != nil {
		return err
	}
	if rules == nil {
		calcTTL = func(int) (int, error) {
			val := 0
			for i := range len(conf) {
				c := conf[i]
				if c < '0' || c > '9' {
					return 0, errors.New("invalid integer config")
				}
				val = val*10 + int(c-'0')
			}
			return val, nil
		}
	} else {
		calcTTL = func(ttl int) (int, error) {
			for _, r := range rules {
				if ttl >= r.threshold {
					if r.typ == '-' {
						return ttl - r.val, nil
					}
					// r.typ == '='
					return r.val, nil
				}
			}
			return 0, errors.New("no matching TTL rule")
		}
	}
	return nil
}

func getMinimalReachableTTL(addr string, ipv6 bool, maxTTL, attempts int, dialTimeout time.Duration) (int, bool, error) {
	if ttlCache != nil {
		if ttl, ok := ttlCache.Get(addr); ok {
			return ttl, true, nil
		}
	}

	var err error
	found := unsetInt
	if ttlSingleflight != nil {
		var v any
		v, err, _ = ttlSingleflight.Do(addr, func() (any, error) {
			return detectMinimalReachableTTL(addr, ipv6, maxTTL, attempts, dialTimeout)
		})
		if err == nil {
			found = v.(int)
		}
	} else {
		found, err = detectMinimalReachableTTL(addr, ipv6, maxTTL, attempts, dialTimeout)
	}
	return found, false, err
}

func getFakeTTL(logger *log.Logger, p *Policy, addr string, ipv6 bool) (ttl int, err error) {
	if p.FakeTTL == 0 || p.FakeTTL == unsetInt {
		var cached bool
		ttl, cached, err = getMinimalReachableTTL(addr, ipv6, p.MaxTTL, p.Attempts, p.SingleTimeout)
		if err != nil {
			return unsetInt, E.WithStr("detect minimum reachable TTL", err)
		}
		if ttl == unsetInt {
			return unsetInt, errors.New("reachable TTL not found")
		}
		if calcTTL != nil {
			ttl, err = calcTTL(ttl)
			if err != nil {
				return unsetInt, E.WithStr("calculate fake TTL", err)
			}
		} else {
			ttl -= 1
		}
		if logger != nil {
			if cached {
				logger.Info("Fake TTL (cached):", ttl)
			} else {
				logger.Info("Fake TTL:", ttl)
			}
		}
	} else {
		ttl = p.FakeTTL
	}
	return
}
