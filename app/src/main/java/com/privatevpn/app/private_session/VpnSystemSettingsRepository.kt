package com.privatevpn.app.private_session

import android.content.Context
import android.content.Intent
import android.provider.Settings

class VpnSystemSettingsRepository(
    private val appContext: Context
) {

    fun readSystemVpnIntegrationState(): SystemVpnIntegrationState {
        val resolver = appContext.contentResolver

        val alwaysOnPackage = runCatching {
            Settings.Secure.getString(resolver, KEY_ALWAYS_ON_VPN_APP)
        }.getOrNull()

        val lockdownEnabled = runCatching {
            Settings.Secure.getInt(resolver, KEY_ALWAYS_ON_VPN_LOCKDOWN, 0) == 1
        }.getOrNull()

        val readable = alwaysOnPackage != null || lockdownEnabled != null

        return SystemVpnIntegrationState(
            readable = readable,
            alwaysOnPackage = alwaysOnPackage,
            lockdownEnabled = lockdownEnabled ?: false,
            privateVpnAlwaysOn = alwaysOnPackage == appContext.packageName
        )
    }

    fun buildOpenVpnSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VPN_SETTINGS)
    }

    private companion object {
        const val KEY_ALWAYS_ON_VPN_APP = "always_on_vpn_app"
        const val KEY_ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"
    }
}
