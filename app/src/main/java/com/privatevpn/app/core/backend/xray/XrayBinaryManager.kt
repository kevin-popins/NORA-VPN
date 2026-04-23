package com.privatevpn.app.core.backend.xray

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

class XrayBinaryManager(
    private val appContext: Context
) {

    data class PreparedBinary(
        val executable: File,
        val abi: String,
        val runtimeDir: File,
        val versionOutput: String,
        val sourceDescription: String,
        val geoIpFile: File?,
        val geoSiteFile: File?
    )

    fun prepareBinary(): PreparedBinary {
        val resolvedBinary = resolveBinaryFromNativeLibraryDir()
        val runtimeDir = prepareRuntimeDirectory()
        val versionOutput = verifyBinaryLaunch(
            executable = resolvedBinary.executable,
            workingDirectory = runtimeDir
        )
        val sourceDescription = buildSourceDescription(
            abi = resolvedBinary.abi,
            executable = resolvedBinary.executable
        )

        return PreparedBinary(
            executable = resolvedBinary.executable,
            abi = resolvedBinary.abi,
            runtimeDir = runtimeDir,
            versionOutput = versionOutput,
            sourceDescription = sourceDescription,
            geoIpFile = runtimeDir.resolve("geoip.dat").takeIf { it.exists() },
            geoSiteFile = runtimeDir.resolve("geosite.dat").takeIf { it.exists() }
        )
    }

    private data class ResolvedNativeBinary(
        val executable: File,
        val abi: String
    )

    private fun resolveBinaryFromNativeLibraryDir(): ResolvedNativeBinary {
        val nativeLibraryDirPath = appContext.applicationInfo.nativeLibraryDir.orEmpty()
        require(nativeLibraryDirPath.isNotBlank()) {
            "nativeLibraryDir недоступен: Android не предоставил путь к встроенным native библиотекам"
        }

        val nativeDirectory = File(nativeLibraryDirPath)
        val xrayFile = File(nativeDirectory, XRAY_LIBRARY_FILENAME)

        if (!xrayFile.exists()) {
            throw IllegalStateException(buildMissingBinaryMessage(nativeDirectory))
        }

        return ResolvedNativeBinary(
            executable = xrayFile,
            abi = detectAbiLabel(nativeDirectory)
        )
    }

    private fun prepareRuntimeDirectory(): File {
        val runtimeDir = File(appContext.noBackupFilesDir, "xray-runtime")
        if (!runtimeDir.exists()) {
            runtimeDir.mkdirs()
        }

        copyAssetToFile("xray/common/geoip.dat", File(runtimeDir, "geoip.dat"))
        copyAssetToFile("xray/common/geosite.dat", File(runtimeDir, "geosite.dat"))
        return runtimeDir
    }

    private fun copyAssetToFile(assetPath: String, outputFile: File) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { error ->
            if (error !is FileNotFoundException) {
                throw IllegalStateException("Ошибка копирования asset '$assetPath': ${error.message}", error)
            }
        }
    }

    private fun verifyBinaryLaunch(executable: File, workingDirectory: File): String {
        val process = runCatching {
            ProcessBuilder(
                executable.absolutePath,
                "version"
            )
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            throw IllegalStateException(
                "Xray binary не запускается из nativeLibraryDir: ${error.message ?: "неизвестная ошибка"}",
                error
            )
        }

        val finished = process.waitFor(VERIFY_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Проверка Xray binary превысила таймаут")
        }

        val output = runCatching {
            process.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrDefault("")

        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "Xray binary завершился с кодом ${process.exitValue()} при проверке version. " +
                    "Вывод: ${output.ifBlank { "пусто" }}"
            )
        }

        if (output.isBlank()) {
            throw IllegalStateException("Xray binary не вернул вывод при команде version")
        }

        return output
    }

    private fun detectAbiLabel(nativeDirectory: File): String {
        val path = nativeDirectory.absolutePath.lowercase()
        return when {
            "arm64" in path -> "arm64-v8a"
            "x86_64" in path -> "x86_64"
            "armeabi-v7a" in path -> "armeabi-v7a"
            else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        }
    }

    private fun buildMissingBinaryMessage(nativeDirectory: File): String {
        val listedNativeLibs = nativeDirectory.list()?.toList().orEmpty()
        val armv7Requested = Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" || it == "arm32-v7a" }
        val armv7Hint = if (armv7Requested) {
            " Для armv7 нужно добавить совместимый xray как jniLibs/armeabi-v7a/$XRAY_LIBRARY_FILENAME."
        } else {
            ""
        }

        return "Не найден встроенный Xray binary для ABI устройства (${Build.SUPPORTED_ABIS.joinToString()}). " +
            "Ожидаемый путь: ${File(nativeDirectory, XRAY_LIBRARY_FILENAME).absolutePath}. " +
            "В nativeLibraryDir найдены: ${listedNativeLibs.joinToString().ifBlank { "пусто" }}.$armv7Hint"
    }

    private fun buildSourceDescription(abi: String, executable: File): String {
        val runtimeMarker = runCatching {
            appContext.assets.open("xray/VERSION.txt").bufferedReader().use { it.readText().trim() }
        }.getOrDefault("Xray source release: неизвестно")

        return buildString {
            append("Artifact: APK jniLibs/$abi/$XRAY_LIBRARY_FILENAME")
            append(", путь запуска: ${executable.absolutePath}")
            append(", маркер релиза: $runtimeMarker")
        }
    }

    private companion object {
        const val XRAY_LIBRARY_FILENAME = "libxray.so"
        const val VERIFY_TIMEOUT_SEC = 4L
    }
}
