package com.privatevpn.app.private_session

data class SystemVpnIntegrationState(
    val readable: Boolean = false,
    val alwaysOnPackage: String? = null,
    val lockdownEnabled: Boolean = false,
    val privateVpnAlwaysOn: Boolean = false
)
