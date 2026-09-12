package com.nuvio.app.features.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppVersionPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
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
    private const val USER_AGENT = "KhaYin-Android-Updater"

    private val log = Logger.withTag("AndroidAppUpdater")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow(AppUpdateState())
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var isInitialized = false

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

    fun initializeWithContext(context: Context) {
        appContext = context.applicationContext
        initialize()
    }

    private fun chooseBestAndroidAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supported = Build.SUPPORTED_ABIS?.toList().orEmpty()
        for (abi in supported) {
            val candidate = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (candidate != null) return candidate
        }

        val universal = apkAssets.firstOrNull {
            val n = it.name.lowercase()
            n.contains("universal") || n.contains("arm64")
        }
        return universal ?: apkAssets.first()
    }

    actual fun initialize() {
        if (isInitialized) return
        val context = appContext ?: return
        isInitialized = true
        log.i { "initialize() — Android update checker started" }

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
            val chosenAsset = chooseBestAndroidAsset(release.assets)

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
            log.e(t) { "Android update check error: ${t.message}" }
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
        val context = appContext ?: return
        if (downloadUrl.isBlank()) return

        scope.launch {
            _state.update {
                it.copy(
                    status = AppUpdateStatus.Downloading(progress = 0f),
                    isDialogVisible = true,
                )
            }

            try {
                val downloadDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(downloadDir, update.assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
                if (targetFile.exists()) targetFile.delete()

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

                _state.update {
                    it.copy(
                        status = AppUpdateStatus.ReadyToInstall(targetFile.absolutePath),
                        isDialogVisible = true,
                    )
                }
            } catch (t: Throwable) {
                log.e(t) { "Android downloadUpdate failed: ${t.message}" }
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
        val context = appContext ?: return
        val readyStatus = _state.value.status as? AppUpdateStatus.ReadyToInstall
        val downloadedFile = readyStatus?.filePath?.let { File(it) }?.takeIf { it.exists() } ?: return

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, downloadedFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            log.e(t) { "Android installUpdate launch failed: ${t.message}" }
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
