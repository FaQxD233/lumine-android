package lumine

import (
	"os"
	"path/filepath"
	"testing"

	log "github.com/moi-si/mylog"
)

func TestLoadConfigResetsDerivedGlobalState(t *testing.T) {
	dir := t.TempDir()
	first := filepath.Join(dir, "first.json")
	second := filepath.Join(dir, "second.json")

	writeTestConfig(t, first, `{
		"log_level": "DEBUG",
		"dns_addr": "1.1.1.1:53",
		"dns_singleflight": true,
		"dns_cache_ttl": 60,
		"dns_cache_cap": 16,
		"ttl_singleflight": true,
		"ttl_cache_ttl": 60,
		"ttl_cache_cap": 16,
		"fake_ttl_rules": "1=1"
	}`)
	writeTestConfig(t, second, `{
		"dns_addr": "9.9.9.9:53"
	}`)

	if _, _, err := LoadConfig(first); err != nil {
		t.Fatalf("load first config: %v", err)
	}
	rememberMobileDNSMapping("203.0.113.7", "old.example", 60)

	if dnsSingleflight == nil || dnsCache == nil || ttlSingleflight == nil || ttlCache == nil || calcTTL == nil {
		t.Fatalf("first config did not enable expected derived state")
	}
	if _, ok := lookupMobileDNSMapping("203.0.113.7"); !ok {
		t.Fatalf("test setup did not create mobile DNS mapping")
	}

	if _, _, err := LoadConfig(second); err != nil {
		t.Fatalf("load second config: %v", err)
	}

	if logLevel != log.INFO {
		t.Fatalf("log level was not reset to INFO: %v", logLevel)
	}
	if dnsSingleflight != nil || dnsCache != nil || ttlSingleflight != nil || ttlCache != nil || calcTTL != nil {
		t.Fatalf("derived caches/singleflight/TTL state leaked after loading second config")
	}
	if _, ok := lookupMobileDNSMapping("203.0.113.7"); ok {
		t.Fatalf("mobile DNS mapping leaked after loading second config")
	}
	if dnsAddr != "9.9.9.9:53" {
		t.Fatalf("dnsAddr = %q, want second config value", dnsAddr)
	}
}

func TestLoadConfigCleansPartialStateOnError(t *testing.T) {
	dir := t.TempDir()
	valid := filepath.Join(dir, "valid.json")
	invalid := filepath.Join(dir, "invalid.json")

	writeTestConfig(t, valid, `{
		"dns_addr": "1.1.1.1:53",
		"dns_cache_ttl": 60,
		"dns_cache_cap": 16
	}`)
	writeTestConfig(t, invalid, `{
		"dns_addr": "9.9.9.9:53",
		"dns_singleflight": true,
		"dns_cache_ttl": 60,
		"dns_cache_cap": 0
	}`)

	if _, _, err := LoadConfig(valid); err != nil {
		t.Fatalf("load valid config: %v", err)
	}
	if dnsCache == nil {
		t.Fatalf("test setup did not create DNS cache")
	}

	if _, _, err := LoadConfig(invalid); err == nil {
		t.Fatalf("load invalid config unexpectedly succeeded")
	}

	if dnsAddr != "" || dnsSingleflight != nil || dnsCache != nil {
		t.Fatalf("partial DNS state leaked after failed load: addr=%q singleflight=%v cache=%v", dnsAddr, dnsSingleflight, dnsCache)
	}
}

func writeTestConfig(t *testing.T, path string, body string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}
