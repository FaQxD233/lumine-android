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

	originHost := req.Host
	isIP := net.ParseIP(req.Host) != nil
	policyHost := req.Host
	policyIsIP := isIP
	recoveredDomain := ""
	if req.Source == RequestSourceMobile && isIP {
		if domain, ok := lookupMobileDNSMapping(req.Host); ok {
			recoveredDomain = domain
			policyHost = domain
			policyIsIP = false
		}
	}

	dstHost, policy, failed, blocked, _ := genPolicy(logger, policyHost, policyIsIP, false)
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
		Source:          req.Source,
		OriginHost:      originHost,
		OriginPort:      req.Port,
		RecoveredDomain: recoveredDomain,
		MatchedDomain:   !policyIsIP,
		MatchedIP:       isIP,
		TargetHost:      dstHost,
		TargetPort:      targetPort,
		Policy:          *policy,
		Blocked:         blocked,
	}, nil
}
