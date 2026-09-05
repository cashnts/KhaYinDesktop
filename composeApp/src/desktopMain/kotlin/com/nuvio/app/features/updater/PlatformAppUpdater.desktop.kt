package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionPolicy
import com.pavi2410.appupdater.AppUpdater
import com.pavi2410.appupdater.DesktopAssetDownloader
import com.pavi2410.appupdater.DesktopAssetInstaller
import com.pavi2410.appupdater.GitHubUpdateSource
import com.pavi2410.appupdater.UpdateState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import java.io.File
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

actual object PlatformAppUpdater {
    private const val GITHUB_OWNER = "cashnts"
    private const val GITHUB_REPO = "KhaYinDesktop"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow(AppUpdateState())
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var updater: AppUpdater? = null
    private var isInitialized = false

    private val updateHttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = null
                socketTimeoutMillis = 120_000L
                connectTimeoutMillis = 30_000L
            }
            defaultRequest {
                header("Accept-Encoding", "identity")
            }
        }
    }

    private val desktopAssetMatcher: (String) -> Boolean = { fileName ->
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> fileName.endsWith(".dmg", ignoreCase = true)
            os.contains("win") -> fileName.endsWith(".msi", ignoreCase = true) || fileName.endsWith(".exe", ignoreCase = true)
            else -> fileName.endsWith(".deb", ignoreCase = true) ||
                fileName.endsWith(".tar.gz", ignoreCase = true) ||
                fileName.endsWith(".AppImage", ignoreCase = true)
        }
    }

    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true

        val currentVersion = AppVersionPolicy.displayVersionName.ifBlank { "1.0.0" }
        val downloadDir = File(System.getProperty("java.io.tmpdir"), "kmp-app-updater").apply { mkdirs() }
        val appUpdater = AppUpdater(
            currentVersion = currentVersion,
            source = GitHubUpdateSource(
                owner = GITHUB_OWNER,
                repo = GITHUB_REPO,
                includePreReleases = false,
                httpClient = null,
            ),
            downloader = DesktopAssetDownloader(
                downloadDir = downloadDir,
                httpClient = updateHttpClient,
            ),
            installer = DesktopAssetInstaller(),
            assetMatcher = desktopAssetMatcher,
        )
        updater = appUpdater

        // Observe reactive updater state
        scope.launch {
            appUpdater.state.collect { updaterState ->
                when (updaterState) {
                    is UpdateState.Idle -> {
                        _state.update { it.copy(status = AppUpdateStatus.Idle) }
                    }
                    is UpdateState.Checking -> {
                        _state.update { it.copy(status = AppUpdateStatus.Checking) }
                    }
                    is UpdateState.UpdateAvailable -> {
                        val release = updaterState.release
                        val info = AppUpdateInfo(
                            versionName = release.version,
                            releaseTitle = "v${release.version}",
                            changelog = release.changelog,
                            assetName = updaterState.asset.name,
                            downloadUrl = updaterState.asset.downloadUrl,
                            isPrerelease = false,
                        )
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.UpdateAvailable(info),
                                availableUpdate = info,
                                isDialogVisible = true,
                                lastCheckedTimestamp = System.currentTimeMillis(),
                            )
                        }
                    }
                    is UpdateState.Downloading -> {
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.Downloading(
                                    progress = updaterState.progress,
                                    bytesDownloaded = updaterState.bytesDownloaded,
                                    totalBytes = updaterState.totalBytes,
                                ),
                                isDialogVisible = true,
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.ReadyToInstall(updaterState.filePath),
                                isDialogVisible = true,
                            )
                        }
                    }
                    is UpdateState.UpToDate -> {
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.UpToDate,
                                lastCheckedTimestamp = System.currentTimeMillis(),
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.Error(updaterState.message),
                                lastCheckedTimestamp = System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }
        }

        // Start background periodic check (initial check after 2s, then every 24h)
        scope.launch {
            delay(2.seconds)
            while (isActive) {
                try {
                    appUpdater.checkForUpdate()
                } catch (_: Throwable) {
                    // Ignore background check network failures
                }
                delay(24.hours)
            }
        }
    }

    actual fun checkForUpdate(manual: Boolean) {
        val u = updater ?: return
        scope.launch {
            _state.update { it.copy(status = AppUpdateStatus.Checking) }
            try {
                val release = u.checkForUpdate()
                if (release == null && manual) {
                    _state.update { it.copy(showUpToDateFeedback = true) }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.Error(t.message ?: "Failed to check for updates"),
                        showUpToDateFeedback = if (manual) true else it.showUpToDateFeedback,
                    )
                }
            }
        }
    }

    actual fun downloadUpdate() {
        val u = updater ?: return
        scope.launch {
            try {
                u.downloadUpdate()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.Error(t.message ?: "Download failed"),
                        isDialogVisible = true,
                    )
                }
            }
        }
    }

    private fun installMacosDmgUpdate(dmgFile: File) {
        val pid = ProcessHandle.current().pid()
        val scriptFile = File.createTempFile("khayin_updater_", ".sh").apply {
            deleteOnExit()
        }

        val scriptContent = """
            #!/bin/bash
            # Wait for current KhaYin process to exit
            while kill -0 $pid 2>/dev/null; do
                sleep 0.2
            done

            # Mount DMG to temporary mountpoint
            MOUNT_DIR=${'$'}(mktemp -d /tmp/khayin_mount_XXXXXX)
            hdiutil attach "${dmgFile.absolutePath}" -nobrowse -readonly -mountpoint "${'$'}MOUNT_DIR" -quiet

            # Find .app inside the mounted DMG
            APP_SOURCE=${'$'}(find "${'$'}MOUNT_DIR" -maxdepth 1 -name "*.app" | head -n 1)

            if [ -n "${'$'}APP_SOURCE" ] && [ -d "${'$'}APP_SOURCE" ]; then
                APP_NAME=${'$'}(basename "${'$'}APP_SOURCE")
                TARGET_APP="/Applications/${'$'}APP_NAME"

                # Strict security assertion: Target MUST strictly end with KhaYin.app or Nuvio.app
                if [[ "${'$'}TARGET_APP" == "/Applications/KhaYin.app" || "${'$'}TARGET_APP" == "/Applications/Nuvio.app" || "${'$'}TARGET_APP" == "/Applications/KhaYin Admin.app" ]]; then
                    xattr -cr "${'$'}APP_SOURCE" 2>/dev/null || true
                    rm -rf "${'$'}TARGET_APP"
                    ditto "${'$'}APP_SOURCE" "${'$'}TARGET_APP"
                    xattr -cr "${'$'}TARGET_APP" 2>/dev/null || true
                fi
            fi

            # Cleanly unmount and cleanup
            hdiutil detach "${'$'}MOUNT_DIR" -quiet 2>/dev/null || true
            rm -rf "${'$'}MOUNT_DIR"
            rm -f "${dmgFile.absolutePath}" 2>/dev/null || true
            rm -f "${scriptFile.absolutePath}" 2>/dev/null || true

            # Relaunch the newly installed app
            if [ -n "${'$'}TARGET_APP" ] && [ -d "${'$'}TARGET_APP" ]; then
                open "${'$'}TARGET_APP"
            fi
        """.trimIndent()

        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        // Cleanly shut down engine daemons before exiting
        try {
            com.nuvio.app.features.p2p.P2pStreamingEngine.shutdown()
            com.nuvio.app.features.discordrpc.DiscordPresenceManager.shutdown()
            com.nuvio.app.core.analytics.PostHogLogger.flush()
            com.nuvio.app.core.analytics.PostHogTracer.flush()
        } catch (_: Throwable) {}

        // Launch helper script detached
        ProcessBuilder("/bin/bash", scriptFile.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        // Exit current app so the helper script can replace the application bundle
        kotlin.system.exitProcess(0)
    }

    private fun installWindowsUpdate(installerFile: File) {
        val pid = ProcessHandle.current().pid()
        val scriptFile = File.createTempFile("khayin_updater_", ".bat").apply {
            deleteOnExit()
        }

        val scriptContent = """
            @echo off
            :waitloop
            tasklist /fi "PID eq $pid" | findstr /i "$pid" >nul
            if not errorlevel 1 (
                timeout /t 1 /nobreak >nul
                goto waitloop
            )
            start "" "${installerFile.absolutePath}"
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
                else -> {
                    val u = updater ?: return
                    u.installUpdate()
                }
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(status = AppUpdateStatus.Error(t.message ?: "Installation failed"))
            }
        }
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
