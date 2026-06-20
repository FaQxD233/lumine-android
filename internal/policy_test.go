package lumine

import "testing"

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
