package com.nuvio.app.features.license

import com.nuvio.app.core.storage.DesktopStorage

internal actual object LicenseStorage {
    private val store = DesktopStorage.store("nuvio_license")
    private const val payloadKey = "license_payload"
    private const val lastKnownKey = "last_known_license_key"
    private const val deviceIdKey = "device_unique_id"
    private const val dismissedBroadcastKey = "dismissed_broadcast_timestamp"

    actual fun loadLicensePayload(): String? =
        store.getString(payloadKey)

    actual fun saveLicensePayload(payload: String) {
        store.putString(payloadKey, payload)
    }

    actual fun clearLicensePayload() {
        store.remove(payloadKey)
    }

    actual fun loadLastKnownKey(): String? =
        store.getString(lastKnownKey)

    actual fun saveLastKnownKey(key: String) {
        store.putString(lastKnownKey, key)
    }

    actual fun loadDeviceId(): String? =
        store.getString(deviceIdKey)

    actual fun saveDeviceId(deviceId: String) {
        store.putString(deviceIdKey, deviceId)
    }

    private const val freeModeKey = "is_free_mode"
    private const val mmsubQuotaKey = "mmsub_quota_data"

    actual fun loadDismissedBroadcastTimestamp(): Long =
        store.getString(dismissedBroadcastKey)?.toLongOrNull() ?: 0L

    actual fun saveDismissedBroadcastTimestamp(timestamp: Long) {
        store.putString(dismissedBroadcastKey, timestamp.toString())
    }

    actual fun isFreeMode(): Boolean =
        store.getBoolean(freeModeKey) ?: false

    actual fun saveFreeMode(isFree: Boolean) {
        store.putBoolean(freeModeKey, isFree)
    }

    actual fun loadMmsubQuotaData(): String? =
        store.getString(mmsubQuotaKey)

    actual fun saveMmsubQuotaData(data: String) {
        store.putString(mmsubQuotaKey, data)
    }
}
