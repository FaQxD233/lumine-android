package mobile

import (
	"encoding/json"
	"sync"
	"time"
)

type runtimeStats struct {
	StartedAt       string       `json:"started_at"`
	TCPConnections  uint64       `json:"tcp_connections"`
	UDPFlows        uint64       `json:"udp_flows"`
	DNSQueries      uint64       `json:"dns_queries"`
	BlockedRequests uint64       `json:"blocked_requests"`
	FailedRequests  uint64       `json:"failed_requests"`
	RecentEvents    []statsEvent `json:"recent_events"`
}

type statsEvent struct {
	Time    string `json:"time"`
	Type    string `json:"type"`
	Target  string `json:"target"`
	Mode    string `json:"mode,omitempty"`
	Outcome string `json:"outcome"`
	Detail  string `json:"detail,omitempty"`
}

var statsState = struct {
	sync.Mutex
	startedAt       time.Time
	tcpConnections  uint64
	udpFlows        uint64
	dnsQueries      uint64
	blockedRequests uint64
	failedRequests  uint64
	events          []statsEvent
}{
	events: make([]statsEvent, 0, 80),
}

func resetStats() {
	statsState.Lock()
	defer statsState.Unlock()
	statsState.startedAt = time.Now()
	statsState.tcpConnections = 0
	statsState.udpFlows = 0
	statsState.dnsQueries = 0
	statsState.blockedRequests = 0
	statsState.failedRequests = 0
	statsState.events = statsState.events[:0]
}

func recordStatEvent(event statsEvent) {
	statsState.Lock()
	defer statsState.Unlock()
	if event.Time == "" {
		event.Time = time.Now().Format("15:04:05")
	}
	statsState.events = append(statsState.events, event)
	if len(statsState.events) > 80 {
		copy(statsState.events, statsState.events[len(statsState.events)-80:])
		statsState.events = statsState.events[:80]
	}
}

func recordTCPStat(target, mode string) {
	statsState.Lock()
	statsState.tcpConnections++
	statsState.Unlock()
	recordStatEvent(statsEvent{Type: "TCP", Target: target, Mode: mode, Outcome: "ok"})
}

func recordUDPStat(target, mode string) {
	statsState.Lock()
	statsState.udpFlows++
	statsState.Unlock()
	recordStatEvent(statsEvent{Type: "UDP", Target: target, Mode: mode, Outcome: "ok"})
}

func recordDNSStat(target, outcome, detail string) {
	statsState.Lock()
	statsState.dnsQueries++
	if outcome == "failed" {
		statsState.failedRequests++
	}
	statsState.Unlock()
	recordStatEvent(statsEvent{Type: "DNS", Target: target, Outcome: outcome, Detail: detail})
}

func recordBlockedStat(kind, target string) {
	statsState.Lock()
	statsState.blockedRequests++
	statsState.Unlock()
	recordStatEvent(statsEvent{Type: kind, Target: target, Outcome: "blocked"})
}

func recordFailedStat(kind, target, detail string) {
	statsState.Lock()
	statsState.failedRequests++
	statsState.Unlock()
	recordStatEvent(statsEvent{Type: kind, Target: target, Outcome: "failed", Detail: detail})
}

func GetStats() string {
	statsState.Lock()
	snapshot := runtimeStats{
		StartedAt:       statsState.startedAt.Format(time.RFC3339),
		TCPConnections:  statsState.tcpConnections,
		UDPFlows:        statsState.udpFlows,
		DNSQueries:      statsState.dnsQueries,
		BlockedRequests: statsState.blockedRequests,
		FailedRequests:  statsState.failedRequests,
		RecentEvents:    append([]statsEvent(nil), statsState.events...),
	}
	statsState.Unlock()

	payload, err := json.Marshal(snapshot)
	if err != nil {
		return "{}"
	}
	return string(payload)
}
