package lumine

import (
	"net"
	"strings"
	"sync"
	"time"

	"github.com/miekg/dns"
)

const vpnDNSIPv4Addr = "172.19.0.2"
const mobileDNSAnswerTTL = 60

type mobileDNSRecord struct {
	domain  string
	expires time.Time
}

var mobileDNSRecords = struct {
	sync.RWMutex
	byIP map[string]mobileDNSRecord
}{
	byIP: make(map[string]mobileDNSRecord),
}

func VPNDNSIPv4() string {
	return vpnDNSIPv4Addr
}

func IsVPNDNSAddress(host string, port int) bool {
	return host == vpnDNSIPv4Addr && port == 53
}

func HandleDNSQueryPacket(payload []byte) ([]byte, error) {
	req := new(dns.Msg)
	if err := req.Unpack(payload); err != nil {
		return nil, err
	}

	if resp, handled, err := handleConfiguredMobileDNS(req); handled || err != nil {
		if err != nil {
			return nil, err
		}
		return resp.Pack()
	}

	resp, err := dnsExchange(req)
	if err != nil {
		return nil, err
	}
	return resp.Pack()
}

func handleConfiguredMobileDNS(req *dns.Msg) (*dns.Msg, bool, error) {
	if len(req.Question) != 1 {
		return nil, false, nil
	}

	question := req.Question[0]
	domain := strings.TrimSuffix(question.Name, ".")
	if domain == "" {
		return nil, false, nil
	}

	logger := NewSessionLogger("[DNS]")
	dstHost, policy, failed, blocked, domainNotFound := genPolicy(logger, domain, false, true)
	if domainNotFound {
		return nil, false, nil
	}

	resp := new(dns.Msg)
	resp.SetReply(req)
	resp.RecursionAvailable = true

	if failed {
		resp.SetRcode(req, dns.RcodeServerFailure)
		return resp, true, nil
	}
	if blocked || (policy != nil && policy.Mode == ModeBlock) {
		resp.SetRcode(req, dns.RcodeRefused)
		return resp, true, nil
	}

	switch question.Qtype {
	case dns.TypeA, dns.TypeAAAA:
		answerIP, err := resolveMobileDNSAnswerIP(dstHost, question.Qtype, policy)
		if err != nil {
			resp.SetRcode(req, dns.RcodeServerFailure)
			return resp, true, nil
		}
		if answerIP == nil {
			return resp, true, nil
		}
		appendAddressAnswer(resp, question, answerIP)
		rememberMobileDNSMapping(answerIP.String(), domain, mobileDNSAnswerTTL)
		return resp, true, nil
	case dns.TypeHTTPS, dns.TypeSVCB:
		// Do not leak upstream address hints for domains pinned by Lumine rules.
		return resp, true, nil
	default:
		return nil, false, nil
	}
}

func resolveMobileDNSAnswerIP(dstHost string, qtype uint16, policy *Policy) (net.IP, error) {
	ip := net.ParseIP(dstHost)
	if ip == nil {
		dnsMode := DNSModeIPv4Only
		if qtype == dns.TypeAAAA {
			dnsMode = DNSModeIPv6Only
		}
		if policy != nil {
			if qtype == dns.TypeA && policy.DNSMode == DNSModeIPv6Only {
				return nil, nil
			}
			if qtype == dns.TypeAAAA && policy.DNSMode == DNSModeIPv4Only {
				return nil, nil
			}
		}
		resolved, _, err := dnsResolve(dstHost, dnsMode)
		if err != nil {
			return nil, err
		}
		ip = net.ParseIP(resolved)
	}
	if ip == nil {
		return nil, nil
	}
	if qtype == dns.TypeA {
		if v4 := ip.To4(); v4 != nil {
			return v4, nil
		}
		return nil, nil
	}
	if ip.To4() == nil {
		return ip, nil
	}
	return nil, nil
}

func appendAddressAnswer(resp *dns.Msg, question dns.Question, ip net.IP) {
	header := dns.RR_Header{
		Name:   question.Name,
		Rrtype: question.Qtype,
		Class:  question.Qclass,
		Ttl:    mobileDNSAnswerTTL,
	}
	if question.Qtype == dns.TypeA {
		resp.Answer = append(resp.Answer, &dns.A{Hdr: header, A: ip.To4()})
		return
	}
	resp.Answer = append(resp.Answer, &dns.AAAA{Hdr: header, AAAA: ip})
}

func rememberMobileDNSMapping(ip, domain string, ttl uint32) {
	parsed := net.ParseIP(ip)
	if parsed == nil || domain == "" {
		return
	}
	if ttl == 0 {
		ttl = mobileDNSAnswerTTL
	}
	if ttl > 3600 {
		ttl = 3600
	}

	mobileDNSRecords.Lock()
	defer mobileDNSRecords.Unlock()
	mobileDNSRecords.byIP[parsed.String()] = mobileDNSRecord{
		domain:  domain,
		expires: time.Now().Add(time.Duration(ttl) * time.Second),
	}
}

func lookupMobileDNSMapping(ip string) (string, bool) {
	parsed := net.ParseIP(ip)
	if parsed == nil {
		return "", false
	}

	key := parsed.String()
	now := time.Now()
	mobileDNSRecords.RLock()
	record, ok := mobileDNSRecords.byIP[key]
	mobileDNSRecords.RUnlock()
	if !ok {
		return "", false
	}
	if now.After(record.expires) {
		mobileDNSRecords.Lock()
		delete(mobileDNSRecords.byIP, key)
		mobileDNSRecords.Unlock()
		return "", false
	}
	return record.domain, true
}
