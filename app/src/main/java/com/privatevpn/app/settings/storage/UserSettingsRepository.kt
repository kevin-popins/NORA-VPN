package com.privatevpn.app.settings.storage

import com.privatevpn.app.settings.DnsMode
import com.privatevpn.app.settings.SettingsState
import com.privatevpn.app.settings.SocksSettings
import kotlinx.coroutines.flow.Flow

interface UserSettingsRepository {
    val settings: Flow<SettingsState>

    suspend fun setAutoConnectOnLaunch(enabled: Boolean)
    suspend fun setVerboseLogs(enabled: Boolean)
    suspend fun setNotificationPermissionPromptShown(shown: Boolean)
    suspend fun setActiveProfile(profileId: String?)
    suspend fun setPrivateSession(enabled: Boolean, startedAtMs: Long?)
    suspend fun setPrivateSessionTrustedPackages(packageNames: Set<String>)
    suspend fun setDnsMode(mode: DnsMode)
    suspend fun setCustomDnsServers(servers: List<String>)
    suspend fun setSocksSettings(settings: SocksSettings)
}
