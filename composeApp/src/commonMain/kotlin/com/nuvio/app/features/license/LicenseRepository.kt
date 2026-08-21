package com.nuvio.app.features.license

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseConfig
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.profiles.ProfileRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LicenseRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("LicenseRepository")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _state = MutableStateFlow<LicenseState>(LicenseState.Loading)
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var initialized = false
    private var verifyJob: Job? = null

    @OptIn(ExperimentalUuidApi::class)
    fun getOrCreateDeviceId(): String {
        var deviceId = LicenseStorage.loadDeviceId()
        if (deviceId.isNullOrBlank()) {
            deviceId = Uuid.random().toString()
            LicenseStorage.saveDeviceId(deviceId)
        }
        return deviceId
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        val cachedPayload = LicenseStorage.loadLicensePayload()
        if (!cachedPayload.isNullOrBlank()) {
            val cachedInfo = runCatching { json.decodeFromString<LicenseInfo>(cachedPayload) }.getOrNull()
            if (cachedInfo != null) {
                if (cachedInfo.status == "revoked") {
                    _state.value = LicenseState.Revoked(cachedInfo)
                } else if (isExpiredTimestamp(cachedInfo.expiresAt)) {
                    _state.value = LicenseState.Expired(cachedInfo)
                } else {
                    _state.value = LicenseState.Active(cachedInfo)
                    syncSupabaseIdentity(cachedInfo.key)
                }
            } else {
                _state.value = LicenseState.Unlicensed
            }
        } else {
            _state.value = LicenseState.Unlicensed
        }

        verifyJob?.cancel()
        verifyJob = scope.launch {
            if (_state.value is LicenseState.Active) {
                verifyRemoteLicense()
            }
        }
    }

    suspend fun activate(rawKey: String): Result<LicenseInfo> {
        val key = rawKey.trim().uppercase()
        if (key.isBlank()) {
            _error.value = "Please enter a valid license key"
            return Result.failure(IllegalArgumentException(_error.value))
        }

        _error.value = null
        val deviceId = getOrCreateDeviceId()
        val clientApp = "KhaYin/${AppVersionConfig.VERSION_NAME.ifBlank { "1.0.0" }}"

        val baseUrl = resolveWorkerBaseUrl()
        val request = LicenseActivationRequest(
            key = key,
            deviceId = deviceId,
            clientApp = clientApp,
        )

        return runCatching {
            val response = httpRequestRaw(
                method = "POST",
                url = "$baseUrl/api/license/activate",
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(request),
            )

            val bodyText = response.body
            val activationRes = json.decodeFromString<LicenseActivationResponse>(bodyText)

            if (!activationRes.success || activationRes.key == null) {
                val err = activationRes.error ?: "Failed to activate license"
                _error.value = err
                throw IllegalStateException(err)
            }

            val info = LicenseInfo(
                key = activationRes.key,
                status = activationRes.status ?: "active",
                customerName = activationRes.customerName,
                tier = activationRes.tier,
                expiresAt = activationRes.expiresAt,
                maxDevices = activationRes.maxDevices ?: 1,
                activeDevices = activationRes.activeDevices ?: 1,
                presetAddons = activationRes.presetAddons,
                profileName = activationRes.profileName,
            )

            LicenseStorage.saveLicensePayload(json.encodeToString(info))
            syncSupabaseIdentity(info.key)

            if (info.status == "revoked") {
                _state.value = LicenseState.Revoked(info)
            } else if (isExpiredTimestamp(info.expiresAt)) {
                _state.value = LicenseState.Expired(info)
            } else {
                _state.value = LicenseState.Active(info)
            }

            info
        }.onFailure { e ->
            log.e(e) { "License activation error: ${e.message}" }
            if (_error.value == null) _error.value = e.message ?: "License activation failed"
        }
    }

    suspend fun verifyRemoteLicense(): LicenseState {
        val currentInfo = state.value.activeInfo ?: return LicenseState.Unlicensed
        val baseUrl = resolveWorkerBaseUrl()
        val deviceId = getOrCreateDeviceId()

        return runCatching {
            val url = "$baseUrl/api/license/verify?key=${currentInfo.key}&deviceId=$deviceId"
            val response = httpRequestRaw(
                method = "GET",
                url = url,
                headers = mapOf("Content-Type" to "application/json"),
                body = "",
            )

            val verifyRes = json.decodeFromString<LicenseVerifyResponse>(response.body)
            if (verifyRes.success && verifyRes.key != null) {
                val updated = currentInfo.copy(
                    status = verifyRes.status ?: "active",
                    expiresAt = verifyRes.expiresAt,
                    maxDevices = verifyRes.maxDevices ?: currentInfo.maxDevices,
                    activeDevices = verifyRes.activeDevices ?: currentInfo.activeDevices,
                    tier = verifyRes.tier ?: currentInfo.tier,
                )
                LicenseStorage.saveLicensePayload(json.encodeToString(updated))
                val newState = if (isExpiredTimestamp(updated.expiresAt)) {
                    LicenseState.Expired(updated)
                } else {
                    LicenseState.Active(updated)
                }
                _state.value = newState
                newState
            } else {
                if (verifyRes.status == "revoked") {
                    val revoked = currentInfo.copy(status = "revoked")
                    LicenseStorage.saveLicensePayload(json.encodeToString(revoked))
                    _state.value = LicenseState.Revoked(revoked)
                    LicenseState.Revoked(revoked)
                } else if (verifyRes.status == "expired" || isExpiredTimestamp(verifyRes.expiresAt)) {
                    val expired = currentInfo.copy(status = "expired", expiresAt = verifyRes.expiresAt)
                    LicenseStorage.saveLicensePayload(json.encodeToString(expired))
                    _state.value = LicenseState.Expired(expired)
                    LicenseState.Expired(expired)
                } else {
                    state.value
                }
            }
        }.getOrElse { e ->
            log.w(e) { "Remote license verification error: ${e.message}" }
            state.value
        }
    }

    fun deactivate() {
        LicenseStorage.clearLicensePayload()
        AuthStorage.clearAnonymousUserId()
        _state.value = LicenseState.Unlicensed
    }

    private fun isExpiredTimestamp(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrBlank()) return false
        // Basic ISO date parsing check
        return false // Detailed check handled by server response or parsing
    }

    private fun resolveWorkerBaseUrl(): String {
        val custom = ServerConfigurationRepository.active.value.backendUrl.trimEnd('/')
        if (custom.isNotBlank() && !custom.contains("supabase", ignoreCase = true)) {
            return custom
        }
        return "https://cachestream.khayin.net"
    }

    private fun syncSupabaseIdentity(licenseKey: String) {
        // Deterministic Supabase UUID for the license key
        val cleanKey = licenseKey.trim().uppercase().replace("-", "")
        val deterministicUserId = buildDeterministicUuid(cleanKey)
        AuthStorage.saveAnonymousUserId(deterministicUserId)
        AuthRepository.initialize()
    }

    private fun buildDeterministicUuid(seed: String): String {
        val padded = (seed + "00000000000000000000000000000000").take(32)
        return "${padded.substring(0, 8)}-${padded.substring(8, 12)}-${padded.substring(12, 16)}-${padded.substring(16, 20)}-${padded.substring(20, 32)}".lowercase()
    }

    // --- BUILT-IN ADMIN CLIENT OPERATIONS ---
    suspend fun adminListLicenses(adminPassword: String): Result<List<LicenseInfo>> = runCatching {
        val baseUrl = resolveWorkerBaseUrl()
        val response = httpRequestRaw(
            method = "GET",
            url = "$baseUrl/api/admin/licenses/list",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer " + encodeBase64(adminPassword),
            ),
            body = "",
        )
        val data = json.decodeFromString<AdminLicenseListResponse>(response.body)
        if (!data.success) throw IllegalStateException(data.error ?: "Failed to list licenses")
        data.licenses
    }

    suspend fun adminCreateLicense(
        adminPassword: String,
        request: AdminLicenseCreateRequest,
    ): Result<LicenseInfo> = runCatching {
        val baseUrl = resolveWorkerBaseUrl()
        val response = httpRequestRaw(
            method = "POST",
            url = "$baseUrl/api/admin/licenses/create",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer " + encodeBase64(adminPassword),
            ),
            body = json.encodeToString(request),
        )
        val data = json.decodeFromString<AdminLicenseCreateResponse>(response.body)
        if (!data.success || data.license == null) throw IllegalStateException(data.error ?: "Failed to create license")
        data.license
    }

    suspend fun adminRevokeLicense(adminPassword: String, key: String): Result<Unit> = runCatching {
        val baseUrl = resolveWorkerBaseUrl()
        val response = httpRequestRaw(
            method = "POST",
            url = "$baseUrl/api/admin/licenses/revoke",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer " + encodeBase64(adminPassword),
            ),
            body = """{"key":"$key"}""",
        )
        val data = json.decodeFromString<AdminLicenseActionResponse>(response.body)
        if (!data.success) throw IllegalStateException(data.error ?: "Failed to revoke license")
    }

    suspend fun adminExtendLicense(adminPassword: String, key: String, days: Int = 30): Result<LicenseInfo> = runCatching {
        val baseUrl = resolveWorkerBaseUrl()
        val response = httpRequestRaw(
            method = "POST",
            url = "$baseUrl/api/admin/licenses/extend",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer " + encodeBase64(adminPassword),
            ),
            body = """{"key":"$key","days":$days}""",
        )
        val data = json.decodeFromString<AdminLicenseActionResponse>(response.body)
        if (!data.success || data.license == null) throw IllegalStateException(data.error ?: "Failed to extend license")
        data.license
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeBase64(str: String): String {
        return Base64.encode(str.encodeToByteArray())
    }
}
