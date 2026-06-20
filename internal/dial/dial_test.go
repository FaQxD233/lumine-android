package dial

import (
	"net"
	"sync/atomic"
	"testing"
	"time"
)

func TestStartLaddrMonitorCancelsPreviousMonitor(t *testing.T) {
	t.Cleanup(stopLaddrMonitor)

	var firstTicks atomic.Int32
	var secondTicks atomic.Int32

	startLaddrMonitor(time.Millisecond, func() (net.IP, net.IP, string, error) {
		firstTicks.Add(1)
		return nil, nil, "", nil
	})
	waitForTicks(t, &firstTicks, 1)

	startLaddrMonitor(time.Millisecond, func() (net.IP, net.IP, string, error) {
		secondTicks.Add(1)
		return nil, nil, "", nil
	})
	waitForTicks(t, &secondTicks, 1)

	firstAfterCancel := firstTicks.Load()
	time.Sleep(10 * time.Millisecond)
	if got := firstTicks.Load(); got > firstAfterCancel+1 {
		t.Fatalf("previous monitor kept running after replacement: before=%d after=%d", firstAfterCancel, got)
	}
}

func waitForTicks(t *testing.T, counter *atomic.Int32, min int32) {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		if counter.Load() >= min {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("monitor did not tick at least %d time(s)", min)
}
