package lumine

import (
	"net"
	"testing"
	"time"

	"github.com/elastic/go-freelru"
	"github.com/miekg/dns"
	"golang.org/x/sync/singleflight"
)

func TestDNSResolveCacheKeyIncludesMode(t *testing.T) {
	oldDNSExchange := dnsExchange
	oldDNSCache := dnsCache
	oldDNSCacheTTL := dnsCacheTTL
	oldDNSSingleflight := dnsSingleflight
	defer func() {
		dnsExchange = oldDNSExchange
		dnsCache = oldDNSCache
		dnsCacheTTL = oldDNSCacheTTL
		dnsSingleflight = oldDNSSingleflight
	}()

	var err error
	dnsCache, err = freelru.NewSharded[string, string](8, hashStringXXHASH)
	if err != nil {
		t.Fatalf("create DNS cache: %v", err)
	}
	dnsCacheTTL = time.Minute
	dnsSingleflight = new(singleflight.Group)
	dnsExchange = func(req *dns.Msg) (*dns.Msg, error) {
		resp := new(dns.Msg)
		resp.SetReply(req)
		question := req.Question[0]
		switch question.Qtype {
		case dns.TypeA:
			resp.Answer = []dns.RR{&dns.A{
				Hdr: dns.RR_Header{Name: question.Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60},
				A:   net.ParseIP("192.0.2.10"),
			}}
		case dns.TypeAAAA:
			resp.Answer = []dns.RR{&dns.AAAA{
				Hdr:  dns.RR_Header{Name: question.Name, Rrtype: dns.TypeAAAA, Class: dns.ClassINET, Ttl: 60},
				AAAA: net.ParseIP("2001:db8::10"),
			}}
		default:
			t.Fatalf("unexpected DNS query type: %d", question.Qtype)
		}
		return resp, nil
	}

	ipv4, cached, err := dnsResolve("example.com", DNSModeIPv4Only)
	if err != nil {
		t.Fatalf("resolve IPv4: %v", err)
	}
	if cached || ipv4 != "192.0.2.10" {
		t.Fatalf("IPv4 resolve = %q cached=%v, want 192.0.2.10 cached=false", ipv4, cached)
	}

	ipv6, cached, err := dnsResolve("example.com", DNSModeIPv6Only)
	if err != nil {
		t.Fatalf("resolve IPv6: %v", err)
	}
	if cached || ipv6 != "2001:db8::10" {
		t.Fatalf("IPv6 resolve = %q cached=%v, want 2001:db8::10 cached=false", ipv6, cached)
	}

	ipv4, cached, err = dnsResolve("example.com", DNSModeIPv4Only)
	if err != nil {
		t.Fatalf("resolve cached IPv4: %v", err)
	}
	if !cached || ipv4 != "192.0.2.10" {
		t.Fatalf("cached IPv4 resolve = %q cached=%v, want 192.0.2.10 cached=true", ipv4, cached)
	}
}
