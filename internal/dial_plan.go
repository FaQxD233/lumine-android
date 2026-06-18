package lumine

import (
	"errors"
	"net"

	F "github.com/moi-si/lumine/internal/format"
	log "github.com/moi-si/mylog"
)

type RequestSource string

const (
	RequestSourceUnknown RequestSource = ""
	RequestSourceMobile  RequestSource = "mobile_tun"
	RequestSourceSOCKS5  RequestSource = "socks5"
	RequestSourceHTTP    RequestSource = "http"
)

type DomainTargetMode uint8

const (
	ResolveDomainTarget DomainTargetMode = iota
	PreserveDomainTarget
)

type RequestContext struct {
	Source           RequestSource
	Host             string
	Port             int
	DomainTargetMode DomainTargetMode
}

type DialPlan struct {
	Source          RequestSource
	OriginHost      string
	OriginPort      int
	RecoveredDomain string
	MatchedDomain   bool
	MatchedIP       bool
	TargetHost      string
	TargetPort      int
	Policy          Policy
	Blocked         bool
}

func (p DialPlan) TargetAddress() string {
	if p.TargetPort <= 0 {
		return p.TargetHost
	}
	return net.JoinHostPort(p.TargetHost, F.Int(p.TargetPort))
}

func (p DialPlan) OriginAddress() string {
	if p.OriginPort <= 0 {
		return p.OriginHost
	}
	return net.JoinHostPort(p.OriginHost, F.Int(p.OriginPort))
}

func PlanRequest(req RequestContext, logger *log.Logger) (DialPlan, error) {
	if req.Host == "" {
		return DialPlan{}, errors.New("request host is empty")
	}

	isIP := net.ParseIP(req.Host) != nil
	dstHost, policy, failed, blocked, _ := genPolicy(logger, req.Host, isIP, false)
	if failed {
		return DialPlan{}, errors.New("failed to resolve dial plan")
	}
	if policy == nil {
		policy = &defaultPolicy
	}

	targetPort := req.Port
	if policy.Port > 0 {
		targetPort = policy.Port
	}

	return DialPlan{
		Source:        req.Source,
		OriginHost:    req.Host,
		OriginPort:    req.Port,
		MatchedDomain: !isIP,
		MatchedIP:     isIP,
		TargetHost:    dstHost,
		TargetPort:    targetPort,
		Policy:        *policy,
		Blocked:       blocked,
	}, nil
}
