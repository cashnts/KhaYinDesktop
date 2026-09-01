package com.nuvio.app.features.updater

data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val assetName: String,
    val downloadUrl: String,
    val isPrerelease: Boolean = false,
)

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateStatus
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
    ) : AppUpdateStatus
    data class ReadyToInstall(val filePath: String = "") : AppUpdateStatus
    data object UpToDate : AppUpdateStatus
    data class Error(val message: String) : AppUpdateStatus
}

data class AppUpdateState(
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    val isDialogVisible: Boolean = false,
    val showUpToDateFeedback: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
) {
    val isChecking: Boolean
        get() = status is AppUpdateStatus.Checking

    val availableUpdate: AppUpdateInfo?
        get() = (status as? AppUpdateStatus.UpdateAvailable)?.info
}
