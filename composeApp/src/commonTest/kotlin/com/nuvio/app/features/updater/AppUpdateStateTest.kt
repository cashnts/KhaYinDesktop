package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateStateTest {

    @Test
    fun initialStateIsIdle() {
        val state = AppUpdateState()
        assertEquals(AppUpdateStatus.Idle, state.status)
        assertFalse(state.isChecking)
        assertFalse(state.isDialogVisible)
        assertFalse(state.showUpToDateFeedback)
        assertNull(state.availableUpdate)
    }

    @Test
    fun checkingStateReportsIsChecking() {
        val state = AppUpdateState(status = AppUpdateStatus.Checking)
        assertTrue(state.isChecking)
        assertNull(state.availableUpdate)
    }

    @Test
    fun updateAvailableStateExtractsInfo() {
        val info = AppUpdateInfo(
            versionName = "2.0.0",
            releaseTitle = "v2.0.0 Major Release",
            changelog = "- New design\n- Performance improvements",
            assetName = "Nuvio-2.0.0.dmg",
            downloadUrl = "https://github.com/cashnts/KhaYinDesktop/releases/download/v2.0.0/Nuvio-2.0.0.dmg",
        )
        val state = AppUpdateState(
            status = AppUpdateStatus.UpdateAvailable(info),
            availableUpdate = info,
            isDialogVisible = true,
        )

        assertFalse(state.isChecking)
        assertTrue(state.isDialogVisible)
        assertNotNull(state.availableUpdate)
        assertEquals("2.0.0", state.availableUpdate?.versionName)
        assertEquals("Nuvio-2.0.0.dmg", state.availableUpdate?.assetName)
    }

    @Test
    fun downloadingStateHoldsProgress() {
        val state = AppUpdateState(
            status = AppUpdateStatus.Downloading(
                progress = 0.65f,
                bytesDownloaded = 65_000_000L,
                totalBytes = 100_000_000L,
            ),
            isDialogVisible = true,
        )

        assertFalse(state.isChecking)
        assertTrue(state.status is AppUpdateStatus.Downloading)
        val downloading = state.status as AppUpdateStatus.Downloading
        assertEquals(0.65f, downloading.progress)
        assertEquals(65_000_000L, downloading.bytesDownloaded)
        assertEquals(100_000_000L, downloading.totalBytes)
    }

    @Test
    fun readyToInstallStateHoldsFilePath() {
        val state = AppUpdateState(
            status = AppUpdateStatus.ReadyToInstall(filePath = "/tmp/Nuvio-2.0.0.dmg"),
            isDialogVisible = true,
        )

        assertTrue(state.status is AppUpdateStatus.ReadyToInstall)
        assertEquals("/tmp/Nuvio-2.0.0.dmg", (state.status as AppUpdateStatus.ReadyToInstall).filePath)
    }

    @Test
    fun errorStateHoldsErrorMessage() {
        val state = AppUpdateState(
            status = AppUpdateStatus.Error(message = "Network timeout"),
            showUpToDateFeedback = true,
        )

        assertTrue(state.status is AppUpdateStatus.Error)
        assertEquals("Network timeout", (state.status as AppUpdateStatus.Error).message)
        assertTrue(state.showUpToDateFeedback)
    }
}
