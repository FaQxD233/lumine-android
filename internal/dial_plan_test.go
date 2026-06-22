package lumine

import "testing"

func TestDialPlanTargetAddressFormatsIPv6WithPort(t *testing.T) {
	plan := DialPlan{
		TargetHost: "2607:f8b0:4005:80a::200e",
		TargetPort: 443,
	}

	got := plan.TargetAddress()
	want := "[2607:f8b0:4005:80a::200e]:443"
	if got != want {
		t.Fatalf("TargetAddress() = %q, want %q", got, want)
	}
}
