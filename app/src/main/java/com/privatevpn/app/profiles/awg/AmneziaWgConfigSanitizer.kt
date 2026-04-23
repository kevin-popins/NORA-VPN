package com.privatevpn.app.profiles.awg

data class AmneziaWgSanitizationResult(
    val config: String,
    val notes: List<String>
)

object AmneziaWgConfigSanitizer {
    fun sanitize(rawConfig: String, randomizeHeaderRanges: Boolean): AmneziaWgSanitizationResult {
        val notes = mutableListOf<String>()
        if (randomizeHeaderRanges) {
            notes += "AWG: используется нативный формат полей H1-H4 без принудительной подмены диапазонов"
        }

        val normalized = rawConfig
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        var inInterfaceSection = false
        val sanitizedLines = normalized.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val section = trimmed.removePrefix("[").removeSuffix("]").trim()
                inInterfaceSection = section.equals("Interface", ignoreCase = true)
                return@mapNotNull line
            }

            if (!inInterfaceSection) {
                return@mapNotNull line
            }

            val emptySpecialJunk = EMPTY_SPECIAL_JUNK_REGEX.matchEntire(line)
            if (emptySpecialJunk != null) {
                val key = emptySpecialJunk.groupValues[1].uppercase()
                notes += "AWG: поле $key присутствует, но пустое; учитывается как допустимое пустое значение"
                return@mapNotNull null
            }

            line
        }.toList()

        return AmneziaWgSanitizationResult(
            config = sanitizedLines.joinToString("\n"),
            notes = notes
        )
    }

    private val EMPTY_SPECIAL_JUNK_REGEX =
        Regex("""^\s*(I[1-5])\s*=\s*(?:[#;].*)?$""", RegexOption.IGNORE_CASE)
}
