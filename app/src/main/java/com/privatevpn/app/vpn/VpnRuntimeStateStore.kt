package com.privatevpn.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnRuntimeStateStore {
    private val _status = MutableStateFlow(VpnConnectionStatus.NO_PERMISSION)
    val status: StateFlow<VpnConnectionStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _appTrafficMode = MutableStateFlow(AppTrafficMode.UNKNOWN)
    val appTrafficMode: StateFlow<AppTrafficMode> = _appTrafficMode.asStateFlow()

    private val _internalDataPlanePort = MutableStateFlow<Int?>(null)
    val internalDataPlanePort: StateFlow<Int?> = _internalDataPlanePort.asStateFlow()

    private val _lastSelectedProfileName = MutableStateFlow<String?>(null)
    val lastSelectedProfileName: StateFlow<String?> = _lastSelectedProfileName.asStateFlow()

    fun setStatus(status: VpnConnectionStatus) {
        _status.value = status
        if (status != VpnConnectionStatus.ERROR) {
            _lastError.value = null
        }
    }

    fun setError(message: String) {
        _status.value = VpnConnectionStatus.ERROR
        _lastError.value = message
    }

    fun clearError() {
        _lastError.value = null
    }

    fun setAppTrafficMode(mode: AppTrafficMode) {
        _appTrafficMode.value = mode
    }

    fun setInternalDataPlanePort(port: Int?) {
        _internalDataPlanePort.value = port
    }

    fun setLastSelectedProfileName(profileName: String?) {
        _lastSelectedProfileName.value = profileName?.trim()?.takeIf { it.isNotBlank() }
    }
}
