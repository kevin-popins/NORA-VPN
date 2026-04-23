package com.privatevpn.app.profiles.subscriptions

import com.privatevpn.app.profiles.importer.ProfileImportParser
import com.privatevpn.app.profiles.model.ImportedProfileDraft

data class SubscriptionParseResult(
    val validProfiles: List<ImportedProfileDraft>,
    val invalidEntriesCount: Int,
    val warnings: List<String>,
    val detectedFormat: SubscriptionPayloadFormat,
    val base64Detected: Boolean,
    val base64Decoded: Boolean,
    val rawLineCount: Int,
    val discoveredEntriesCount: Int,
    val skippedLinesCount: Int
)

class SubscriptionParser(
    private val profileImportParser: ProfileImportParser,
    private val payloadDecoder: SubscriptionPayloadDecoder = SubscriptionPayloadDecoder()
) {
    fun parse(rawPayload: String): SubscriptionParseResult {
        val decoded = payloadDecoder.decodeWithDiagnostics(rawPayload)
        val extracted = extractEntries(decoded.payload)
        val entries = extracted.entries

        val valid = mutableListOf<ImportedProfileDraft>()
        val warnings = mutableListOf<String>()
        var invalidCount = 0

        entries.forEachIndexed { index, entry ->
            runCatching {
                profileImportParser.parse(entry)
            }.onSuccess { draft ->
                valid += draft
                if (draft.isPartialImport) {
                    warnings += "Элемент #${index + 1} импортирован частично"
                }
            }.onFailure { error ->
                invalidCount += 1
                warnings += "Элемент #${index + 1} пропущен: ${error.message ?: "ошибка"}"
            }
        }

        if (entries.isEmpty()) {
            warnings += "В подписке не найдено строк с серверами."
        }
        if (valid.isEmpty() && entries.isNotEmpty()) {
            warnings += "В подписке не найдено валидных профилей."
        }

        return SubscriptionParseResult(
            validProfiles = valid,
            invalidEntriesCount = invalidCount,
            warnings = warnings,
            detectedFormat = decoded.format,
            base64Detected = decoded.base64Detected,
            base64Decoded = decoded.base64Decoded,
            rawLineCount = extracted.rawLineCount,
            discoveredEntriesCount = extracted.entries.size,
            skippedLinesCount = extracted.skippedLinesCount
        )
    }

    private fun extractEntries(decoded: String): ExtractedEntries {
        val trimmed = decoded.removePrefix(BOM).trim()
        if (trimmed.isBlank()) return ExtractedEntries(emptyList(), 0, 0)

        if (looksLikeSingleConfig(trimmed)) {
            return ExtractedEntries(
                entries = listOf(trimmed),
                rawLineCount = trimmed.lineSequence().count(),
                skippedLinesCount = 0
            )
        }

        val entries = mutableListOf<String>()
        var rawLineCount = 0
        var skippedLineCount = 0

        trimmed.lineSequence().forEach { rawLine ->
            rawLineCount += 1
            val line = rawLine.removePrefix(BOM).trim()
            if (line.isBlank()) {
                skippedLineCount += 1
                return@forEach
            }
            if (line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) {
                skippedLineCount += 1
                return@forEach
            }
            val normalizedLine = line
                .replace(INLINE_HASH_COMMENT_REGEX, "")
                .replace(INLINE_SEMICOLON_COMMENT_REGEX, "")
                .trim()
            if (normalizedLine.isBlank()) {
                skippedLineCount += 1
                return@forEach
            }
            entries += normalizedLine
        }

        return ExtractedEntries(
            entries = entries,
            rawLineCount = rawLineCount,
            skippedLinesCount = skippedLineCount
        )
    }

    private fun looksLikeSingleConfig(text: String): Boolean {
        if (text.startsWith("{") && text.endsWith("}")) return true
        if (text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)) {
            return true
        }
        val nonBlankLines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (nonBlankLines.size != 1) return false

        val line = nonBlankLines.first().lowercase()
        return line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("trojan://")
    }

    private data class ExtractedEntries(
        val entries: List<String>,
        val rawLineCount: Int,
        val skippedLinesCount: Int
    )

    private companion object {
        const val BOM: String = "\uFEFF"
        val INLINE_HASH_COMMENT_REGEX = Regex("\\s+#.*$")
        val INLINE_SEMICOLON_COMMENT_REGEX = Regex("\\s+;.*$")
    }
}
