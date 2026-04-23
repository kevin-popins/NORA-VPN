package com.privatevpn.app.core.backend.awg

import com.privatevpn.app.profiles.awg.AmneziaWgConfigSanitizer
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress

data class AmneziaWgRuntimeBuildInput(
    val sourceConfig: String,
    val resolvedDnsServers: List<String>,
    val privateSessionEnabled: Boolean,
    val trustedPackages: Set<String>
)

data class AmneziaWgRuntimeBuildResult(
    val config: Config,
    val runtimeConfigPreview: String,
    val notes: List<String>
)

class AmneziaWgRuntimeConfigBuilder {

    fun build(input: AmneziaWgRuntimeBuildInput): AmneziaWgRuntimeBuildResult {
        val sanitized = AmneziaWgConfigSanitizer.sanitize(
            rawConfig = input.sourceConfig,
            randomizeHeaderRanges = true
        )
        val sourceConfig = parse(sanitized.config)
        val sourceInterface = sourceConfig.`interface`
        val notes = sanitized.notes.toMutableList()

        val rebuiltInterface = rebuildInterface(
            source = sourceInterface,
            dnsServers = input.resolvedDnsServers,
            privateSessionEnabled = input.privateSessionEnabled,
            trustedPackages = input.trustedPackages,
            notes = notes
        )

        val configBuilder = Config.Builder()
            .setInterface(rebuiltInterface)
            .addPeers(sourceConfig.peers)
            .addProxies(sourceConfig.proxies)
            .setDnsSettings(sourceConfig.dnsSettings)

        val runtimeConfig = runCatching { configBuilder.build() }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "Не удалось собрать runtime AWG конфиг: ${error.message ?: "неизвестная ошибка"}",
                    error
                )
            }

        notes += "AWG runtime-конфиг собран"
        notes += "AWG fields: " + listOf(
            "Jc" to sourceInterface.junkPacketCount.isPresent,
            "Jmin" to sourceInterface.junkPacketMinSize.isPresent,
            "Jmax" to sourceInterface.junkPacketMaxSize.isPresent,
            "S1" to sourceInterface.initPacketJunkSize.isPresent,
            "S2" to sourceInterface.responsePacketJunkSize.isPresent,
            "S3" to sourceInterface.cookieReplyPacketJunkSize.isPresent,
            "S4" to sourceInterface.transportPacketJunkSize.isPresent,
            "H1" to sourceInterface.initPacketMagicHeader.isPresent,
            "H2" to sourceInterface.responsePacketMagicHeader.isPresent,
            "H3" to sourceInterface.underloadPacketMagicHeader.isPresent,
            "H4" to sourceInterface.transportPacketMagicHeader.isPresent,
            "I1" to sourceInterface.specialJunkI1.isPresent,
            "I2" to sourceInterface.specialJunkI2.isPresent,
            "I3" to sourceInterface.specialJunkI3.isPresent,
            "I4" to sourceInterface.specialJunkI4.isPresent,
            "I5" to sourceInterface.specialJunkI5.isPresent
        ).joinToString { "${it.first}=${it.second}" }

        return AmneziaWgRuntimeBuildResult(
            config = runtimeConfig,
            runtimeConfigPreview = runtimeConfig.toAwgQuickString(false, false),
            notes = notes
        )
    }

    private fun rebuildInterface(
        source: Interface,
        dnsServers: List<String>,
        privateSessionEnabled: Boolean,
        trustedPackages: Set<String>,
        notes: MutableList<String>
    ): Interface {
        val builder = Interface.Builder()
            .setKeyPair(source.keyPair)
            .addAddresses(source.addresses)
            .addDnsSearchDomains(source.dnsSearchDomains)

        val effectiveDns = dnsServers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                source.dnsServers.mapNotNull { it.hostAddress?.trim() }.filter { it.isNotBlank() }
            }

        effectiveDns.forEach { dns ->
            builder.addDnsServer(InetAddress.getByName(dns))
        }
        notes += "DNS для AWG runtime: ${effectiveDns.joinToString()}"

        source.listenPort.ifPresent { builder.setListenPort(it) }
        source.mtu.ifPresent { builder.setMtu(it) }
        source.junkPacketCount.ifPresent { builder.setJunkPacketCount(it) }
        source.junkPacketMinSize.ifPresent { builder.setJunkPacketMinSize(it) }
        source.junkPacketMaxSize.ifPresent { builder.setJunkPacketMaxSize(it) }
        source.initPacketJunkSize.ifPresent { builder.setInitPacketJunkSize(it) }
        source.responsePacketJunkSize.ifPresent { builder.setResponsePacketJunkSize(it) }
        source.cookieReplyPacketJunkSize.ifPresent { builder.setCookieReplyPacketJunkSize(it) }
        source.transportPacketJunkSize.ifPresent { builder.setTransportPacketJunkSize(it) }
        source.initPacketMagicHeader.ifPresent { builder.setInitPacketMagicHeader(it) }
        source.responsePacketMagicHeader.ifPresent { builder.setResponsePacketMagicHeader(it) }
        source.underloadPacketMagicHeader.ifPresent { builder.setUnderloadPacketMagicHeader(it) }
        source.transportPacketMagicHeader.ifPresent { builder.setTransportPacketMagicHeader(it) }
        source.specialJunkI1.ifPresent { builder.setSpecialJunkI1(it) }
        source.specialJunkI2.ifPresent { builder.setSpecialJunkI2(it) }
        source.specialJunkI3.ifPresent { builder.setSpecialJunkI3(it) }
        source.specialJunkI4.ifPresent { builder.setSpecialJunkI4(it) }
        source.specialJunkI5.ifPresent { builder.setSpecialJunkI5(it) }
        source.domainBlockingEnabled.ifPresent { enabled ->
            builder.parseDomainBlockingEnabled(if (enabled) "true" else "false")
        }
        source.preUp.forEach { builder.parsePreUp(it) }
        source.postUp.forEach { builder.parsePostUp(it) }
        source.preDown.forEach { builder.parsePreDown(it) }
        source.postDown.forEach { builder.parsePostDown(it) }

        if (privateSessionEnabled) {
            val validTrusted = trustedPackages
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            require(validTrusted.isNotEmpty()) {
                "В режиме приватной сессии AWG нужен хотя бы один trusted package"
            }
            builder.includeApplications(validTrusted)
            notes += "AWG private session includeApplications: ${validTrusted.joinToString()}"
        } else {
            builder.includeApplications(source.includedApplications)
            builder.excludeApplications(source.excludedApplications)
        }

        return runCatching { builder.build() }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "Ошибка построения Interface для AWG runtime: ${error.message ?: "неизвестная ошибка"}",
                    error
                )
            }
    }

    private fun parse(rawConfig: String): Config {
        return try {
            Config.parse(BufferedReader(StringReader(rawConfig)))
        } catch (error: BadConfigException) {
            throw IllegalArgumentException("Некорректный AWG конфиг: ${error.describe()}", error)
        }
    }
}

private fun BadConfigException.describe(): String {
    val sectionPart = "section=${section.name}"
    val locationPart = "location=${location.name}"
    val reasonPart = "reason=${reason.name}"
    val textPart = text?.toString()?.takeIf { it.isNotBlank() }?.let { "value=$it" } ?: "value=<empty>"
    return listOf(sectionPart, locationPart, reasonPart, textPart).joinToString(", ")
}
