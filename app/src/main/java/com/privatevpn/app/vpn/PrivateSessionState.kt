package com.privatevpn.app.vpn

data class PrivateSessionState(
    val enabled: Boolean = false,
    val startedAtMs: Long? = null
)
