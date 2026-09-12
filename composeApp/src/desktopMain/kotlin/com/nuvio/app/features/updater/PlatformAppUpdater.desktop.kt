package com.nuvio.app.features.updater

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppVersionPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

actual object PlatformAppUpdater {
    private const val GITHUB_OWNER = "cashnts"
    private const val GITHUB_REPO = "KhaYinDesktop"
    private const val USER_AGENT = "KhaYin-Desktop-Updater"

    private val log = Logger.withTag("DesktopAppUpdater")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow(AppUpdateState())
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val releaseClient = GitHubReleaseClient(
        owner = GITHUB_OWNER,
        repo = GITHUB_REPO,
        userAgent = USER_AGENT,
    )

    private val downloadHttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = null
                socketTimeoutMillis = 120_000L
                connectTimeoutMillis = 30_000L
            }
        }
    }

    private var isInitialized = false

    private fun chooseBestDesktopAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        if (assets.isEmpty()) return null
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val isArm = arch.contains("aarch64") || arch.contains("arm")

        return when {
            os.contains("mac") -> {
                val dmgAssets = assets.filter { it.name.endsWith(".dmg", ignoreCase = true) }
                if (dmgAssets.isEmpty()) return null
                if (isArm) {
                    dmgAssets.firstOrNull { it.name.contains("arm", ignoreCase = true) || it.name.contains("aarch64", ignoreCase = true) }
                        ?: dmgAssets.firstOrNull { !it.name.contains("x64", ignoreCase = true) && !it.name.contains("x86", ignoreCase = true) }
                        ?: dmgAssets.first()
                } else {
                    dmgAssets.firstOrNull { it.name.contains("x64", ignoreCase = true) || it.name.contains("x86_64", ignoreCase = true) || it.name.contains("intel", ignoreCase = true) }
                        ?: dmgAssets.firstOrNull { !it.name.contains("arm", ignoreCase = true) && !it.name.contains("aarch64", ignoreCase = true) }
                        ?: dmgAssets.first()
                }
            }
            os.contains("win") -> {
                val winAssets = assets.filter { it.name.endsWith(".msi", ignoreCase = true) || it.name.endsWith(".exe", ignoreCase = true) }
                if (winAssets.isEmpty()) return null
                winAssets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) } ?: winAssets.first()
            }
            else -> {
                // Linux
                assets.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
                    ?: assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
                    ?: assets.firstOrNull { it.name.endsWith(".tar.gz", ignoreCase = true) }
                    ?: assets.first()
            }
        }
    }

    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        log.i { "initialize() — starting background update checker" }

        // Start background periodic check (initial check after 3s, then every 24h)
        scope.launch {
            delay(3.seconds)
            while (isActive) {
                try {
                    performCheck(manual = false)
                } catch (t: Throwable) {
                    log.w(t) { "Background update check failed: ${t.message}" }
                }
                delay(24.hours)
            }
        }
    }

    actual fun checkForUpdate(manual: Boolean) {
        scope.launch {
            performCheck(manual = manual)
        }
    }

    private suspend fun performCheck(manual: Boolean) {
        _state.update { it.copy(status = AppUpdateStatus.Checking) }
        val currentVersion = AppVersionPolicy.displayVersionName.ifBlank { "1.0.0" }
        log.i { "performCheck(manual=$manual) — currentVersion=$currentVersion" }

        try {
            val release = releaseClient.getLatestRelease(includePrereleases = false)
            if (release == null) {
                log.w { "No release found on GitHub" }
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.UpToDate,
                        showUpToDateFeedback = manual,
                        lastCheckedTimestamp = System.currentTimeMillis(),
                    )
                }
                return
            }

            val remoteTag = release.tagName ?: release.name ?: ""
            val isNewer = VersionComparator.isRemoteNewer(remoteTag, currentVersion)
            log.i { "performCheck — remoteTag=$remoteTag, currentVersion=$currentVersion, isNewer=$isNewer" }

            val chosenAsset = chooseBestDesktopAsset(release.assets)

            if (isNewer && chosenAsset != null) {
                val info = AppUpdateInfo(
                    versionName = VersionComparator.normalize(remoteTag),
                    releaseTitle = release.name ?: remoteTag,
                    changelog = release.body.orEmpty(),
                    assetName = chosenAsset.name,
                    downloadUrl = chosenAsset.browserDownloadUrl,
                    isPrerelease = release.prerelease,
                )
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.UpdateAvailable(info),
                        availableUpdate = info,
                        isDialogVisible = true,
                        lastCheckedTimestamp = System.currentTimeMillis(),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.UpToDate,
                        showUpToDateFeedback = manual,
                        lastCheckedTimestamp = System.currentTimeMillis(),
                    )
                }
            }
        } catch (t: Throwable) {
            log.e(t) { "Update check error: ${t.message}" }
            _state.update {
                it.copy(
                    status = AppUpdateStatus.Error(t.message ?: "Failed to check for updates"),
                    showUpToDateFeedback = manual,
                    lastCheckedTimestamp = System.currentTimeMillis(),
                )
            }
        }
    }

    actual fun downloadUpdate() {
        val update = _state.value.availableUpdate ?: return
        val downloadUrl = update.downloadUrl
        if (downloadUrl.isBlank()) return

        scope.launch {
            _state.update {
                it.copy(
                    status = AppUpdateStatus.Downloading(progress = 0f),
                    isDialogVisible = true,
                )
            }

            try {
                val downloadDir = File(System.getProperty("java.io.tmpdir"), "khayin-updates").apply { mkdirs() }
                val targetFile = File(downloadDir, update.assetName)
                if (targetFile.exists()) targetFile.delete()

                log.i { "downloadUpdate() — downloading $downloadUrl to ${targetFile.absolutePath}" }

                withContext(Dispatchers.IO) {
                    val response = downloadHttpClient.get(downloadUrl) {
                        header("User-Agent", USER_AGENT)
                        onDownload { bytesSentTotal, contentLength ->
                            val progress = if (contentLength != null && contentLength > 0) {
                                (bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            _state.update {
                                it.copy(
                                    status = AppUpdateStatus.Downloading(
                                        progress = progress,
                                        bytesDownloaded = bytesSentTotal,
                                        totalBytes = contentLength ?: 0L,
                                    ),
                                )
                            }
                        }
                    }

                    if (response.status != HttpStatusCode.OK) {
                        error("Download failed: HTTP ${response.status.value}")
                    }

                    val bytes: ByteArray = response.body()
                    targetFile.writeBytes(bytes)
                }

                log.i { "downloadUpdate() — download complete: ${targetFile.length()} bytes" }
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.ReadyToInstall(targetFile.absolutePath),
                        isDialogVisible = true,
                    )
                }
            } catch (t: Throwable) {
                log.e(t) { "downloadUpdate() failed: ${t.message}" }
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.Error(t.message ?: "Download failed"),
                        isDialogVisible = true,
                    )
                }
            }
        }
    }

    private fun getRunningMacosAppPath(): String? {
        val cmd = ProcessHandle.current().info().command().orElse("")
        val idx = cmd.lastIndexOf(".app")
        return if (idx != -1) {
            cmd.substring(0, idx + 4)
        } else {
            null
        }
    }

    private fun installMacosDmgUpdate(dmgFile: File) {
        val pid = ProcessHandle.current().pid()
        val runningApp = getRunningMacosAppPath() ?: "/Applications/KhaYin.app"
        val scriptFile = File.createTempFile("khayin_updater_", ".sh").apply {
            deleteOnExit()
        }

        val scriptContent = """
            #!/bin/bash
            # Wait for current process to exit
            while kill -0 $pid 2>/dev/null; do
                sleep 0.2
            done

            MOUNT_DIR=${'$'}(mktemp -d /tmp/khayin_mount_XXXXXX)
            hdiutil attach "${dmgFile.absolutePath}" -nobrowse -readonly -mountpoint "${'$'}MOUNT_DIR" -quiet

            APP_SOURCE=${'$'}(find "${'$'}MOUNT_DIR" -maxdepth 1 -name "*.app" | head -n 1)

            if [ -n "${'$'}APP_SOURCE" ] && [ -d "${'$'}APP_SOURCE" ]; then
                APP_NAME=${'$'}(basename "${'$'}APP_SOURCE")
                TARGET_APP="$runningApp"
                if [[ "${'$'}TARGET_APP" != *"${'$'}APP_NAME" || ! -d "${'$'}TARGET_APP" ]]; then
                    TARGET_APP="/Applications/${'$'}APP_NAME"
                fi

                # Strict security assertion: Target MUST strictly end with KhaYin.app, Nuvio.app, or KhaYin Admin.app
                if [[ "${'$'}TARGET_APP" == *"/KhaYin.app" || "${'$'}TARGET_APP" == *"/Nuvio.app" || "${'$'}TARGET_APP" == *"/KhaYin Admin.app" ]]; then
                    xattr -cr "${'$'}APP_SOURCE" 2>/dev/null || true
                    rm -rf "${'$'}TARGET_APP"
                    ditto "${'$'}APP_SOURCE" "${'$'}TARGET_APP"
                    xattr -cr "${'$'}TARGET_APP" 2>/dev/null || true
                    xattr -d com.apple.quarantine "${'$'}TARGET_APP" 2>/dev/null || true
                fi
            fi

            hdiutil detach "${'$'}MOUNT_DIR" -quiet 2>/dev/null || true
            rm -rf "${'$'}MOUNT_DIR"
            rm -f "${dmgFile.absolutePath}" 2>/dev/null || true
            rm -f "${scriptFile.absolutePath}" 2>/dev/null || true

            if [ -n "${'$'}TARGET_APP" ] && [ -d "${'$'}TARGET_APP" ]; then
                open "${'$'}TARGET_APP"
            fi
        """.trimIndent()

        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        try {
            com.nuvio.app.features.p2p.P2pStreamingEngine.shutdown()
            com.nuvio.app.features.discordrpc.DiscordPresenceManager.shutdown()
            com.nuvio.app.core.analytics.PostHogLogger.flush()
            com.nuvio.app.core.analytics.PostHogTracer.flush()
        } catch (_: Throwable) {}

        ProcessBuilder("/bin/bash", scriptFile.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        kotlin.system.exitProcess(0)
    }

    private fun installWindowsUpdate(installerFile: File) {
        val pid = ProcessHandle.current().pid()
        val scriptFile = File.createTempFile("khayin_updater_", ".bat").apply {
            deleteOnExit()
        }

        val isMsi = installerFile.name.endsWith(".msi", ignoreCase = true)
        val installCmd = if (isMsi) {
            "start \"\" msiexec /i \"${installerFile.absolutePath}\""
        } else {
            "start \"\" \"${installerFile.absolutePath}\""
        }

        val scriptContent = """
            @echo off
            :waitloop
            tasklist /fi "PID eq $pid" | findstr /i "$pid" >nul
            if not errorlevel 1 (
                timeout /t 1 /nobreak >nul
                goto waitloop
            )
            $installCmd
            del "%~f0" 2>nul
            exit
        """.trimIndent()

        scriptFile.writeText(scriptContent)

        try {
            com.nuvio.app.features.p2p.P2pStreamingEngine.shutdown()
            com.nuvio.app.features.discordrpc.DiscordPresenceManager.shutdown()
            com.nuvio.app.core.analytics.PostHogLogger.flush()
            com.nuvio.app.core.analytics.PostHogTracer.flush()
        } catch (_: Throwable) {}

        ProcessBuilder("cmd.exe", "/c", scriptFile.absolutePath).start()
        kotlin.system.exitProcess(0)
    }

    private fun installLinuxUpdate(file: File) {
        file.setExecutable(true)
        try {
            com.nuvio.app.features.p2p.P2pStreamingEngine.shutdown()
            com.nuvio.app.features.discordrpc.DiscordPresenceManager.shutdown()
        } catch (_: Throwable) {}

        ProcessBuilder(file.absolutePath).start()
        kotlin.system.exitProcess(0)
    }

    actual fun installUpdate() {
        val readyStatus = _state.value.status as? AppUpdateStatus.ReadyToInstall
        val downloadedFile = readyStatus?.filePath?.let { File(it) }?.takeIf { it.exists() }
        val os = System.getProperty("os.name").lowercase()

        try {
            when {
                os.contains("mac") && downloadedFile != null && downloadedFile.name.endsWith(".dmg", ignoreCase = true) -> {
                    installMacosDmgUpdate(downloadedFile)
                }
                os.contains("win") && downloadedFile != null -> {
                    installWindowsUpdate(downloadedFile)
                }
                downloadFileIsLinux(downloadedFile) -> {
                    installLinuxUpdate(downloadedFile!!)
                }
                else -> {
                    log.e { "Unsupported OS / file for installation: $os, ${downloadedFile?.name}" }
                }
            }
        } catch (t: Throwable) {
            log.e(t) { "Installation invocation failed: ${t.message}" }
            _state.update {
                it.copy(status = AppUpdateStatus.Error(t.message ?: "Installation failed"))
            }
        }
    }

    private fun downloadFileIsLinux(file: File?): Boolean {
        if (file == null) return false
        val os = System.getProperty("os.name").lowercase()
        return !os.contains("mac") && !os.contains("win")
    }

    actual fun showUpdateDialog() {
        _state.update { it.copy(isDialogVisible = true) }
    }

    actual fun dismissDialog() {
        _state.update { it.copy(isDialogVisible = false) }
    }

    actual fun dismissUpToDateFeedback() {
        _state.update { it.copy(showUpToDateFeedback = false) }
    }
}
