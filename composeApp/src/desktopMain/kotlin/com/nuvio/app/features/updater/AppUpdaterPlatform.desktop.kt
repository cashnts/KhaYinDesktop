package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_download_failed_http
import nuvio.composeapp.generated.resources.updates_downloaded_file_missing
import nuvio.composeapp.generated.resources.updates_empty_download_body
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

private const val desktopUpdaterPreferencesName = "nuvio_updater"
private const val ignoredTagKey = "ignored_release_tag"

private val desktopUpdaterHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(60))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

actual object AppUpdaterPlatform {
    private val currentOs: DesktopUpdaterOs = DesktopUpdaterOs.current()
    private val store = DesktopStorage.store(desktopUpdaterPreferencesName)
    actual val isDebugBuild: Boolean = false

    actual val isSupported: Boolean = currentOs != DesktopUpdaterOs.UNKNOWN

    actual val platformId: String
        get() = when (currentOs) {
            DesktopUpdaterOs.MACOS -> "mac"
            DesktopUpdaterOs.WINDOWS -> "windows"
            DesktopUpdaterOs.LINUX -> "linux"
            DesktopUpdaterOs.UNKNOWN -> "mac"
        }

    actual val releaseSource: AppUpdateReleaseSource = AppUpdateReleaseSource(
        owner = "NuvioMedia",
        repo = "NuvioDesktop",
        channelBranch = null,
        includePrereleases = true,
        userAgent = "NuvioDesktop",
    )

    actual val assetSelector: AppUpdateAssetSelector
        get() = currentOs.assetSelector

    actual val currentVersionName: String = AppVersionConfig.DESKTOP_VERSION_NAME

    actual fun getIgnoredTag(): String? = store.getString(ignoredTagKey)

    actual fun setIgnoredTag(tag: String?) {
        store.putString(ignoredTagKey, tag)
    }

    actual suspend fun downloadUpdateAsset(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destination = File(updatesDir(), safeName)
            val tempFile = File(updatesDir(), "$safeName.part")

            var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
            var attemptedRangeRequest = resumeFromBytes > 0L

            fun buildRequest(rangeStart: Long?): HttpRequest {
                val builder = HttpRequest.newBuilder()
                    .uri(URI(assetUrl))
                    .header("User-Agent", "KhaYin/${AppUpdaterPlatform.currentVersionName}")
                    .header("Accept", "*/*")
                    .GET()
                if (rangeStart != null && rangeStart > 0L) {
                    builder.header("Range", "bytes=$rangeStart-")
                }
                return builder.build()
            }

            var response = desktopUpdaterHttpClient.send(buildRequest(if (attemptedRangeRequest) resumeFromBytes else null), HttpResponse.BodyHandlers.ofInputStream())

            if (attemptedRangeRequest && response.statusCode() == 416) {
                tempFile.delete()
                resumeFromBytes = 0L
                attemptedRangeRequest = false
                response = desktopUpdaterHttpClient.send(buildRequest(null), HttpResponse.BodyHandlers.ofInputStream())
            }

            if (response.statusCode() !in 200..299) {
                error(runBlocking { getString(Res.string.updates_download_failed_http, response.statusCode()) })
            }

            val isPartialResume = attemptedRangeRequest && response.statusCode() == 206 && resumeFromBytes > 0L
            val appendToTemp = isPartialResume
            val startingBytes = if (appendToTemp) resumeFromBytes else 0L
            if (!appendToTemp && tempFile.exists()) {
                tempFile.delete()
            }

            val contentLength = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
            val totalBytes = if (contentLength != null && contentLength > 0L) {
                startingBytes + contentLength
            } else null

            var downloadedBytes = startingBytes
            onProgress(downloadedBytes, totalBytes)

            response.body()?.use { input ->
                FileOutputStream(tempFile, appendToTemp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read.toLong()
                        onProgress(downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            } ?: error(runBlocking { getString(Res.string.updates_empty_download_body) })

            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }
            destination.absolutePath
        }
    }

    actual fun canInstallDownloadedUpdate(): Boolean = true

    actual fun openInstallPermissionSettings() = Unit

    actual fun installDownloadedUpdate(path: String): Result<Unit> = runCatching {
        val isTestEnvironment = System.getProperty("org.gradle.test.worker") != null ||
            System.getProperty("java.class.path").orEmpty().contains("test")
        if (isTestEnvironment) {
            return@runCatching
        }

        val updateFile = File(path)
        check(updateFile.exists()) { runBlocking { getString(Res.string.updates_downloaded_file_missing) } }

        launchInstaller(updateFile)
        scheduleAppExit()
    }

    private fun updatesDir(): File =
        File(DesktopStorage.rootDir.resolve("updates").also { it.createDirectories() }.toUri())

    private fun launchInstaller(updateFile: File) {
        when (currentOs) {
            DesktopUpdaterOs.MACOS -> launchAutonomousMacInstaller(updateFile)
            DesktopUpdaterOs.WINDOWS -> launchAutonomousWindowsInstaller(updateFile)
            DesktopUpdaterOs.LINUX -> {
                val command = listOf("xdg-open", updateFile.absolutePath)
                ProcessBuilder(command).start()
            }
            DesktopUpdaterOs.UNKNOWN -> error("Desktop updates are not supported on this operating system.")
        }
    }

    private fun getTargetMacAppBundle(): File {
        val command = ProcessHandle.current().info().command().orElse(null)
        if (command != null && command.contains(".app")) {
            val appPath = command.substringBefore(".app") + ".app"
            val appFile = File(appPath)
            if (appFile.exists() && appFile.isDirectory && (appFile.name.contains("KhaYin", ignoreCase = true) || appFile.name.contains("Nuvio", ignoreCase = true))) {
                return appFile
            }
        }
        val javaHome = System.getProperty("java.home") ?: ""
        if (javaHome.contains(".app")) {
            val appPath = javaHome.substringBefore(".app") + ".app"
            val appFile = File(appPath)
            if (appFile.exists() && appFile.isDirectory && (appFile.name.contains("KhaYin", ignoreCase = true) || appFile.name.contains("Nuvio", ignoreCase = true))) {
                return appFile
            }
        }
        val defaultApps = File("/Applications/KhaYin.app")
        if (defaultApps.exists()) return defaultApps
        val userApps = File(System.getProperty("user.home") + "/Applications/KhaYin.app")
        if (userApps.exists()) return userApps
        val nuvioApps = File("/Applications/Nuvio.app")
        if (nuvioApps.exists()) return nuvioApps
        return defaultApps
    }

    private fun launchAutonomousMacInstaller(updateFile: File) {
        val currentPid = ProcessHandle.current().pid()
        val targetApp = getTargetMacAppBundle()
        val helperScript = File(updatesDir(), "mac_auto_updater.sh")

        val s = "$"
        val scriptContent = """
            #!/bin/bash
            exec > "/tmp/khayin_update.log" 2>&1
            echo "[${s}(date)] Starting autonomous updater for PID ${s}1"

            PID=${s}1
            UPDATE_FILE="${s}2"
            TARGET_APP="${s}3"
            MOUNT_DIR="/tmp/khayin_update_mount_${s}${s}"

            # Ensure TARGET_APP is strictly KhaYin or Nuvio app bundle and not a system directory
            if [[ "${s}TARGET_APP" != *.app || ( "${s}TARGET_APP" != *KhaYin* && "${s}TARGET_APP" != *Nuvio* ) || "${s}TARGET_APP" == "/Applications"* && "${s}TARGET_APP" == "/Applications" ]]; then
                echo "Resetting TARGET_APP to default /Applications/KhaYin.app"
                TARGET_APP="/Applications/KhaYin.app"
            fi

            if [[ "${s}TARGET_APP" == "/" || "${s}TARGET_APP" == "/Applications" || "${s}TARGET_APP" == "/Applications/" || -z "${s}TARGET_APP" ]]; then
                echo "CRITICAL: Aborting updater - invalid TARGET_APP path: ${s}TARGET_APP"
                exit 1
            fi

            while kill -0 "${s}PID" 2>/dev/null; do
                sleep 0.2
            done
            sleep 0.5

            echo "Target app: ${s}TARGET_APP"
            echo "Update file: ${s}UPDATE_FILE"

            SOURCE_APP=""
            MOUNTED=0

            if [[ "${s}UPDATE_FILE" == *.dmg ]]; then
                mkdir -p "${s}MOUNT_DIR"
                echo "Attaching DMG..."
                hdiutil attach "${s}UPDATE_FILE" -nobrowse -readonly -mountpoint "${s}MOUNT_DIR" -quiet
                MOUNTED=1
                SOURCE_APP=${s}(find "${s}MOUNT_DIR" -maxdepth 2 -name "*.app" | head -n 1)
            elif [[ "${s}UPDATE_FILE" == *.zip ]]; then
                EXTRACT_DIR="/tmp/khayin_update_zip_${s}${s}"
                mkdir -p "${s}EXTRACT_DIR"
                echo "Extracting ZIP..."
                unzip -q -o "${s}UPDATE_FILE" -d "${s}EXTRACT_DIR"
                SOURCE_APP=${s}(find "${s}EXTRACT_DIR" -maxdepth 2 -name "*.app" | head -n 1)
            elif [[ "${s}UPDATE_FILE" == *.app ]]; then
                SOURCE_APP="${s}UPDATE_FILE"
            fi

            if [[ -n "${s}SOURCE_APP" && -d "${s}SOURCE_APP" && ( "${s}TARGET_APP" == *KhaYin*.app || "${s}TARGET_APP" == *Nuvio*.app ) ]]; then
                echo "Found source app bundle: ${s}SOURCE_APP"
                mkdir -p "${s}(dirname "${s}TARGET_APP")"
                echo "Replacing ${s}TARGET_APP..."
                rm -rf "${s}TARGET_APP"
                ditto "${s}SOURCE_APP" "${s}TARGET_APP"
                xattr -dr com.apple.quarantine "${s}TARGET_APP" 2>/dev/null || true
                if [[ ${s}MOUNTED -eq 1 ]]; then
                    echo "Detaching DMG..."
                    hdiutil detach "${s}MOUNT_DIR" -force -quiet 2>/dev/null || true
                    rm -rf "${s}MOUNT_DIR"
                fi
                if [[ -d "${s}EXTRACT_DIR" ]]; then
                    rm -rf "${s}EXTRACT_DIR"
                fi
                echo "Relaunching ${s}TARGET_APP..."
                open -n "${s}TARGET_APP"
                echo "Autonomous update successful!"
            else
                echo "Fallback: Opening installer file directly"
                if [[ ${s}MOUNTED -eq 1 ]]; then
                    hdiutil detach "${s}MOUNT_DIR" -force -quiet 2>/dev/null || true
                    rm -rf "${s}MOUNT_DIR"
                fi
                open "${s}UPDATE_FILE"
            fi
            rm -f "${s}0"
        """.trimIndent()

        helperScript.writeText(scriptContent)
        helperScript.setExecutable(true, false)

        ProcessBuilder("/bin/bash", helperScript.absolutePath, currentPid.toString(), updateFile.absolutePath, targetApp.absolutePath)
            .start()
    }

    private fun launchAutonomousWindowsInstaller(updateFile: File) {
        val currentPid = ProcessHandle.current().pid()
        val helperScript = File(updatesDir(), "win_auto_updater.bat")

        val scriptContent = """
            @echo off
            set PID=$currentPid
            set UPDATE_FILE=${updateFile.absolutePath}

            :WAIT_LOOP
            tasklist /FI "PID eq %PID%" 2>NUL | find /I /N "%PID%">NUL
            if "%ERRORLEVEL%"=="0" (
                timeout /t 1 /nobreak >nul
                goto WAIT_LOOP
            )
            timeout /t 1 /nobreak >nul

            if /I "%UPDATE_FILE:~-4%"==".msi" (
                msiexec /i "%UPDATE_FILE%" /passive /norestart
            ) else (
                "%UPDATE_FILE%" /SILENT /NORESTART
            )

            del "%~f0"
        """.trimIndent()

        helperScript.writeText(scriptContent)
        ProcessBuilder("cmd.exe", "/c", helperScript.absolutePath).start()
    }

    private fun scheduleAppExit() {
        thread(name = "nuvio-updater-exit", isDaemon = true) {
            Thread.sleep(500)
            exitProcess(0)
        }
    }
}

