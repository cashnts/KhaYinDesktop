package com.nuvio.app.features.license

import com.nuvio.app.core.storage.DesktopStorage

internal actual object LicenseStorage {
    private val store = DesktopStorage.store("nuvio_license")
    private const val payloadKey = "license_payload"
    private const val deviceIdKey = "device_unique_id"

    actual fun loadLicensePayload(): String? =
        store.getString(payloadKey)

    actual fun saveLicensePayload(payload: String) {
        store.putString(payloadKey, payload)
    }

    actual fun clearLicensePayload() {
        store.remove(payloadKey)
    }

    actual fun loadDeviceId(): String? =
        store.getString(deviceIdKey)

    actual fun saveDeviceId(deviceId: String) {
        store.putString(deviceIdKey, deviceId)
    }
}
