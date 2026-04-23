package com.privatevpn.app.profiles.importer

import android.net.Uri
import com.privatevpn.app.core.dns.DefaultDnsProvider
import com.privatevpn.app.profiles.awg.AmneziaWgConfigParser
import com.privatevpn.app.profiles.model.ImportedProfileDraft
import com.privatevpn.app.profiles.model.ProfileType
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

class ProfileImportParser {
    private val amneziaWgConfigParser = AmneziaWgConfigParser()

    fun parse(rawInput: String): ImportedProfileDraft {
        val input = normalize(rawInput).trim()
        require(input.isNotBlank()) { "Пустой профиль." }

        return when {
            input.startsWith(VLESS_PREFIX, ignoreCase = true) -> parseVless(input)
            input.startsWith(TROJAN_PREFIX, ignoreCase = true) -> parseUriProfile(input, ProfileType.TROJAN)
            input.startsWith(VMESS_PREFIX, ignoreCase = true) -> parseVmess(input)
            looksLikeAwgConf(input) -> parseAmneziaWgConf(input)
            looksLikeJson(input) -> parseXrayJson(input)
            else -> throw IllegalArgumentException("Неподдерживаемый формат профиля.")
        }
    }

    private fun parseVless(input: String): ImportedProfileDraft {
        return runCatching {
            val uri = Uri.parse(input)
            val address = uri.host?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("VLESS: отсутствует address")
            val port = uri.port.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("VLESS: отсутствует корректный port")
            val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("VLESS: отсутствует uuid")

            val displayName = uri.fragment?.takeIf { it.isNotBlank() }
                ?: "$address:$port"

            val transport = uri.getQueryParameter("type")?.takeIf { it.isNotBlank() } ?: "tcp"
            val flow = uri.getQueryParameter("flow")?.takeIf { it.isNotBlank() }
            val security = uri.getQueryParameter("security")?.lowercase() ?: "none"
            val publicKey = uri.getQueryParameter("pbk")
                ?: uri.getQueryParameter("publicKey")
                ?: uri.getQueryParameter("password")
            val shortId = uri.getQueryParameter("sid")
                ?: uri.getQueryParameter("shortId")
            val serverName = uri.getQueryParameter("sni")
                ?: uri.getQueryParameter("serverName")
            val fingerprint = uri.getQueryParameter("fp")
                ?: uri.getQueryParameter("fingerprint")
            val spiderX = uri.getQueryParameter("spx")
                ?: uri.getQueryParameter("spiderX")
            val hasSpiderX = uri.queryParameterNames.contains("spx") || uri.queryParameterNames.contains("spiderX")

            val warnings = mutableListOf<String>()
            if (security == "reality") {
                if (publicKey.isNullOrBlank()) warnings += "VLESS REALITY: отсутствует publicKey/pbk"
                if (serverName.isNullOrBlank()) warnings += "VLESS REALITY: отсутствует serverName/sni"
                if (fingerprint.isNullOrBlank()) warnings += "VLESS REALITY: отсутствует fingerprint/fp"
                if (shortId.isNullOrBlank()) warnings += "VLESS REALITY: отсутствует shortId/sid"
                if (!hasSpiderX) warnings += "VLESS REALITY: отсутствует spiderX/spx"
                if (flow.isNullOrBlank()) warnings += "VLESS REALITY: отсутствует flow (например xtls-rprx-vision)"
            }

            val normalizedJson = buildVlessConfig(
                address = address,
                port = port,
                uuid = uuid,
                flow = flow,
                transport = transport,
                security = security,
                publicKey = publicKey,
                shortId = shortId,
                serverName = serverName,
                fingerprint = fingerprint,
                spiderX = spiderX
            )

            ImportedProfileDraft(
                displayName = displayName,
                type = if (security == "reality") {
                    ProfileType.XRAY_VLESS_REALITY
                } else {
                    ProfileType.VLESS
                },
                sourceRaw = input,
                normalizedJson = normalizedJson,
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = warnings.isNotEmpty(),
                importWarnings = warnings
            )
        }.getOrElse { error ->
            ImportedProfileDraft(
                displayName = "VLESS",
                type = ProfileType.XRAY_VLESS_REALITY,
                sourceRaw = input,
                normalizedJson = null,
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = true,
                importWarnings = listOf(
                    "Не удалось полностью разобрать VLESS профиль: ${error.message ?: "неизвестная ошибка"}"
                )
            )
        }
    }

