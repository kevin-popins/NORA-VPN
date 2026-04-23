package com.privatevpn.app.profiles.awg

data class AmneziaWgProfile(
    val displayName: String,
    val interfaceFields: Map<String, String>,
    val peerFields: Map<String, String>,
    val dnsServers: List<String>,
    val normalizedConfig: String,
    val importWarnings: List<String>
) {
    fun interfaceValue(key: String): String? = interfaceFields[key]
    fun peerValue(key: String): String? = peerFields[key]
    fun hasInterfaceKey(key: String): Boolean = interfaceFields.containsKey(key)
}
