package com.nuvio.app.features.updater

import android.content.Context
import com.nuvio.app.core.build.AppVersionPolicy
import com.pavi2410.appupdater.AppUpdater
import com.pavi2410.appupdater.UpdateState
import com.pavi2410.appupdater.github
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

actual object PlatformAppUpdater {
    private const val GITHUB_OWNER = "cashnts"
    private const val GITHUB_REPO = "KhaYinDesktop"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow(AppUpdateState())
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var updater: AppUpdater? = null

    fun initializeWithContext(context: Context) {
        appContext = context.applicationContext
        initialize()
    }

    actual fun initialize() {
        val context = appContext ?: return
        if (updater != null) return

        val currentVersion = AppVersionPolicy.displayVersionName.ifBlank { "1.0.0" }
        val appUpdater = AppUpdater.github(
            context = context,
            owner = GITHUB_OWNER,
            repo = GITHUB_REPO,
            currentVersion = currentVersion,
            assetMatcher = { it.endsWith(".apk", ignoreCase = true) },
        )
        updater = appUpdater

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
                    it.copy(status = AppUpdateStatus.Error(t.message ?: "Download failed"))
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
