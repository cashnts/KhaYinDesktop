package com.nuvio.app.core.build

private val isWindowsDesktop = System.getProperty("os.name")
    ?.startsWith("Windows", ignoreCase = true)
    ?: false

actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = true
    actual val downloadsEnabled: Boolean = true
    actual val notificationsEnabled: Boolean = false
    actual val supportersContributorsPageEnabled: Boolean = true
    actual val donationActionsEnabled: Boolean = true
    actual val donationProgressEnabled: Boolean = false
    actual val accountDeletionEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    actual val p2pEnabled: Boolean = true
    actual val externalPlayerSupported: Boolean = false
    actual val trailerPlaybackMode: TrailerPlaybackMode =
        if (isWindowsDesktop) TrailerPlaybackMode.EXTERNAL else TrailerPlaybackMode.IN_APP
    actual val heroTrailerPlaybackSupported: Boolean = !isWindowsDesktop
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = false
    actual val customServerConnectionsEnabled: Boolean = ClientRoleConfig.IS_ADMIN
    actual val isAdminClient: Boolean = ClientRoleConfig.IS_ADMIN
    actual val isUserClient: Boolean = ClientRoleConfig.IS_USER
}