    private fun parseUriProfile(input: String, type: ProfileType): ImportedProfileDraft {
        return runCatching {
            val uri = Uri.parse(input)
            val name = uri.fragment?.takeIf { it.isNotBlank() }
                ?: uri.host?.takeIf { it.isNotBlank() }
                ?: type.name

            ImportedProfileDraft(
                displayName = name,
                type = type,
                sourceRaw = input,
                normalizedJson = null,
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = false,
                importWarnings = emptyList()
            )
        }.getOrElse {
            ImportedProfileDraft(
                displayName = type.name,
                type = type,
                sourceRaw = input,
                normalizedJson = null,
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = true,
                importWarnings = listOf("Не удалось полноценно разобрать URI. Сохранён частичный импорт.")
            )
        }
    }

    private fun parseVmess(input: String): ImportedProfileDraft {
        val encodedPart = input.removePrefix(VMESS_PREFIX).trim()
        require(encodedPart.isNotBlank()) { "VMess ссылка не содержит полезной нагрузки." }

        return runCatching {
            val decodedJson = decodeBase64(encodedPart)
            val json = JSONObject(decodedJson)

            val displayName = json.optString("ps").takeIf { it.isNotBlank() }
                ?: json.optString("add").takeIf { it.isNotBlank() }
                ?: "VMESS"

            ImportedProfileDraft(
                displayName = displayName,
                type = ProfileType.VMESS,
                sourceRaw = input,
                normalizedJson = json.toString(2),
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = false,
                importWarnings = emptyList()
            )
        }.getOrElse {
            ImportedProfileDraft(
                displayName = "VMESS",
                type = ProfileType.VMESS,
                sourceRaw = input,
                normalizedJson = null,
                dnsServers = DefaultDnsProvider.defaultServers,
                dnsFallbackApplied = true,
                isPartialImport = true,
                importWarnings = listOf("Не удалось декодировать VMESS JSON. Сохранён частичный импорт.")
            )
        }
    }

    private fun parseXrayJson(input: String): ImportedProfileDraft {
        val json = JSONObject(input)
        val warnings = mutableListOf<String>()
        var dnsFallbackApplied = false

        if (!json.has("outbounds")) {
            warnings += "В JSON не найдена секция outbounds."
        }
        if (!json.has("inbounds")) {
            warnings += "В JSON не найдена секция inbounds."
        } else {
            val legacyInboundWarnings = describeLegacyInbounds(json)
            warnings += legacyInboundWarnings
        }

        val dnsServers = extractDnsServers(json).ifEmpty {
            dnsFallbackApplied = true
            warnings += "DNS в конфиге отсутствует: подставлены DNS по умолчанию приложения."
            val defaultDns = JSONArray(DefaultDnsProvider.defaultServers)
            json.put("dns", JSONObject().put("servers", defaultDns))
            DefaultDnsProvider.defaultServers
        }

        val displayName = json.optString("remarks").takeIf { it.isNotBlank() }
            ?: json.optString("tag").takeIf { it.isNotBlank() }
            ?: "Xray JSON"

        return ImportedProfileDraft(
            displayName = displayName,
            type = detectJsonProfileType(json),
            sourceRaw = input,
            normalizedJson = json.toString(2),
            dnsServers = dnsServers,
            dnsFallbackApplied = dnsFallbackApplied,
            isPartialImport = warnings.isNotEmpty(),
            importWarnings = warnings
        )
    }

    private fun parseAmneziaWgConf(input: String): ImportedProfileDraft {
        val parsed = amneziaWgConfigParser.parse(input)
        val warnings = parsed.importWarnings.toMutableList()
        val dnsServers = parsed.dnsServers.ifEmpty {
            warnings += "DNS в AWG конфиге отсутствует: подставлены DNS по умолчанию приложения."
            DefaultDnsProvider.defaultServers
        }

        return ImportedProfileDraft(
            displayName = parsed.displayName,
            type = ProfileType.AMNEZIA_WG_20,
            sourceRaw = input,
            normalizedJson = parsed.normalizedConfig,
            dnsServers = dnsServers,
            dnsFallbackApplied = parsed.dnsServers.isEmpty(),
            isPartialImport = warnings.isNotEmpty(),
            importWarnings = warnings
        )
    }

