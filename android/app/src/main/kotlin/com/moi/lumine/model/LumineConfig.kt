package com.moi.lumine.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LumineConfig(
    @Json(name = "log_level") val logLevel: String = "INFO",
    @Json(name = "socks5_address") val socks5Address: String = "127.0.0.1:1080",
    @Json(name = "http_address") val httpAddress: String = "127.0.0.1:1225",
    @Json(name = "outbound_binding") val outboundBinding: OutboundBinding = OutboundBinding(),
    @Json(name = "dns_addr") val dnsAddr: String = "https://xwfpeb16ii.cloudflare-gateway.com/dns-query",
    @Json(name = "socks5_for_doh") val socks5ForDoh: String = "",
    @Json(name = "udp_minsize") val udpMinSize: Int = 4096,
    @Json(name = "dns_singleflight") val dnsSingleFlight: Boolean = false,
    @Json(name = "dns_cache_ttl") val dnsCacheTtl: Int = 3600,
    @Json(name = "dns_cache_cap") val dnsCacheCap: Int = 4096,
    @Json(name = "ttl_singleflight") val ttlSingleFlight: Boolean = true,
    @Json(name = "ttl_cache_ttl") val ttlCacheTtl: Int = 86400,
    @Json(name = "ttl_cache_cap") val ttlCacheCap: Int = 1024,
    @Json(name = "fake_ttl_rules") val fakeTtlRules: String = "0-1;3=3;5-1;8-2;13-3;20=18",
    @Json(name = "transmit_file_limit") val transmitFileLimit: Int = 2,
    @Json(name = "ip_pools") val ipPools: Map<String, IPPool> = emptyMap(),
    @Json(name = "hosts") val hosts: Map<String, String> = emptyMap(),
    @Json(name = "default_policy") val defaultPolicy: Policy = Policy(),
    @Json(name = "ip_policies") val ipPolicies: Map<String, Policy> = emptyMap(),
    @Json(name = "domain_policies") val domainPolicies: Map<String, Policy> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class OutboundBinding(
    @Json(name = "method") val method: String = "off",
    @Json(name = "manual_select") val manualSelect: Boolean = true,
    @Json(name = "dial_ipv4_target") val dialIpv4Target: String = "8.8.8.8:53",
    @Json(name = "dial_ipv6_target") val dialIpv6Target: String = "[2001:4860:4860::8888]:53",
    @Json(name = "update_interval") val updateInterval: String = "30m"
)

@JsonClass(generateAdapter = true)
data class IPPool(
    @Json(name = "ips") val ips: List<String> = emptyList(),
    @Json(name = "port") val port: Int = 443,
    @Json(name = "top_ip_count") val topIpCount: Int? = null,
    @Json(name = "update_interval") val updateInterval: String? = null
)

@JsonClass(generateAdapter = true)
data class Policy(
    @Json(name = "reply_first") val replyFirst: Boolean? = null,
    @Json(name = "sniff_override") val sniffOverride: String? = null,
    @Json(name = "dns_mode") val dnsMode: String? = null,
    @Json(name = "connect_timeout") val connectTimeout: String? = null,
    @Json(name = "tls13_only") val tls13Only: Boolean? = null,
    @Json(name = "http_status") val httpStatus: Int? = null,
    @Json(name = "mode") val mode: String? = null,
    @Json(name = "mod_minor_ver") val modMinorVer: Boolean? = null,
    @Json(name = "num_records") val numRecords: Int? = null,
    @Json(name = "num_segs") val numSegs: Int? = null,
    @Json(name = "wait_for_ack") val waitForAck: Boolean? = null,
    @Json(name = "send_interval") val sendInterval: String? = null,
    @Json(name = "oob") val oob: Boolean? = null,
    @Json(name = "oob_ex") val oobEx: Boolean? = null,
    @Json(name = "fake_ttl") val fakeTtl: Int? = null,
    @Json(name = "fake_sleep") val fakeSleep: String? = null,
    @Json(name = "attempts") val attempts: Int? = null,
    @Json(name = "max_ttl") val maxTtl: Int? = null,
    @Json(name = "single_timeout") val singleTimeout: String? = null,
    @Json(name = "host") val host: String? = null,
    @Json(name = "map_to") val mapTo: String? = null,
    @Json(name = "port") val port: Int? = null
)
