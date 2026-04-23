package com.privatevpn.app.core.backend.xray

import com.privatevpn.app.core.backend.runtime.RuntimeConfigPreparer
import com.privatevpn.app.core.backend.runtime.RuntimePreparationInput
import com.privatevpn.app.core.backend.runtime.RuntimePreparationResult
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class XrayRuntimeConfigPreparer : RuntimeConfigPreparer {
    override fun prepare(input: RuntimePreparationInput): RuntimePreparationResult {
        val root = runCatching { JSONObject(input.normalizedConfig.normalizedPayload) }
            .getOrElse { throw IllegalArgumentException("Нормализованный конфиг не является валидным JSON", it) }

        val primaryOutboundTag = ensurePrimaryOutboundTag(root)

        val notes = mutableListOf<String>()
        notes += "Runtime-конфиг подготовлен для профиля '${input.normalizedConfig.profileName}'"
        notes += "Приватная сессия: ${if (input.privateSessionEnabled) "включена" else "выключена"}"
        notes += input.normalizedConfig.warnings

        root.put("dns", buildDnsSection(input.dnsServers))
        sanitizeLegacyInbounds(
            root = root,
            notes = notes
        )

        applyDataPlaneSocks(
            root = root,
            input = input,
            notes = notes,
            outboundTag = primaryOutboundTag
        )

        applyLocalhostSocks(
            root = root,
            input = input,
            notes = notes
        )

        normalizeRealityKeyAliases(root = root, notes = notes)
        validateAndDescribeOutbound(
            root = root,
            profileType = input.normalizedConfig.profileType,
            notes = notes
        )
        ensureLogSection(root)

        val runtimeConfig = root.toString(2)
        notes += "Финальный runtime config сформирован: size=${runtimeConfig.length}, sha256=${sha256(runtimeConfig)}"

        return RuntimePreparationResult(
            runtimeConfig = runtimeConfig,
            notes = notes,
            dataPlaneProxy = input.dataPlaneProxy
        )
    }

    private fun ensurePrimaryOutboundTag(root: JSONObject): String {
        val outbounds = root.optJSONArray("outbounds")
            ?: throw IllegalArgumentException("В runtime конфиге отсутствует секция outbounds")
        require(outbounds.length() > 0) { "В runtime конфиге отсутствуют outbounds для выхода в интернет" }

        val primaryOutbound = outbounds.optJSONObject(0)
            ?: throw IllegalArgumentException("Первый outbound должен быть объектом JSON")
        val existingTag = primaryOutbound.optString("tag").takeIf { it.isNotBlank() }
        if (existingTag != null) {
            return existingTag
        }

        primaryOutbound.put("tag", DEFAULT_PRIMARY_OUTBOUND_TAG)
        return DEFAULT_PRIMARY_OUTBOUND_TAG
    }

    private fun buildDnsSection(dnsServers: List<String>): JSONObject {
        val servers = dnsServers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("1.1.1.1", "9.9.9.9") }

        return JSONObject().put("servers", JSONArray(servers))
    }

    private fun applyDataPlaneSocks(
        root: JSONObject,
        input: RuntimePreparationInput,
        notes: MutableList<String>,
        outboundTag: String
    ) {
        val dataPlane = input.dataPlaneProxy
        require(dataPlane.port in 1..65535) { "Некорректный порт внутреннего SOCKS data plane" }
        require(dataPlane.username.isNotBlank() && dataPlane.password.isNotBlank()) {
            "Некорректные учётные данные внутреннего SOCKS data plane"
        }

        val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also { root.put("inbounds", it) }
        val filteredInbounds = JSONArray()
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            val tag = inbound.optString("tag")
            if (tag != PRIVATEVPN_DATAPLANE_SOCKS_TAG) {
                filteredInbounds.put(inbound)
            }
        }

        val dataPlaneInbound = JSONObject()
            .put("tag", PRIVATEVPN_DATAPLANE_SOCKS_TAG)
            .put("listen", dataPlane.host)
            .put("port", dataPlane.port)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "password")
                    .put(
                        "accounts",
                        JSONArray().put(
                            JSONObject()
                                .put("user", dataPlane.username)
                                .put("pass", dataPlane.password)
                        )
                    )
                    .put("udp", true)
            )
        filteredInbounds.put(dataPlaneInbound)
        root.put("inbounds", filteredInbounds)

        val routing = root.optJSONObject("routing") ?: JSONObject().also { root.put("routing", it) }
        val routingRules = routing.optJSONArray("rules") ?: JSONArray()
        val filteredRules = JSONArray()
        for (index in 0 until routingRules.length()) {
            val rule = routingRules.optJSONObject(index) ?: continue
            val inboundTagArray = rule.optJSONArray("inboundTag")
            val isDataPlaneRule = inboundTagArray?.let { tags ->
                (0 until tags.length()).any { tagIndex ->
                    tags.optString(tagIndex) == PRIVATEVPN_DATAPLANE_SOCKS_TAG
                }
            } ?: false
            if (!isDataPlaneRule) {
                filteredRules.put(rule)
            }
        }

        val dataPlaneRule = JSONObject()
            .put("type", "field")
            .put("inboundTag", JSONArray().put(PRIVATEVPN_DATAPLANE_SOCKS_TAG))
            .put("outboundTag", outboundTag)

        val rebuiltRules = JSONArray().put(dataPlaneRule)
        for (index in 0 until filteredRules.length()) {
            rebuiltRules.put(filteredRules.get(index))
        }
        routing.put("rules", rebuiltRules)

        notes += "Data plane SOCKS добавлен: ${dataPlane.host}:${dataPlane.port}, auth=password, mode=internal-only"
    }

    private fun applyLocalhostSocks(
        root: JSONObject,
        input: RuntimePreparationInput,
        notes: MutableList<String>
    ) {
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also { root.put("inbounds", it) }
        val filtered = JSONArray()
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            if (inbound.optString("tag") != PRIVATEVPN_SOCKS_TAG) {
                filtered.put(inbound)
            }
        }
        root.put("inbounds", filtered)

        val socks = input.socksSettings
        if (input.privateSessionEnabled) {
            notes += "localhost SOCKS отключён в режиме приватной сессии для минимизации локальных каналов обхода"
            return
        }

        if (!socks.enabled) {
            notes += "localhost SOCKS выключен в настройках"
            return
        }

        if (socks.port == input.dataPlaneProxy.port) {
            throw IllegalArgumentException(
                "Порт пользовательского localhost SOCKS конфликтует с внутренним data plane портом"
            )
        }

        require(socks.port in 1..65535) { "Порт localhost SOCKS должен быть в диапазоне 1..65535" }
        require(socks.login.isNotBlank() && socks.password.isNotBlank()) {
            "Для localhost SOCKS обязательны логин и пароль"
        }

        val socksInbound = JSONObject()
            .put("tag", PRIVATEVPN_SOCKS_TAG)
            .put("listen", "127.0.0.1")
            .put("port", socks.port)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "password")
                    .put(
                        "accounts",
                        JSONArray().put(
                            JSONObject()
                                .put("user", socks.login)
                                .put("pass", socks.password)
                        )
                    )
                    .put("udp", true)
            )

        filtered.put(socksInbound)
        notes += "localhost SOCKS добавлен в runtime: 127.0.0.1:${socks.port}, auth=password"
    }

    private fun sanitizeLegacyInbounds(
        root: JSONObject,
        notes: MutableList<String>
    ) {
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
        if (inbounds.length() == 0) {
            root.put("inbounds", JSONArray())
            notes += "Legacy inbounds не обнаружены"
            return
        }

        val sanitized = JSONArray()
        val removedDescriptors = mutableListOf<String>()

        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            val tag = inbound.optString("tag")
            val protocol = inbound.optString("protocol").ifBlank { "unknown" }
            val listen = inbound.optString("listen").ifBlank { "0.0.0.0" }
            val port = inbound.opt("port")?.toString() ?: "?"

            val keep = tag == PRIVATEVPN_DATAPLANE_SOCKS_TAG || tag == PRIVATEVPN_SOCKS_TAG
            if (keep) {
                sanitized.put(inbound)
            } else {
                removedDescriptors += "$protocol@$listen:$port tag=${tag.ifBlank { "<none>" }}"
            }
        }

        root.put("inbounds", sanitized)

        if (removedDescriptors.isEmpty()) {
            notes += "Legacy inbounds не обнаружены"
            return
        }

        notes += "Legacy inbounds удалены из импортированного профиля: ${removedDescriptors.size}"
        removedDescriptors.take(MAX_REMOVED_INBOUND_LOGS).forEach { descriptor ->
            notes += "Удалён inbound: $descriptor"
        }
        if (removedDescriptors.size > MAX_REMOVED_INBOUND_LOGS) {
            notes += "Удалено ещё ${removedDescriptors.size - MAX_REMOVED_INBOUND_LOGS} inbound(ов)"
        }
    }

    private fun ensureLogSection(root: JSONObject) {
        val log = root.optJSONObject("log") ?: JSONObject().also { root.put("log", it) }
        if (!log.has("loglevel")) {
            log.put("loglevel", "warning")
        }
    }

    private fun normalizeRealityKeyAliases(
        root: JSONObject,
        notes: MutableList<String>
    ) {
        val outbounds = root.optJSONArray("outbounds") ?: return
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val streamSettings = outbound.optJSONObject("streamSettings") ?: continue
            val security = streamSettings.optString("security").lowercase()
            if (security != "reality") continue

            val realitySettings = streamSettings.optJSONObject("realitySettings")
                ?: throw IllegalArgumentException("security=reality указан, но realitySettings отсутствует")
            val publicKey = realitySettings.optString("publicKey").trim()
            val password = realitySettings.optString("password").trim()

            when {
                publicKey.isNotBlank() && password.isBlank() -> {
                    realitySettings.put("password", publicKey)
                    notes += "REALITY: добавлен alias realitySettings.password из publicKey (совместимость модели Xray)"
                }

                password.isNotBlank() && publicKey.isBlank() -> {
                    realitySettings.put("publicKey", password)
                    notes += "REALITY: добавлен alias realitySettings.publicKey из password (совместимость ссылок VLESS)"
                }
            }
        }
    }

    private fun validateAndDescribeOutbound(
        root: JSONObject,
        profileType: String,
        notes: MutableList<String>
    ) {
        val outbounds = root.optJSONArray("outbounds")
            ?: throw IllegalArgumentException("В runtime конфиге отсутствует секция outbounds")
        require(outbounds.length() > 0) { "В runtime конфиге нет ни одного outbound" }

        val outbound = findValidationOutbound(outbounds)
            ?: throw IllegalArgumentException("Не найден outbound для self-check")

        val protocol = outbound.optString("protocol").trim()
        val streamSettings = outbound.optJSONObject("streamSettings")
        val network = streamSettings?.optString("network")?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: "raw"
        val security = streamSettings?.optString("security")?.trim()?.lowercase().orEmpty()
        val realitySettings = streamSettings?.optJSONObject("realitySettings")
        val flow = outbound.extractFlow()

        notes += "Self-check runtime: protocol=${protocol.ifBlank { "не задан" }}, " +
            "network=${network.ifBlank { "не задан" }}, security=${security.ifBlank { "не задан" }}, " +
            "flow=${flow ?: "не задан"}, realitySettings=${if (realitySettings != null) "есть" else "нет"}"

        if (realitySettings != null) {
            val presence = listOf(
                "publicKey" to realitySettings.optString("publicKey").isNotBlank(),
                "password" to realitySettings.optString("password").isNotBlank(),
                "serverName" to realitySettings.optString("serverName").isNotBlank(),
                "fingerprint" to realitySettings.optString("fingerprint").isNotBlank(),
                "shortId" to realitySettings.optString("shortId").isNotBlank(),
                "spiderX" to realitySettings.has("spiderX")
            )
            notes += "Self-check REALITY ключи: " + presence.joinToString { "${it.first}=${it.second}" }

            val spiderXState = describeJsonStringField(realitySettings, "spiderX")
            notes += "Self-check REALITY spiderX: состояние=${spiderXState.state}, " +
                "ключ=${if (spiderXState.present) "есть" else "нет"}, " +
                "значение='${spiderXState.valueForLog}'"
        }

        val isVless = protocol.equals("vless", ignoreCase = true)
        val expectsVless = profileType.equals("VLESS", ignoreCase = true) ||
            profileType.equals("XRAY_VLESS_REALITY", ignoreCase = true)
        if (expectsVless && !isVless) {
            throw IllegalArgumentException(
                "Self-check runtime: профиль VLESS, но outbound protocol='${protocol.ifBlank { "не задан" }}'"
            )
        }

        if (!isVless) {
            return
        }

        if (security == "reality") {
            val missing = mutableListOf<String>()
            if (network.lowercase() !in setOf("tcp", "raw")) {
                missing += "streamSettings.network=tcp/raw"
            }
            if (flow.isNullOrBlank()) {
                missing += "settings.vnext[0].users[0].flow"
            }
            if (realitySettings == null) {
                missing += "streamSettings.realitySettings"
            } else {
                if (realitySettings.optString("publicKey").isBlank() &&
                    realitySettings.optString("password").isBlank()
                ) {
                    missing += "realitySettings.publicKey/password"
                }
                if (realitySettings.optString("serverName").isBlank()) {
                    missing += "realitySettings.serverName"
                }
                if (realitySettings.optString("fingerprint").isBlank()) {
                    missing += "realitySettings.fingerprint"
                }
                if (realitySettings.optString("shortId").isBlank()) {
                    missing += "realitySettings.shortId"
                }
            }
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Self-check runtime: VLESS+REALITY конфиг неполный, отсутствуют: ${missing.joinToString()}"
                )
            }
        }
    }

    private fun findValidationOutbound(outbounds: JSONArray): JSONObject? {
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("protocol").equals("vless", ignoreCase = true)) {
                return outbound
            }
        }
        return outbounds.optJSONObject(0)
    }

    private fun JSONObject.extractFlow(): String? {
        val settings = optJSONObject("settings") ?: return null
        val vnext = settings.optJSONArray("vnext") ?: return null
        if (vnext.length() == 0) return null
        val firstVnext = vnext.optJSONObject(0) ?: return null
        val users = firstVnext.optJSONArray("users") ?: return null
        if (users.length() == 0) return null
        val firstUser = users.optJSONObject(0) ?: return null
        return firstUser.optString("flow").trim().takeIf { it.isNotBlank() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class JsonStringFieldState(
        val present: Boolean,
        val state: String,
        val valueForLog: String
    )

    private fun describeJsonStringField(json: JSONObject, key: String): JsonStringFieldState {
        if (!json.has(key)) {
            return JsonStringFieldState(
                present = false,
                state = "missing",
                valueForLog = "<missing>"
            )
        }
        if (json.isNull(key)) {
            return JsonStringFieldState(
                present = true,
                state = "null",
                valueForLog = "<null>"
            )
        }

        val raw = json.opt(key)
        if (raw !is String) {
            return JsonStringFieldState(
                present = true,
                state = "non-string",
                valueForLog = java.lang.String.valueOf(raw).take(120)
            )
        }

        if (raw.isEmpty()) {
            return JsonStringFieldState(
                present = true,
                state = "empty-string",
                valueForLog = ""
            )
        }

        return JsonStringFieldState(
            present = true,
            state = "value",
            valueForLog = raw.take(120)
        )
    }

    private companion object {
        const val DEFAULT_PRIMARY_OUTBOUND_TAG = "privatevpn-primary-outbound"
        const val PRIVATEVPN_DATAPLANE_SOCKS_TAG = "privatevpn-dataplane-socks"
        const val PRIVATEVPN_SOCKS_TAG = "privatevpn-local-socks"
        const val MAX_REMOVED_INBOUND_LOGS = 8
    }
}