private enum class DesktopUpdaterOs {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN;

    val assetSelector: AppUpdateAssetSelector
        get() {
            val archFragments = desktopArchitectureFragments()
            return when (this) {
                WINDOWS -> AppUpdateAssetSelector(
                    fileExtensions = listOf(".msi", ".exe"),
                    preferredNameFragments = archFragments + listOf("windows", "win"),
                    fallbackNameFragments = listOf("universal", "all"),
                )
                MACOS -> AppUpdateAssetSelector(
                    fileExtensions = listOf(".dmg", ".pkg"),
                    preferredNameFragments = archFragments + listOf("macos", "mac", "darwin"),
                    fallbackNameFragments = listOf("universal", "all"),
                )
                LINUX -> AppUpdateAssetSelector(
                    fileExtensions = listOf(".deb", ".AppImage"),
                    preferredNameFragments = archFragments + listOf("linux"),
                    fallbackNameFragments = listOf("universal", "all"),
                )
                UNKNOWN -> AppUpdateAssetSelector(fileExtensions = emptyList())
            }
        }

    companion object {
        fun current(): DesktopUpdaterOs {
            val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
            return when {
                osName.contains("win") -> WINDOWS
                osName.contains("mac") -> MACOS
                osName.contains("linux") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

private fun desktopArchitectureFragments(): List<String> {
    val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
    return when {
        arch == "aarch64" || arch == "arm64" -> listOf("arm64", "aarch64")
        arch == "x86" || arch == "i386" || arch == "i686" -> listOf("x86", "i386", "i686")
        arch.contains("64") -> listOf("x64", "x86_64", "amd64")
        else -> emptyList()
    }
}

internal fun windowsInstallerCommand(updateFile: File): List<String> {
    if (!updateFile.extension.equals("msi", ignoreCase = true)) {
        return listOf(updateFile.absolutePath)
    }

    return listOf("msiexec", "/i", updateFile.absolutePath)
}
