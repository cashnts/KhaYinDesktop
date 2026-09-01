package com.nuvio.app.features.updater

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

actual object PlatformAppUpdater {
    private val _state = MutableStateFlow(AppUpdateState())
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    actual fun initialize() = Unit

    actual fun checkForUpdate(manual: Boolean) {
        if (manual) {
            _state.update {
                it.copy(
                    status = AppUpdateStatus.UpToDate,
                    showUpToDateFeedback = true,
                )
            }
        }
    }

    actual fun downloadUpdate() = Unit
    actual fun installUpdate() = Unit
    actual fun showUpdateDialog() = Unit
    actual fun dismissDialog() = Unit
    actual fun dismissUpToDateFeedback() {
        _state.update { it.copy(showUpToDateFeedback = false) }
    }
}
