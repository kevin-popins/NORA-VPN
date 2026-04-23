package com.privatevpn.app.settings

import com.privatevpn.app.core.dns.DefaultDnsProvider

data class DnsState(
    val mode: DnsMode = DnsMode.FROM_PROFILE,
    val resolvedSource: DnsResolvedSource = DnsResolvedSource.APP_DEFAULT,
    val resolvedServers: List<String> = DefaultDnsProvider.defaultServers,
    val customServers: List<String> = emptyList(),
    val profileServers: List<String> = emptyList(),
    val activeProfileName: String? = null
)
