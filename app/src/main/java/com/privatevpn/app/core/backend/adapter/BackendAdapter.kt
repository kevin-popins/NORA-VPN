package com.privatevpn.app.core.backend.adapter

import com.privatevpn.app.profiles.model.VpnProfile
import com.privatevpn.app.settings.SocksSettings

data class BackendStartResult(
    val runtimeConfigPreview: String,
    val notes: List<String>
)

interface BackendAdapter {
    fun start(
        profile: VpnProfile,
        dnsServers: List<String>,
        privateSessionEnabled: Boolean,
        privateSessionTrustedPackages: Set<String>,
        socksSettings: SocksSettings
    ): Result<BackendStartResult>

    fun stop(): Result<Unit>
}
