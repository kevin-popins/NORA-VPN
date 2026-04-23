package com.privatevpn.app.profiles.model

data class ImportedProfileDraft(
    val displayName: String,
    val type: ProfileType,
    val sourceRaw: String,
    val normalizedJson: String?,
    val dnsServers: List<String>,
    val dnsFallbackApplied: Boolean,
    val isPartialImport: Boolean,
    val importWarnings: List<String>
)
