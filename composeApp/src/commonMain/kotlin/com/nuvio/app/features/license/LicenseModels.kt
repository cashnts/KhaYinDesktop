package com.nuvio.app.features.license

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LicenseInfo(
    val key: String,
    val status: String = "active", // "active", "expired", "revoked"
    val customerName: String? = null,
    val tier: String? = "standard",
    val expiresAt: String? = null, // null = Lifetime
    val maxDevices: Int = 1,
    val activeDevices: Int = 1,
    val presetAddons: List<String> = emptyList(),
    val profileName: String? = null,
    val createdAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class SupabaseLicenseRecord(
    val key: String,
    val status: String = "active",
    @SerialName("customer_name") val customerName: String? = null,
    val tier: String? = "standard",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("max_devices") val maxDevices: Int = 1,
    @SerialName("active_devices") val activeDevices: Int = 0,
    @SerialName("preset_addons") val presetAddons: List<String> = emptyList(),
    @SerialName("profile_name") val profileName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val notes: String? = null,
) {
    fun toLicenseInfo(): LicenseInfo = LicenseInfo(
        key = key,
        status = status,
        customerName = customerName,
        tier = tier,
        expiresAt = expiresAt,
        maxDevices = maxDevices,
        activeDevices = activeDevices,
        presetAddons = presetAddons,
        profileName = profileName,
        createdAt = createdAt,
        notes = notes,
    )
}

@Serializable
data class SupabaseErrorResponse(
    val message: String? = null,
    val error: String? = null,
    val details: String? = null,
    val hint: String? = null,
    val code: String? = null,
)

sealed interface LicenseState {
    data object Loading : LicenseState
    data object Unlicensed : LicenseState
    data class Active(val info: LicenseInfo) : LicenseState
    data class Expired(val info: LicenseInfo) : LicenseState
    data class Revoked(val info: LicenseInfo) : LicenseState
}

val LicenseState.isActive: Boolean
    get() = this is LicenseState.Active

val LicenseState.isExpired: Boolean
    get() = this is LicenseState.Expired || this is LicenseState.Revoked

val LicenseState.licenseKey: String?
    get() = when (this) {
        is LicenseState.Active -> info.key
        is LicenseState.Expired -> info.key
        is LicenseState.Revoked -> info.key
        else -> null
    }

val LicenseState.activeInfo: LicenseInfo?
    get() = when (this) {
        is LicenseState.Active -> info
        is LicenseState.Expired -> info
        is LicenseState.Revoked -> info
        else -> null
    }

@Serializable
data class LicenseActivationRequest(
    val key: String,
    val deviceId: String,
    val clientApp: String,
)

@Serializable
data class LicenseActivationResponse(
    val success: Boolean,
    val status: String? = null,
    val key: String? = null,
    val customerName: String? = null,
    val tier: String? = null,
    val expiresAt: String? = null,
    val maxDevices: Int? = null,
    val activeDevices: Int? = null,
    val presetAddons: List<String> = emptyList(),
    val profileName: String? = null,
    val error: String? = null,
)

@Serializable
data class LicenseVerifyResponse(
    val success: Boolean,
    val status: String? = null,
    val key: String? = null,
    val customerName: String? = null,
    val tier: String? = null,
    val expiresAt: String? = null,
    val maxDevices: Int? = null,
    val activeDevices: Int? = null,
    val presetAddons: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
data class AdminLicenseCreateRequest(
    val customerName: String? = null,
    val durationDays: Int? = null,
    val maxDevices: Int? = null,
    val tier: String? = null,
    val notes: String? = null,
)

@Serializable
data class AdminLicenseCreateResponse(
    val success: Boolean,
    val license: LicenseInfo? = null,
    val error: String? = null,
)

@Serializable
data class AdminLicenseListResponse(
    val success: Boolean,
    val licenses: List<LicenseInfo> = emptyList(),
    val error: String? = null,
)

@Serializable
data class AdminLicenseActionResponse(
    val success: Boolean,
    val message: String? = null,
    val license: LicenseInfo? = null,
    val error: String? = null,
)
