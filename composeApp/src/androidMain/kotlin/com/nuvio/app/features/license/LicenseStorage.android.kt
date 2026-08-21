package com.nuvio.app.features.license

import android.content.Context
import android.content.SharedPreferences

actual object LicenseStorage {
    private const val preferencesName = "nuvio_license_cache"
    private const val payloadKey = "license_payload"
    private const val deviceIdKey = "device_unique_id"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadLicensePayload(): String? =
        preferences?.getString(payloadKey, null)

    actual fun saveLicensePayload(payload: String) {
        preferences?.edit()?.putString(payloadKey, payload)?.apply()
    }

    actual fun clearLicensePayload() {
        preferences?.edit()?.remove(payloadKey)?.apply()
    }

    actual fun loadDeviceId(): String? =
        preferences?.getString(deviceIdKey, null)

    actual fun saveDeviceId(deviceId: String) {
        preferences?.edit()?.putString(deviceIdKey, deviceId)?.apply()
    }
}
