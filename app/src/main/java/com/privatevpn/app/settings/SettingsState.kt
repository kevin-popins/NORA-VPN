package com.privatevpn.app.settings

data class SettingsState(
    val autoConnectOnLaunch: Boolean = false,
    val verboseLogs: Boolean = true,
    val notificationPermissionPromptShown: Boolean = false,
    val localhostSocksOnboardingShown: Boolean = false,
    val activeProfileId: String? = null,
    val privateSessionEnabled: Boolean = false,
    val privateSessionStartedAtMs: Long? = null,
    val privateSessionTrustedPackages: Set<String> = emptySet(),
    val dnsMode: DnsMode = DnsMode.FROM_PROFILE,
    val customDnsServers: List<String> = emptyList(),
    val socksSettings: SocksSettings = SocksSettings()
)
