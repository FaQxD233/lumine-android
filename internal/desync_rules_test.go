package lumine

import "testing"

func TestParseTTLRulesAcceptsConstantRule(t *testing.T) {
	rules, err := parseTTLRules("3")
	if err != nil {
		t.Fatalf("parseTTLRules rejected constant rule: %v", err)
	}
	if rules != nil {
		t.Fatalf("parseTTLRules returned %d dynamic rules for constant rule", len(rules))
	}
}

func TestParseTTLRulesRejectsTrailingBareNumber(t *testing.T) {
	if _, err := parseTTLRules("1=1;3"); err == nil {
		t.Fatalf("parseTTLRules accepted trailing bare number after dynamic rules")
	}
}
