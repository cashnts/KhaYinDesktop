package com.nuvio.app.features.updater

import kotlinx.coroutines.flow.StateFlow

expect object PlatformAppUpdater {
    val state: StateFlow<AppUpdateState>
    fun initialize()
    fun checkForUpdate(manual: Boolean = false)
    fun downloadUpdate()
    fun installUpdate()
    fun showUpdateDialog()
    fun dismissDialog()
    fun dismissUpToDateFeedback()
}
