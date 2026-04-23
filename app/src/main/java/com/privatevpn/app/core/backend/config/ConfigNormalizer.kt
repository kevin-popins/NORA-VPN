package com.privatevpn.app.core.backend.config

import com.privatevpn.app.profiles.model.VpnProfile

data class NormalizedConfig(
    val profileId: String,
    val profileName: String,
    val profileType: String,
    val normalizedPayload: String,
    val warnings: List<String>
)

interface ConfigNormalizer {
    fun normalize(profile: VpnProfile): NormalizedConfig
}
