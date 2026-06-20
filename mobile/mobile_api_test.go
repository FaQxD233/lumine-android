package mobile

import (
	"io"
	"net"
	"testing"
	"time"
)

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

func TestBlockQuicDropsUDP443BeforeRelay(t *testing.T) {
	SetBlockQuic(true)
	t.Cleanup(func() {
		SetBlockQuic(true)
	})

	base := &packetConnRecorder{}
	conn := &luminePacketConn{PacketConn: base}
	payload := []byte("quic")

	n, err := conn.WriteTo(payload, &net.UDPAddr{
		IP:   net.IPv4(203, 0, 113, 1),
		Port: 443,
	})
	if err != nil {
		t.Fatalf("WriteTo returned error: %v", err)
	}
	if n != len(payload) {
		t.Fatalf("WriteTo returned n=%d, want %d", n, len(payload))
	}
	if base.writes != 0 {
		t.Fatalf("UDP/443 reached base PacketConn writes=%d, want 0", base.writes)
	}
}

type packetConnRecorder struct {
	writes int
}

func (pc *packetConnRecorder) ReadFrom([]byte) (int, net.Addr, error) {
	return 0, nil, io.EOF
}

func (pc *packetConnRecorder) WriteTo([]byte, net.Addr) (int, error) {
	pc.writes++
	return 0, nil
}

func (pc *packetConnRecorder) Close() error {
	return nil
}

func (pc *packetConnRecorder) LocalAddr() net.Addr {
	return &net.UDPAddr{}
}

func (pc *packetConnRecorder) SetDeadline(time.Time) error {
	return nil
}

func (pc *packetConnRecorder) SetReadDeadline(time.Time) error {
	return nil
}

func (pc *packetConnRecorder) SetWriteDeadline(time.Time) error {
	return nil
}
