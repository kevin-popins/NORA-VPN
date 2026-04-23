package com.privatevpn.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsControllerCompat
import com.privatevpn.app.ui.PrivateVpnApp
import com.privatevpn.app.ui.theme.PrivateVpnTheme
import com.privatevpn.app.vpn.VpnQuickSettingsTileService

class MainActivity : ComponentActivity() {

    private var requestVpnPermissionFromIntent by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        consumeIntentFlags(intent)
        setContent {
            PrivateVpnTheme {
                PrivateVpnApp(
                    requestVpnPermissionOnStart = requestVpnPermissionFromIntent,
                    onRequestVpnPermissionConsumed = { requestVpnPermissionFromIntent = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntentFlags(intent)
    }

    private fun consumeIntentFlags(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(VpnQuickSettingsTileService.EXTRA_REQUEST_VPN_PERMISSION, false) == true) {
            requestVpnPermissionFromIntent = true
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}
