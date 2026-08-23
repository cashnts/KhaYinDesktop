package com.nuvio.app.features.license

internal expect object LicenseStorage {
    fun loadLicensePayload(): String?
    fun saveLicensePayload(payload: String)
    fun clearLicensePayload()
    fun loadLastKnownKey(): String?
    fun saveLastKnownKey(key: String)
    fun loadDeviceId(): String?
    fun saveDeviceId(deviceId: String)
    fun loadDismissedBroadcastTimestamp(): Long
    fun saveDismissedBroadcastTimestamp(timestamp: Long)
}
