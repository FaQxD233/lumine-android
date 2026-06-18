package lumine

import "github.com/miekg/dns"

const vpnDNSIPv4Addr = "172.19.0.2"

func VPNDNSIPv4() string {
	return vpnDNSIPv4Addr
}

func IsVPNDNSAddress(host string, port int) bool {
	return host == vpnDNSIPv4Addr && port == 53
}

func HandleDNSQueryPacket(payload []byte) ([]byte, error) {
	req := new(dns.Msg)
	if err := req.Unpack(payload); err != nil {
		return nil, err
	}
	resp, err := dnsExchange(req)
	if err != nil {
		return nil, err
	}
	return resp.Pack()
}
