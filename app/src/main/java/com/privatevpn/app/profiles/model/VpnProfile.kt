package com.privatevpn.app.profiles.model

data class VpnProfile(
    val id: String,
    val displayName: String,
    val type: ProfileType,
    val sourceRaw: String,
    val normalizedJson: String?,
    val dnsServers: List<String>,
    val dnsFallbackApplied: Boolean,
    val isPartialImport: Boolean,
    val importWarnings: List<String>,
    val importedAtMs: Long,
    val parentSubscriptionId: String? = null,
    val sourceOrder: Int = 0
)
