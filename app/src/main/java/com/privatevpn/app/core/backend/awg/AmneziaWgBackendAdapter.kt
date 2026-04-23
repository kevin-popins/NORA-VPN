package com.privatevpn.app.core.backend.awg

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.privatevpn.app.core.backend.adapter.BackendAdapter
import com.privatevpn.app.core.backend.adapter.BackendStartResult
import com.privatevpn.app.core.error.AppErrors
import com.privatevpn.app.profiles.model.VpnProfile
import com.privatevpn.app.settings.SocksSettings
import com.privatevpn.app.vpn.AppTrafficMode
import com.privatevpn.app.vpn.VpnConnectionStatus
import com.privatevpn.app.vpn.VpnQuickSettingsTileService
import com.privatevpn.app.vpn.VpnRuntimeStateStore
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.NoopTunnelActionHandler
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config

class AmneziaWgBackendAdapter(
    appContext: Context,
    private val runtimeConfigBuilder: AmneziaWgRuntimeConfigBuilder
) : BackendAdapter {

    private val context = appContext.applicationContext
    private val backend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GoBackend(context, NoopTunnelActionHandler())
    }
    private val stateLock = Any()
    private var currentConfig: Config? = null
    private var startInProgress = false

    private val tunnel = object : Tunnel {
        override fun getName(): String = "privatevpn-awg"

        override fun onStateChange(state: Tunnel.State) {
            when (state) {
                Tunnel.State.UP -> VpnRuntimeStateStore.setStatus(VpnConnectionStatus.CONNECTED)
                Tunnel.State.DOWN -> updateReadyOrNoPermission()
            }
        }

        override fun isIpv4ResolutionPreferred(): Boolean = true

        override fun isMetered(): Boolean = false
    }

    override fun start(
        profile: VpnProfile,
        dnsServers: List<String>,
        privateSessionEnabled: Boolean,
        privateSessionTrustedPackages: Set<String>,
        socksSettings: SocksSettings
    ): Result<BackendStartResult> {
        return runCatching {
            synchronized(stateLock) {
                if (startInProgress) {
                    throw IllegalStateException("Запуск AWG backend уже выполняется")
                }
                startInProgress = true
            }

            try {
                VpnRuntimeStateStore.setStatus(VpnConnectionStatus.CONNECTING)
                val runtime = runtimeConfigBuilder.build(
                    AmneziaWgRuntimeBuildInput(
                        sourceConfig = profile.sourceRaw,
                        resolvedDnsServers = dnsServers,
                        privateSessionEnabled = privateSessionEnabled,
                        trustedPackages = privateSessionTrustedPackages
                    )
                )
                val appliedState = backend.setState(tunnel, Tunnel.State.UP, runtime.config)
                if (appliedState != Tunnel.State.UP) {
                    throw IllegalStateException("AWG backend вернул состояние ${appliedState.name} вместо UP")
                }

                synchronized(stateLock) {
                    currentConfig = runtime.config
                }

                VpnRuntimeStateStore.setInternalDataPlanePort(null)
                VpnRuntimeStateStore.setLastSelectedProfileName(profile.displayName)
                VpnRuntimeStateStore.setAppTrafficMode(
                    if (privateSessionEnabled) {
                        AppTrafficMode.PRIVATE_SESSION_APP_EXCLUDED
                    } else {
                        AppTrafficMode.UNKNOWN
                    }
                )
                VpnRuntimeStateStore.setStatus(VpnConnectionStatus.CONNECTED)
                VpnQuickSettingsTileService.requestTileStateRefresh(context)

                val runtimeVersion = runCatching { backend.version }.getOrNull()
                val notes = mutableListOf<String>()
                if (!runtimeVersion.isNullOrBlank()) {
                    notes += "AmneziaWG runtime version: $runtimeVersion"
                }
                notes += runtime.notes
                if (socksSettings.enabled) {
                    notes += "В AWG-режиме пользовательский localhost SOCKS не используется backend-ом автоматически"
                }

                BackendStartResult(
                    runtimeConfigPreview = runtime.runtimeConfigPreview,
                    notes = notes
                )
            } finally {
                synchronized(stateLock) {
                    startInProgress = false
                }
            }
        }.onFailure { error ->
            val appError = AppErrors.awgRuntimeStartFailed(
                technicalReason = error.message ?: "Не удалось запустить AmneziaWG backend"
            )
            VpnRuntimeStateStore.setError(appError.toUiMessage())
            Log.w(TAG, appError.toLogMessage(), error)
        }
    }

    override fun stop(): Result<Unit> {
        return runCatching {
            val config = synchronized(stateLock) { currentConfig }
            if (config != null) {
                backend.setState(tunnel, Tunnel.State.DOWN, config)
            } else {
                runCatching {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                }
            }

            synchronized(stateLock) {
                currentConfig = null
                startInProgress = false
            }
            VpnRuntimeStateStore.setInternalDataPlanePort(null)
            VpnRuntimeStateStore.setAppTrafficMode(AppTrafficMode.UNKNOWN)
            updateReadyOrNoPermission()
            VpnQuickSettingsTileService.requestTileStateRefresh(context)
        }.onFailure { error ->
            val appError = AppErrors.awgRuntimeStopFailed(
                technicalReason = error.message ?: "Не удалось остановить AmneziaWG backend"
            )
            VpnRuntimeStateStore.setError(appError.toUiMessage())
            Log.w(TAG, appError.toLogMessage(), error)
            VpnQuickSettingsTileService.requestTileStateRefresh(context)
        }
    }

    private fun updateReadyOrNoPermission() {
        if (VpnService.prepare(context) == null) {
            VpnRuntimeStateStore.setStatus(VpnConnectionStatus.READY)
        } else {
            VpnRuntimeStateStore.setStatus(VpnConnectionStatus.NO_PERMISSION)
        }
    }

    private companion object {
        const val TAG = "PrivateVPN-AWG"
    }
}
