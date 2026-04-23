package com.privatevpn.app.core.backend.xray

import java.io.File

class XrayBackendLauncher {

    data class LaunchResult(
        val process: Process,
        val command: List<String>
    )

    fun start(
        executable: File,
        configFile: File,
        workingDirectory: File
    ): LaunchResult {
        require(executable.exists()) { "Xray executable не найден: ${executable.absolutePath}" }
        require(configFile.exists()) { "Xray config не найден: ${configFile.absolutePath}" }

        val commands = listOf(
            listOf(executable.absolutePath, "run", "-c", configFile.absolutePath),
            listOf(executable.absolutePath, "-config", configFile.absolutePath),
            listOf(executable.absolutePath, "-c", configFile.absolutePath)
        )

        var lastError: Throwable? = null
        for (command in commands) {
            val attempt = runCatching {
                buildProcessBuilder(
                    command = command,
                    workingDirectory = workingDirectory
                ).start()
            }.onFailure { error ->
                lastError = error
            }.getOrNull() ?: continue

            Thread.sleep(BACKEND_START_GRACE_MS)
            if (attempt.isAlive) {
                return LaunchResult(
                    process = attempt,
                    command = command
                )
            }

            val output = readProcessOutput(attempt)
            val summarizedOutput = summarizeProcessOutput(output)
            lastError = IllegalStateException(
                "Xray завершился сразу после старта. Команда: ${command.joinToString(" ")}. " +
                    "Вывод: ${summarizedOutput.ifBlank { "пусто" }}"
            )
        }

        throw lastError ?: IllegalStateException("Не удалось запустить Xray процесс")
    }

    private fun buildProcessBuilder(
        command: List<String>,
        workingDirectory: File
    ): ProcessBuilder {
        val builder = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(true)

        // Xray looks for geosite.dat/geoip.dat in this location.
        builder.environment()["XRAY_LOCATION_ASSET"] = workingDirectory.absolutePath
        return builder
    }

    private fun readProcessOutput(process: Process): String {
        return runCatching {
            process.inputStream.bufferedReader().use { reader ->
                val text = reader.readText().trim()
                if (text.length <= MAX_CAPTURE_CHARS) {
                    text
                } else {
                    text.takeLast(MAX_CAPTURE_CHARS)
                }
            }
        }.getOrDefault("")
    }

    private fun summarizeProcessOutput(rawOutput: String): String {
        if (rawOutput.isBlank()) return ""

        val lines = rawOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return ""

        val tail = lines.takeLast(MAX_SUMMARY_LINES).joinToString(" || ")
        return if (tail.length <= MAX_LOG_CHARS) {
            tail
        } else {
            tail.takeLast(MAX_LOG_CHARS)
        }
    }

    private companion object {
        private const val BACKEND_START_GRACE_MS: Long = 600
        private const val MAX_CAPTURE_CHARS: Int = 12000
        private const val MAX_SUMMARY_LINES: Int = 14
        private const val MAX_LOG_CHARS: Int = 2200
    }
}
