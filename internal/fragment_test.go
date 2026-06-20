package lumine

import (
	"bytes"
	"io"
	"net"
	"testing"
	"time"
)

type recordingConn struct {
	bytes.Buffer
}

func (c *recordingConn) Read([]byte) (int, error) {
	return 0, io.EOF
}

func (c *recordingConn) Close() error {
	return nil
}

func (c *recordingConn) LocalAddr() net.Addr {
	return nil
}

func (c *recordingConn) RemoteAddr() net.Addr {
	return nil
}

func (c *recordingConn) SetDeadline(time.Time) error {
	return nil
}

func (c *recordingConn) SetReadDeadline(time.Time) error {
	return nil
}

func (c *recordingConn) SetWriteDeadline(time.Time) error {
	return nil
}

func TestSendRecordsDefaultsMissingFragmentCountsToSingleWrite(t *testing.T) {
	payload := []byte{0x16, 0x03, 0x01, 0x00, 0x04, 't', 'e', 's', 't'}
	conn := &recordingConn{}

	if err := sendRecords(conn, payload, 0, 0, 0, 0, false, false, false, false, 0); err != nil {
		t.Fatalf("sendRecords failed: %v", err)
	}

	if !bytes.Equal(conn.Bytes(), payload) {
		t.Fatalf("sendRecords wrote %x, want %x", conn.Bytes(), payload)
	}
}
