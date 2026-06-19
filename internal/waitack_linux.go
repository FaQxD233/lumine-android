//go:build linux

package lumine

import (
	"net"
	"time"

	E "github.com/moi-si/lumine/internal/errors"
	"golang.org/x/sys/unix"
)

const (
	waitForAckOverallTimeout = 5 * time.Second
	waitForAckEarlyThreshold = 20 * time.Millisecond
	waitForAckPollInterval    = 10 * time.Millisecond
)

func waitForAck(enabled bool, conn net.Conn, delay time.Duration) error {
	if !enabled {
		time.Sleep(delay)
		return nil
	}
	rawConn, err := getTCPRawConn(conn)
	if err != nil {
		return err
	}
	var innerErr error
	rawCtrlErr := rawConn.Control(func(fd uintptr) {
		start := time.Now()
		fdInt := int(fd)
		deadline := start.Add(waitForAckOverallTimeout)
		for {
			if time.Now().After(deadline) {
				innerErr = E.New("wait for ACK: timeout exceeded")
				return
			}
			var tcpInfo *unix.TCPInfo
			tcpInfo, innerErr = unix.GetsockoptTCPInfo(fdInt, unix.IPPROTO_TCP, unix.TCP_INFO)
			if innerErr != nil {
				return
			}
			if tcpInfo.Unacked == 0 {
				if time.Since(start) <= waitForAckEarlyThreshold {
					time.Sleep(delay)
				}
				return
			}
			time.Sleep(waitForAckPollInterval)
		}
	})
	if rawCtrlErr != nil {
		return E.WithStr("wait for ACK: raw control", rawCtrlErr)
	} else if innerErr != nil {
		return E.WithStr("wait for ACK", innerErr)
	}
	return nil
}
