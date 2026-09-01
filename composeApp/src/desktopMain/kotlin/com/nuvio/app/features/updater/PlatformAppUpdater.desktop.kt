package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionPolicy
import com.pavi2410.appupdater.AppUpdater
import com.pavi2410.appupdater.DesktopAssetDownloader
import com.pavi2410.appupdater.DesktopAssetInstaller
import com.pavi2410.appupdater.GitHubUpdateSource
import com.pavi2410.appupdater.UpdateState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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

    actual fun installUpdate() {
        val u = updater ?: return
        try {
            u.installUpdate()
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
