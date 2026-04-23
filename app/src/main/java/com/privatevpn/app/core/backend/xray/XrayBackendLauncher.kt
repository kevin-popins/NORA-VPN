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
                ProcessBuilder(command)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()
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
            lastError = IllegalStateException(
                "Xray завершился сразу после старта. Команда: ${command.joinToString(" ")}. " +
                    "Вывод: ${output.ifBlank { "пусто" }}"
            )
        }

        throw lastError ?: IllegalStateException("Не удалось запустить Xray процесс")
    }

    private fun readProcessOutput(process: Process): String {
        return runCatching {
            process.inputStream.bufferedReader().use { reader ->
                reader.readText().trim().take(MAX_LOG_CHARS)
            }
        }.getOrDefault("")
    }

    private companion object {
        private const val BACKEND_START_GRACE_MS: Long = 600
        private const val MAX_LOG_CHARS: Int = 3000
    }
}
