package com.privatevpn.app.profiles.awg

import org.amnezia.awg.config.Config
import org.amnezia.awg.config.BadConfigException
import java.io.BufferedReader
import java.io.StringReader

class AmneziaWgConfigParser {

    fun parse(rawInput: String): AmneziaWgProfile {
        val raw = rawInput.trim()
        require(raw.isNotBlank()) { "Пустой AmneziaWG конфиг" }

        val sanitized = AmneziaWgConfigSanitizer.sanitize(
            rawConfig = raw,
            randomizeHeaderRanges = false
        )
        val parsedConfig = parseWithRuntime(sanitized.config)
        val sections = parseSections(raw)
        val interfaceFields = sections[SECTION_INTERFACE]
            ?: throw IllegalArgumentException("В AWG конфиге отсутствует секция [Interface]")
        val peerFields = sections[SECTION_PEER]
            ?: throw IllegalArgumentException("В AWG конфиге отсутствует секция [Peer]")

        val endpoint = peerFields[FIELD_ENDPOINT].orEmpty().trim()
        val displayName = endpoint.ifBlank { DEFAULT_DISPLAY_NAME }
        val dnsServers = parsedConfig.`interface`.dnsServers
            .mapNotNull { it.hostAddress?.trim() }
            .filter { it.isNotBlank() }

        return AmneziaWgProfile(
            displayName = displayName,
            interfaceFields = interfaceFields,
            peerFields = peerFields,
            dnsServers = dnsServers,
            normalizedConfig = parsedConfig.toAwgQuickString(false, false),
            importWarnings = sanitized.notes
        )
    }

    fun parseWithRuntime(rawConfig: String): Config {
        return try {
            Config.parse(BufferedReader(StringReader(rawConfig)))
        } catch (bad: BadConfigException) {
            throw IllegalArgumentException("Некорректный AWG конфиг: ${bad.describe()}", bad)
        }
    }

    private fun parseSections(rawConfig: String): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null

        rawConfig.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
                return@forEach
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                val sectionName = line.removePrefix("[").removeSuffix("]").trim()
                if (sectionName.isNotBlank()) {
                    currentSection = sectionName
                    result.getOrPut(sectionName) { linkedMapOf() }
                }
                return@forEach
            }

            val section = currentSection ?: return@forEach
            val delimiterIndex = line.indexOf('=')
            if (delimiterIndex < 0) return@forEach

            val key = line.substring(0, delimiterIndex).trim()
            if (key.isBlank()) return@forEach
            val value = line.substring(delimiterIndex + 1).trim()
            result.getOrPut(section) { linkedMapOf() }[key] = value
        }

        return result
    }

    companion object {
        private const val SECTION_INTERFACE = "Interface"
        private const val SECTION_PEER = "Peer"
        private const val FIELD_ENDPOINT = "Endpoint"
        private const val DEFAULT_DISPLAY_NAME = "AmneziaWG 2.0"
    }
}

private fun BadConfigException.describe(): String {
    val sectionPart = "section=${section.name}"
    val locationPart = "location=${location.name}"
    val reasonPart = "reason=${reason.name}"
    val textPart = text?.toString()?.takeIf { it.isNotBlank() }?.let { "value=$it" } ?: "value=<empty>"
    return listOf(sectionPart, locationPart, reasonPart, textPart).joinToString(", ")
}
