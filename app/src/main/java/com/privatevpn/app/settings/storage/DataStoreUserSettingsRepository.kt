package com.privatevpn.app.settings.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.privatevpn.app.settings.DnsMode
import com.privatevpn.app.settings.SettingsState
import com.privatevpn.app.settings.SocksSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore by preferencesDataStore(name = "privatevpn_user_settings")

class DataStoreUserSettingsRepository(
    private val context: Context
) : UserSettingsRepository {

    override val settings: Flow<SettingsState> = context.userSettingsDataStore.data
        .map(::mapPreferencesToState)

    override suspend fun setAutoConnectOnLaunch(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_CONNECT] = enabled
        }
    }

    override suspend fun setVerboseLogs(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.VERBOSE_LOGS] = enabled
        }
    }

    override suspend fun setNotificationPermissionPromptShown(shown: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_PERMISSION_PROMPT_SHOWN] = shown
        }
    }

    override suspend fun setLocalhostSocksOnboardingShown(shown: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.LOCALHOST_SOCKS_ONBOARDING_SHOWN] = shown
        }
    }

    override suspend fun setActiveProfile(profileId: String?) {
        context.userSettingsDataStore.edit { prefs ->
            if (profileId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
            } else {
                prefs[Keys.ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    override suspend fun setPrivateSession(enabled: Boolean, startedAtMs: Long?) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.PRIVATE_SESSION_ENABLED] = enabled
            if (enabled && startedAtMs != null) {
                prefs[Keys.PRIVATE_SESSION_STARTED_AT] = startedAtMs
            } else {
                prefs.remove(Keys.PRIVATE_SESSION_STARTED_AT)
            }
        }
    }

    override suspend fun setPrivateSessionTrustedPackages(packageNames: Set<String>) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.PRIVATE_SESSION_TRUSTED_PACKAGES] = packageNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .sorted()
                .joinToString(PACKAGES_SEPARATOR)
        }
    }

    override suspend fun setDnsMode(mode: DnsMode) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.DNS_MODE] = mode.name
        }
    }

    override suspend fun setCustomDnsServers(servers: List<String>) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DNS_SERVERS] = servers.joinToString(",")
        }
    }

    override suspend fun setSocksSettings(settings: SocksSettings) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.SOCKS_ENABLED] = settings.enabled
            prefs[Keys.SOCKS_PORT] = settings.port
            prefs[Keys.SOCKS_LOGIN] = settings.login
            prefs[Keys.SOCKS_PASSWORD] = settings.password
        }
    }

    private fun mapPreferencesToState(prefs: Preferences): SettingsState {
        val dnsMode = prefs[Keys.DNS_MODE]
            ?.let { value -> DnsMode.entries.firstOrNull { it.name == value } }
            ?: DnsMode.FROM_PROFILE

        return SettingsState(
            autoConnectOnLaunch = prefs[Keys.AUTO_CONNECT] ?: false,
            verboseLogs = prefs[Keys.VERBOSE_LOGS] ?: true,
            notificationPermissionPromptShown = prefs[Keys.NOTIFICATION_PERMISSION_PROMPT_SHOWN] ?: false,
            localhostSocksOnboardingShown = prefs[Keys.LOCALHOST_SOCKS_ONBOARDING_SHOWN] ?: false,
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID],
            privateSessionEnabled = prefs[Keys.PRIVATE_SESSION_ENABLED] ?: false,
            privateSessionStartedAtMs = prefs[Keys.PRIVATE_SESSION_STARTED_AT],
            privateSessionTrustedPackages = (prefs[Keys.PRIVATE_SESSION_TRUSTED_PACKAGES] ?: "")
                .split(PACKAGES_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet(),
            dnsMode = dnsMode,
            customDnsServers = (prefs[Keys.CUSTOM_DNS_SERVERS] ?: "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() },
            socksSettings = SocksSettings(
                enabled = prefs[Keys.SOCKS_ENABLED] ?: false,
                port = prefs[Keys.SOCKS_PORT] ?: 1080,
                login = prefs[Keys.SOCKS_LOGIN] ?: "",
                password = prefs[Keys.SOCKS_PASSWORD] ?: ""
            )
        )
    }

    private object Keys {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val VERBOSE_LOGS = booleanPreferencesKey("verbose_logs")
        val NOTIFICATION_PERMISSION_PROMPT_SHOWN = booleanPreferencesKey("notification_permission_prompt_shown")
        val LOCALHOST_SOCKS_ONBOARDING_SHOWN = booleanPreferencesKey("localhost_socks_onboarding_shown")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val PRIVATE_SESSION_ENABLED = booleanPreferencesKey("private_session_enabled")
        val PRIVATE_SESSION_STARTED_AT = longPreferencesKey("private_session_started_at")
        val PRIVATE_SESSION_TRUSTED_PACKAGES = stringPreferencesKey("private_session_trusted_packages")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val CUSTOM_DNS_SERVERS = stringPreferencesKey("custom_dns_servers")
        val SOCKS_ENABLED = booleanPreferencesKey("socks_enabled")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val SOCKS_LOGIN = stringPreferencesKey("socks_login")
        val SOCKS_PASSWORD = stringPreferencesKey("socks_password")
    }

    private companion object {
        const val PACKAGES_SEPARATOR = ";"
    }
}
