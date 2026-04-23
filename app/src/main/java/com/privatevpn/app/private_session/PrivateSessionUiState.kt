package com.privatevpn.app.private_session

data class PrivateSessionUiState(
    val enabled: Boolean = false,
    val startedAtMs: Long? = null,
    val trustedPackages: Set<String> = emptySet(),
    val draftTrustedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appIcons: Map<String, ByteArray> = emptyMap(),
    val loadingInstalledApps: Boolean = false,
    val draftDirty: Boolean = false,
    val systemIntegration: SystemVpnIntegrationState = SystemVpnIntegrationState()
)
