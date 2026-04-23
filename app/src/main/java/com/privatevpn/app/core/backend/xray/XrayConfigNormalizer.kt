package com.privatevpn.app.core.backend.xray

import com.privatevpn.app.core.backend.config.ConfigNormalizer
import com.privatevpn.app.core.backend.config.NormalizedConfig
import com.privatevpn.app.profiles.model.VpnProfile

class XrayConfigNormalizer : ConfigNormalizer {
    override fun normalize(profile: VpnProfile): NormalizedConfig {
        val payload = profile.normalizedJson
            ?: throw IllegalArgumentException(
                "Профиль '${profile.displayName}' не содержит нормализованный Xray JSON"
            )

        return NormalizedConfig(
            profileId = profile.id,
            profileName = profile.displayName,
            profileType = profile.type.name,
            normalizedPayload = payload,
            warnings = profile.importWarnings
        )
    }
}