    private fun buildVlessConfig(
        address: String,
        port: Int,
        uuid: String,
        flow: String?,
        transport: String,
        security: String,
        publicKey: String?,
        shortId: String?,
        serverName: String?,
        fingerprint: String?,
        spiderX: String?
    ): String {
        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", "none")
        if (!flow.isNullOrBlank()) {
            user.put("flow", flow)
        }

        val vnext = JSONObject()
            .put("address", address)
            .put("port", port)
            .put("users", JSONArray().put(user))

        val streamSettings = JSONObject()
            .put("network", transport)
            .put("security", security)

        if (security == "reality") {
            val realitySettings = JSONObject()
            if (!publicKey.isNullOrBlank()) {
                realitySettings.put("publicKey", publicKey)
                realitySettings.put("password", publicKey)
            }
            if (!shortId.isNullOrBlank()) realitySettings.put("shortId", shortId)
            if (!serverName.isNullOrBlank()) realitySettings.put("serverName", serverName)
            if (!fingerprint.isNullOrBlank()) realitySettings.put("fingerprint", fingerprint)
            if (spiderX != null) realitySettings.put("spiderX", spiderX)
            streamSettings.put("realitySettings", realitySettings)
        }

        val outboundProxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings)

        val outboundDirect = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject())

        val outboundBlock = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject())

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray())
            .put("outbounds", JSONArray().put(outboundProxy).put(outboundDirect).put(outboundBlock))
            .put("routing", JSONObject().put("domainStrategy", "AsIs"))
            .toString(2)
    }

    private fun extractDnsServers(json: JSONObject): List<String> {
        val dnsObject = json.optJSONObject("dns") ?: return emptyList()
        val servers = dnsObject.optJSONArray("servers") ?: return emptyList()

        return buildList {
            for (index in 0 until servers.length()) {
                when (val value = servers.get(index)) {
                    is String -> add(value)
                    is JSONObject -> {
                        val address = value.optString("address")
                        if (address.isNotBlank()) add(address)
                    }
                }
            }
        }
    }

    private fun describeLegacyInbounds(json: JSONObject): List<String> {
        val inbounds = json.optJSONArray("inbounds") ?: return emptyList()
        if (inbounds.length() == 0) return emptyList()

        val descriptions = mutableListOf<String>()
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            val protocol = inbound.optString("protocol").ifBlank { "unknown" }
            val listen = inbound.optString("listen").ifBlank { "0.0.0.0" }
            val port = inbound.opt("port")?.toString() ?: "?"
            descriptions += "$protocol@$listen:$port"
        }

        if (descriptions.isEmpty()) return emptyList()

        return listOf(
            "Обнаружены inbound секции (${descriptions.size}). " +
                "Для клиентского VPN приложение формирует собственные runtime inbounds; " +
                "legacy inbound из профиля не используется как внутренний data plane приватной сессии.",
            "Inbound из профиля: ${descriptions.joinToString(limit = 6, truncated = "...")}"
        )
    }

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
        return String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
    }

    private fun looksLikeJson(input: String): Boolean =
        input.startsWith("{") && input.endsWith("}")

    private fun looksLikeAwgConf(input: String): Boolean {
        val hasInterface = Regex("(?im)^\\s*\\[Interface\\]\\s*$").containsMatchIn(input)
        val hasPeer = Regex("(?im)^\\s*\\[Peer\\]\\s*$").containsMatchIn(input)
        return hasInterface && hasPeer
    }

    private fun normalize(input: String): String {
        return input.removePrefix("\uFEFF")
    }

    private fun detectJsonProfileType(json: JSONObject): ProfileType {
        val outbounds = json.optJSONArray("outbounds") ?: return ProfileType.XRAY_JSON
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val protocol = outbound.optString("protocol").trim().lowercase()
            if (protocol != "vless") continue
            val security = outbound
                .optJSONObject("streamSettings")
                ?.optString("security")
                ?.trim()
                ?.lowercase()
            if (security == "reality") {
                return ProfileType.XRAY_VLESS_REALITY
            }
        }
        return ProfileType.XRAY_JSON
    }

    private companion object {
        const val VLESS_PREFIX = "vless://"
        const val VMESS_PREFIX = "vmess://"
        const val TROJAN_PREFIX = "trojan://"
    }
}
