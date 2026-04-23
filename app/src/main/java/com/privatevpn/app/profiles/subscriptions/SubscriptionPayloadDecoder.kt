package com.privatevpn.app.profiles.subscriptions

import java.nio.charset.StandardCharsets
import java.util.Base64

enum class SubscriptionPayloadFormat {
    PLAIN,
    BASE64,
    UNKNOWN
}

data class SubscriptionDecodedPayload(
    val payload: String,
    val format: SubscriptionPayloadFormat,
    val base64Detected: Boolean,
    val base64Decoded: Boolean,
    val hadBom: Boolean
)

class SubscriptionPayloadDecoder {
    fun decode(rawBody: String): String {
        return decodeWithDiagnostics(rawBody).payload
    }

    fun decodeWithDiagnostics(rawBody: String): SubscriptionDecodedPayload {
        val bomRemoved = rawBody.removePrefix(BOM)
        val hadBom = rawBody.length != bomRemoved.length
        val normalizedSource = bomRemoved.trim()
        if (normalizedSource.isBlank()) {
            return SubscriptionDecodedPayload(
                payload = normalizedSource,
                format = SubscriptionPayloadFormat.UNKNOWN,
                base64Detected = false,
                base64Decoded = false,
                hadBom = hadBom
            )
        }

        val shouldTryBase64 = shouldAttemptBase64(normalizedSource)
        val decodedCandidate = if (shouldTryBase64) decodeBase64OrNull(normalizedSource) else null
        val decodedNormalized = decodedCandidate?.removePrefix(BOM)?.trim()
        val decodedLooksUsable = decodedNormalized?.let { looksLikePayload(it) } == true

        if (decodedLooksUsable) {
            return SubscriptionDecodedPayload(
                payload = decodedNormalized.orEmpty(),
                format = SubscriptionPayloadFormat.BASE64,
                base64Detected = true,
                base64Decoded = true,
                hadBom = hadBom
            )
        }

        if (looksLikePayload(normalizedSource)) {
            return SubscriptionDecodedPayload(
                payload = normalizedSource,
                format = SubscriptionPayloadFormat.PLAIN,
                base64Detected = shouldTryBase64,
                base64Decoded = false,
                hadBom = hadBom
            )
        }

        if (!decodedNormalized.isNullOrBlank()) {
            return SubscriptionDecodedPayload(
                payload = decodedNormalized,
                format = SubscriptionPayloadFormat.BASE64,
                base64Detected = true,
                base64Decoded = true,
                hadBom = hadBom
            )
        }

        return SubscriptionDecodedPayload(
            payload = normalizedSource,
            format = SubscriptionPayloadFormat.UNKNOWN,
            base64Detected = shouldTryBase64,
            base64Decoded = false,
            hadBom = hadBom
        )
    }

    private fun looksLikePayload(text: String): Boolean {
        val normalized = text.removePrefix(BOM).trim()
        if (normalized.isBlank()) return false

        if (containsKnownScheme(normalized)) return true
        if (looksLikeSingleConfig(normalized)) return true
        if (text.contains('\n')) {
            return true
        }
        return normalized.contains("://")
    }

    private fun containsKnownScheme(text: String): Boolean {
        return text.lineSequence()
            .map { it.trim() }
            .any { line ->
                val lowered = line.lowercase()
                lowered.startsWith("vless://") ||
                    lowered.startsWith("vmess://") ||
                    lowered.startsWith("trojan://") ||
                    lowered.startsWith("ss://") ||
                    lowered.startsWith("socks://") ||
                    lowered.startsWith("hy2://") ||
                    lowered.startsWith("hysteria2://") ||
                    lowered.startsWith("tuic://") ||
                    lowered.startsWith("wireguard://")
            }
    }

    private fun looksLikeSingleConfig(text: String): Boolean {
        if (text.startsWith("{") && text.endsWith("}")) return true
        if (text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun shouldAttemptBase64(text: String): Boolean {
        val compact = text
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .replace(" ", "")
        if (compact.length < 24) return false

        return compact.all { ch ->
            ch in 'A'..'Z' ||
                ch in 'a'..'z' ||
                ch in '0'..'9' ||
                ch == '+' || ch == '/' || ch == '=' || ch == '-' || ch == '_'
        }
    }

    private fun decodeBase64OrNull(text: String): String? {
        val normalized = text
            .replace('-', '+')
            .replace('_', '/')
            .replace("\n", "")
            .replace("\r", "")
        val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
        return runCatching {
            val decodedBytes = runCatching { Base64.getDecoder().decode(padded) }
                .getOrElse { Base64.getMimeDecoder().decode(padded) }
            String(decodedBytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        const val BOM = "\uFEFF"
    }
}
