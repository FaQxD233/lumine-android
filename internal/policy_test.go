package lumine

import (
	"testing"

	"github.com/moi-si/addrtrie"
)

func TestMergePoliciesAppliesRuntimeDefaults(t *testing.T) {
	merged := mergePolicies(&Policy{Mode: ModeTTLD}, &Policy{})

	if merged.Mode != ModeTTLD {
		t.Fatalf("mode = %v, want %v", merged.Mode, ModeTTLD)
	}
	if merged.DNSMode != DNSModeDefault {
		t.Fatalf("dns mode = %v, want %v", merged.DNSMode, DNSModeDefault)
	}
	if merged.ConnectTimeout != defaultConnectTimeout {
		t.Fatalf("connect timeout = %v, want %v", merged.ConnectTimeout, defaultConnectTimeout)
	}
	if merged.Attempts != defaultTTLDAttempts {
		t.Fatalf("attempts = %d, want %d", merged.Attempts, defaultTTLDAttempts)
	}
	if merged.MaxTTL != defaultTTLDMaxTTL {
		t.Fatalf("max ttl = %d, want %d", merged.MaxTTL, defaultTTLDMaxTTL)
	}
	if merged.FakeSleep != defaultTTLDFakeSleep {
		t.Fatalf("fake sleep = %v, want %v", merged.FakeSleep, defaultTTLDFakeSleep)
	}
	if merged.SingleTimeout != defaultTTLDSingleTimeout {
		t.Fatalf("single timeout = %v, want %v", merged.SingleTimeout, defaultTTLDSingleTimeout)
	}
}

func TestResolveDoHPolicyHostKeepsOriginalWithoutRedirect(t *testing.T) {
	oldHostsMatcher := hostsMatcher
	hostsMatcher = addrtrie.NewDomainMatcher[string]()
	t.Cleanup(func() {
		hostsMatcher = oldHostsMatcher
	})

	policy := normalizePolicyDefaults(Policy{})
	host, err := resolveDoHPolicyHost("dns.google", &policy)
	if err != nil {
		t.Fatalf("resolve DoH policy host: %v", err)
	}
	if host != "dns.google" {
		t.Fatalf("DoH host = %q, want original host", host)
	}
}
