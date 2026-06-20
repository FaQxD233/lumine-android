package mobile

import "testing"

func TestCheckConfigRejectsCoreInvalidValues(t *testing.T) {
	errText := CheckConfig(`{"log_level":"WARN","dns_addr":"1.1.1.1:53"}`)
	if errText == "" {
		t.Fatalf("CheckConfig accepted invalid log level")
	}
}

func TestCheckConfigAcceptsValidConfig(t *testing.T) {
	errText := CheckConfig(`{"log_level":"INFO","dns_addr":"1.1.1.1:53"}`)
	if errText != "" {
		t.Fatalf("CheckConfig rejected valid config: %s", errText)
	}
}
