package com.privatevpn.app.settings

data class SocksSettings(
    val enabled: Boolean = false,
    val port: Int = 1080,
    val login: String = "",
    val password: String = ""
)
