package lumine

import (
	"encoding/binary"
	"io"
	"net"
	"sync"
	"time"

	log "github.com/moi-si/mylog"
)

var (
	coreLogWriter   io.Writer
	coreLogWriterMu sync.RWMutex
)

func SetLogWriter(w io.Writer) {
	coreLogWriterMu.Lock()
	defer coreLogWriterMu.Unlock()
	coreLogWriter = w
}

func NewSessionLogger(prefix string) *log.Logger {
	coreLogWriterMu.RLock()
	w := coreLogWriter
	coreLogWriterMu.RUnlock()
	if w == nil {
		return log.New(io.Discard, prefix, log.LstdFlags, logLevel)
	}
	return log.New(w, prefix, log.LstdFlags, logLevel)
}

func DefaultDialTimeout(p Policy) time.Duration {
	if p.ConnectTimeout > 0 {
		return p.ConnectTimeout
	}
	return defaultConnectTimeout
}

func WrapTCPConn(conn net.Conn, plan DialPlan, logger *log.Logger) net.Conn {
	return &tunPolicyConn{
		Conn:   conn,
		plan:   plan,
		logger: logger,
	}
}

type tunPolicyConn struct {
	net.Conn

	plan    DialPlan
	logger  *log.Logger
	mu      sync.Mutex
	handled bool
	closed  bool
	pending []byte
}

func (c *tunPolicyConn) Write(b []byte) (int, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.closed {
		return 0, io.EOF
	}
	if c.handled {
		return c.Conn.Write(b)
	}

	c.pending = append(c.pending, b...)
	handled, err := c.tryHandlePendingLocked()
	if !handled {
		return len(b), nil
	}
	if err != nil {
		return 0, err
	}
	return len(b), nil
}

func (c *tunPolicyConn) tryHandlePendingLocked() (handled bool, err error) {
	if len(c.pending) < 5 {
		return false, nil
	}

	if c.pending[0] != tlsRecordTypeHandshake || c.pending[1] != tlsMajorVersion {
		c.handled = true
		err = c.flushPendingLocked()
		c.closed = err != nil
		return true, err
	}

	recordLen := tlsRecordHeaderLen + int(binary.BigEndian.Uint16(c.pending[3:5]))
	if recordLen < tlsRecordHeaderLen || len(c.pending) < recordLen {
		return false, nil
	}

	record := append([]byte(nil), c.pending[:recordLen]...)
	tail := append([]byte(nil), c.pending[recordLen:]...)
	err = c.handleTLSRecordLocked(record)
	c.handled = true
	c.pending = nil
	if err != nil {
		c.closed = true
		return true, err
	}
	if len(tail) > 0 {
		_, err = c.Conn.Write(tail)
		if err != nil {
			c.logger.Error("Forward buffered tail:", err)
			c.closed = true
			return true, err
		}
	}
	return true, nil
}

func (c *tunPolicyConn) flushPendingLocked() error {
	if len(c.pending) == 0 {
		return nil
	}
	_, err := c.Conn.Write(c.pending)
	if err != nil {
		c.logger.Error("Forward initial payload:", err)
	}
	c.pending = nil
	return err
}

func (c *tunPolicyConn) handleTLSRecordLocked(record []byte) error {
	prtVer, sniStart, sniLen, hasKeyShare, _, err := parseClientHello(record)
	if err != nil {
		c.logger.Debug("Parse ClientHello:", err)
		return c.flushPendingLocked()
	}

	p := c.plan.Policy
	if p.Mode == ModeTLSAlert {
		sendTLSAlert(c.logger, c.Conn, prtVer, tlsAlertAccessDenied, tlsAlertLevelFatal)
		_ = c.Conn.Close()
		return io.EOF
	}

	if p.TLS13Only.IsTrue() && !hasKeyShare {
		c.logger.Info("Connection blocked: key_share missing from ClientHello")
		sendTLSAlert(c.logger, c.Conn, prtVer, tlsAlertProtocolVersion, tlsAlertLevelFatal)
		_ = c.Conn.Close()
		return io.EOF
	}

	if sniStart > 0 && sniLen > 0 {
		sni := string(record[sniStart : sniStart+sniLen])
		if domainPolicy, ok := domainMatcher.Find(sni); ok {
			p = *mergePolicies(domainPolicy, &p)
			c.logger.Info("SNI policy:", sni, "->", p)
		}
	}

	switch p.Mode {
	case ModeBlock:
		_ = c.Conn.Close()
		return io.EOF
	case ModeTLSAlert:
		sendTLSAlert(c.logger, c.Conn, prtVer, tlsAlertAccessDenied, tlsAlertLevelFatal)
		_ = c.Conn.Close()
		return io.EOF
	}

	switch p.Mode {
	case ModeDirect, ModeRaw:
		_, err = c.Conn.Write(record)
	case ModeTLSRF:
		err = sendRecords(c.Conn, record, sniStart, sniLen,
			p.NumRecords, p.NumSegments,
			p.OOB.IsTrue(), p.OOBEx.IsTrue(),
			p.ModMinorVer.IsTrue(), p.WaitForAck.IsTrue(),
			p.SendInterval)
	case ModeTTLD:
		ipv6 := isIPv6(c.plan.TargetHost)
		ttl := p.FakeTTL
		if ttl == 0 || ttl == unsetInt {
			ttl, err = getFakeTTL(c.logger, &p, c.plan.TargetAddress(), ipv6)
			if err != nil {
				c.logger.Error("Get fake TTL:", err)
				return err
			}
		}
		err = desyncSend(c.Conn, ipv6, record, sniStart, sniLen, ttl, p.FakeSleep)
	default:
		_, err = c.Conn.Write(record)
	}
	if err != nil {
		c.logger.Error("Forward TLS record:", err)
	}
	return err
}

type directLuminePacketConn struct {
	net.PacketConn
	timeout time.Duration
}

func NewDirectPacketConn(timeout time.Duration) (net.PacketConn, error) {
	pc, err := net.ListenPacket("udp", "")
	if err != nil {
		return nil, err
	}
	return &directLuminePacketConn{PacketConn: pc, timeout: timeout}, nil
}

func (pc *directLuminePacketConn) WriteTo(b []byte, addr net.Addr) (int, error) {
	if udpAddr, ok := addr.(*net.UDPAddr); ok {
		return pc.PacketConn.WriteTo(b, udpAddr)
	}
	udpAddr, err := net.ResolveUDPAddr("udp", addr.String())
	if err != nil {
		return 0, err
	}
	return pc.PacketConn.WriteTo(b, udpAddr)
}
