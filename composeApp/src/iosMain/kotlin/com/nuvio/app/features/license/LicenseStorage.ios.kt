package com.nuvio.app.features.license

import platform.Foundation.NSUserDefaults

actual object LicenseStorage {
    private const val payloadKey = "license_payload"
    private const val deviceIdKey = "device_unique_id"
    private const val dismissedBroadcastKey = "dismissed_broadcast_timestamp"

    actual fun loadLicensePayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey)

    actual fun saveLicensePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey)
    }

    actual fun clearLicensePayload() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(payloadKey)
    }

    actual fun loadDeviceId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(deviceIdKey)

    actual fun saveDeviceId(deviceId: String) {
        NSUserDefaults.standardUserDefaults.setObject(deviceId, forKey = deviceIdKey)
    }

    actual fun loadDismissedBroadcastTimestamp(): Long =
        NSUserDefaults.standardUserDefaults.stringForKey(dismissedBroadcastKey)?.toLongOrNull() ?: 0L

    actual fun saveDismissedBroadcastTimestamp(timestamp: Long) {
        NSUserDefaults.standardUserDefaults.setObject(timestamp.toString(), forKey = dismissedBroadcastKey)
    }
}
