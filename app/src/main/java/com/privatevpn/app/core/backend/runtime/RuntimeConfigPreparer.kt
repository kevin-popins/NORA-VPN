package com.privatevpn.app.core.backend.runtime

import com.privatevpn.app.core.backend.config.NormalizedConfig
import com.privatevpn.app.settings.SocksSettings

data class DataPlaneProxy(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

data class RuntimePreparationInput(
    val normalizedConfig: NormalizedConfig,
    val dnsServers: List<String>,
    val privateSessionEnabled: Boolean,
    val socksSettings: SocksSettings,
    val dataPlaneProxy: DataPlaneProxy
)

data class RuntimePreparationResult(
    val runtimeConfig: String,
    val notes: List<String>,
    val dataPlaneProxy: DataPlaneProxy
)

interface RuntimeConfigPreparer {
    fun prepare(input: RuntimePreparationInput): RuntimePreparationResult
}
