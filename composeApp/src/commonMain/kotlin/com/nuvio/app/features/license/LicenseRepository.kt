package com.nuvio.app.features.license

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseConfig
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import io.github.jan.supabase.auth.auth
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    private var heartbeatJob: Job? = null

    fun initialize() {
        if (initialized) return
        initialized = true

        val cachedPayload = LicenseStorage.loadLicensePayload()
        if (!cachedPayload.isNullOrBlank()) {
            val cachedInfo = runCatching { json.decodeFromString<LicenseInfo>(cachedPayload) }.getOrNull()
            if (cachedInfo != null) {
                if (cachedInfo.status.equals("revoked", ignoreCase = true)) {
                    ProfileRepository.clearAll()
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

        startHeartbeat()
    }

    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            if (_state.value is LicenseState.Active) {
                verifyRemoteLicense()
            }
            while (true) {
                delay(15_000L) // 15s heartbeat interval for fast detection of admin revoking
                if (_state.value is LicenseState.Active) {
                    verifyRemoteLicense()
                }
            }
        }
    }

    private fun supabaseRestUrl(): String {
        val custom = ServerConfigurationRepository.active.value.backendUrl.trimEnd('/')
        val base = if (custom.isNotBlank()) custom else SupabaseConfig.URL
        return "$base/rest/v1"
    }

    private fun supabaseHeaders(): Map<String, String> {
        val key = ServerConfigurationRepository.active.value.publishableKey.ifBlank { SupabaseConfig.ANON_KEY }
        val token = runCatching { SupabaseProvider.client.auth.currentAccessTokenOrNull() }.getOrNull()?.takeIf { it.isNotBlank() } ?: key
        return mapOf(
            "apikey" to key,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation",
        )
    }

    private fun checkResponseOrThrow(response: RawHttpResponse, actionName: String) {
        val body = response.body.trim()
        if (response.status !in 200..299) {
            if (body.startsWith("<") || body.contains("<!DOCTYPE", ignoreCase = true)) {
                throw IllegalStateException("Supabase returned HTTP ${response.status}. Please ensure your Supabase backend is reachable.")
            }
            val errorObj = runCatching { json.decodeFromString<SupabaseErrorResponse>(body) }.getOrNull()
            val msg = errorObj?.message ?: errorObj?.error ?: "Supabase error (HTTP ${response.status})"
            if (msg.contains("relation \"public.license_keys\" does not exist") || msg.contains("does not exist")) {
                throw IllegalStateException("Table 'license_keys' does not exist in Supabase yet. Please create it in your Supabase SQL editor.")
            }
            throw IllegalStateException(msg)
        }
        if (body.startsWith("<") || body.contains("<!DOCTYPE", ignoreCase = true)) {
            throw IllegalStateException("Received HTML error from server (HTTP ${response.status}).")
        }
    }

    suspend fun activate(rawKey: String): Result<LicenseInfo> {
        val key = rawKey.trim().uppercase()
        if (key.isBlank()) {
            _error.value = "Please enter a valid license key"
            return Result.failure(IllegalArgumentException(_error.value))
        }

        _error.value = null
        val restUrl = supabaseRestUrl()

        return runCatching {
            val response = httpRequestRaw(
                method = "GET",
                url = "$restUrl/license_keys?key=eq.$key&select=*",
                headers = supabaseHeaders(),
                body = "",
            )

            checkResponseOrThrow(response, "License Activation")

            val records = json.decodeFromString<List<SupabaseLicenseRecord>>(response.body)
            if (records.isEmpty()) {
                val err = "License key '$key' was not found."
                _error.value = err
                throw IllegalStateException(err)
            }

            val record = records.first()
            val info = record.toLicenseInfo()

            if (info.status.equals("revoked", ignoreCase = true)) {
                LicenseStorage.saveLicensePayload(json.encodeToString(info))
                _state.value = LicenseState.Revoked(info)
                val err = "This license key has been revoked."
                _error.value = err
                throw IllegalStateException(err)
            }

            if (isExpiredTimestamp(info.expiresAt)) {
                LicenseStorage.saveLicensePayload(json.encodeToString(info))
                _state.value = LicenseState.Expired(info)
                val err = "This license key has expired."
                _error.value = err
                throw IllegalStateException(err)
            }

            // Check if device limit reached for new activations
            val currentCachedKey = LicenseStorage.loadLicensePayload()?.let {
                runCatching { json.decodeFromString<LicenseInfo>(it).key }.getOrNull()
            }
            val isReactivationOnSameDevice = currentCachedKey.equals(info.key, ignoreCase = true)

            if (!isReactivationOnSameDevice && info.activeDevices >= info.maxDevices) {
                val err = "Maximum active devices limit reached (${info.activeDevices}/${info.maxDevices}). Please disconnect an existing device or upgrade your tier."
                _error.value = err
                throw IllegalStateException(err)
            }

            // If new device, increment active device count on Supabase up to maxDevices
            if (!isReactivationOnSameDevice) {
                val newActiveCount = minOf(info.activeDevices + 1, info.maxDevices)
                runCatching {
                    httpRequestRaw(
                        method = "PATCH",
                        url = "$restUrl/license_keys?key=eq.$key",
                        headers = supabaseHeaders(),
                        body = json.encodeToString(mapOf("active_devices" to newActiveCount)),
                    )
                }
            }

            LicenseStorage.saveLicensePayload(json.encodeToString(info))
            syncSupabaseIdentity(info.key)
            _state.value = LicenseState.Active(info)
            startHeartbeat()

            info
        }.onFailure { e ->
            log.e(e) { "License activation error: ${e.message}" }
            if (_error.value == null) _error.value = e.message ?: "License activation failed"
        }
    }

    suspend fun verifyRemoteLicense(): LicenseState {
        val currentInfo = state.value.activeInfo ?: return LicenseState.Unlicensed
        val restUrl = supabaseRestUrl()

        return runCatching {
            val response = httpRequestRaw(
                method = "GET",
                url = "$restUrl/license_keys?key=eq.${currentInfo.key}&select=*",
                headers = supabaseHeaders(),
                body = "",
            )

            checkResponseOrThrow(response, "License Verification")

            val records = json.decodeFromString<List<SupabaseLicenseRecord>>(response.body)
            if (records.isEmpty()) {
                val revoked = currentInfo.copy(status = "revoked")
                LicenseStorage.saveLicensePayload(json.encodeToString(revoked))
                _state.value = LicenseState.Revoked(revoked)
                return@runCatching LicenseState.Revoked(revoked)
            }

            val updated = records.first().toLicenseInfo()
            LicenseStorage.saveLicensePayload(json.encodeToString(updated))

            // Analytics Heartbeat Ping to register device heartbeat and telemetry
            val todayIso = CurrentDateProvider.todayIsoDate()
            val devId = getOrCreateDeviceId()
            runCatching {
                httpRequestRaw(
                    method = "POST",
                    url = "$restUrl/license_analytics",
                    headers = supabaseHeaders(),
                    body = json.encodeToString(mapOf(
                        "license_key" to currentInfo.key,
                        "device_id" to devId,
                        "platform" to "KhaYin-Client",
                        "version" to "1.1.20",
                        "event" to "heartbeat",
                        "last_seen_at" to todayIso,
                    )),
                )
            }

            val newState = if (updated.status.equals("revoked", ignoreCase = true)) {
                LicenseState.Revoked(updated)
            } else if (isExpiredTimestamp(updated.expiresAt)) {
                LicenseState.Expired(updated)
            } else {
                LicenseState.Active(updated)
            }
            _state.value = newState
            newState
        }.getOrElse { e ->
            log.w(e) { "Remote license verification error: ${e.message}" }
            state.value
        }
    }

    fun deactivate() {
        val currentInfo = state.value.activeInfo
        if (currentInfo != null) {
            val restUrl = supabaseRestUrl()
            val newCount = maxOf(0, currentInfo.activeDevices - 1)
            scope.launch {
                runCatching {
                    httpRequestRaw(
                        method = "PATCH",
                        url = "$restUrl/license_keys?key=eq.${currentInfo.key}",
                        headers = supabaseHeaders(),
                        body = json.encodeToString(mapOf("active_devices" to newCount)),
                    )
                }
            }
        }
        heartbeatJob?.cancel()
        heartbeatJob = null
        LicenseStorage.clearLicensePayload()
        AuthStorage.clearAnonymousUserId()
        _state.value = LicenseState.Unlicensed
    }

    private fun isExpiredTimestamp(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrBlank()) return false
        val today = CurrentDateProvider.todayIsoDate()
        // Lexicographical ISO comparison works accurately for YYYY-MM-DD
        val expDate = expiresAt.take(10)
        return expDate < today
    }

    private fun syncSupabaseIdentity(licenseKey: String) {
        val cleanKey = licenseKey.trim().uppercase().replace("-", "")
        val deterministicUserId = buildDeterministicUuid(cleanKey)
        AuthStorage.saveAnonymousUserId(deterministicUserId)
        AuthRepository.initialize()
    }

    private fun buildDeterministicUuid(seed: String): String {
        val padded = (seed + "00000000000000000000000000000000").take(32)
        return "${padded.substring(0, 8)}-${padded.substring(8, 12)}-${padded.substring(12, 16)}-${padded.substring(16, 20)}-${padded.substring(20, 32)}".lowercase()
    }

    private fun generateLicenseKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun chunk(len: Int) = (1..len).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "KHAYIN-${chunk(4)}-${chunk(4)}-${chunk(4)}"
    }

    // --- BUILT-IN ADMIN CLIENT OPERATIONS (DIRECT SUPABASE REST) ---

    suspend fun adminListLicenses(adminPassword: String = ""): Result<List<LicenseInfo>> = runCatching {
        val restUrl = supabaseRestUrl()
        val response = httpRequestRaw(
            method = "GET",
            url = "$restUrl/license_keys?select=*&order=created_at.desc",
            headers = supabaseHeaders(),
            body = "",
        )

        checkResponseOrThrow(response, "List Licenses")

        val records = json.decodeFromString<List<SupabaseLicenseRecord>>(response.body)
        records.map { it.toLicenseInfo() }
    }

    suspend fun adminCreateLicense(
        adminPassword: String = "",
        request: AdminLicenseCreateRequest,
    ): Result<LicenseInfo> = runCatching {
        val restUrl = supabaseRestUrl()
        val key = generateLicenseKey()
        val today = CurrentDateProvider.todayIsoDate()

        val expiresAt = request.durationDays?.takeIf { it > 0 }?.let { days ->
            val parts = today.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                var y = parts[0]
                var m = parts[1]
                var d = parts[2] + days
                while (d > 28) {
                    val daysInMonth = when (m) {
                        2 -> if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 29 else 28
                        4, 6, 9, 11 -> 30
                        else -> 31
                    }
                    if (d > daysInMonth) {
                        d -= daysInMonth
                        m++
                        if (m > 12) {
                            m = 1
                            y++
                        }
                    } else {
                        break
                    }
                }
                val mStr = if (m < 10) "0$m" else "$m"
                val dStr = if (d < 10) "0$d" else "$d"
                "$y-$mStr-${dStr}T23:59:59Z"
            } else null
        }

        val record = SupabaseLicenseRecord(
            key = key,
            status = "active",
            customerName = request.customerName,
            tier = request.tier ?: "standard",
            expiresAt = expiresAt,
            maxDevices = request.maxDevices ?: 1,
            activeDevices = 0,
            notes = request.notes,
        )

        val response = httpRequestRaw(
            method = "POST",
            url = "$restUrl/license_keys",
            headers = supabaseHeaders(),
            body = json.encodeToString(record),
        )

        checkResponseOrThrow(response, "Create License")

        val createdRecords = runCatching { json.decodeFromString<List<SupabaseLicenseRecord>>(response.body) }.getOrNull()
        createdRecords?.firstOrNull()?.toLicenseInfo() ?: record.toLicenseInfo()
    }

    suspend fun adminRevokeLicense(adminPassword: String = "", key: String): Result<Unit> = runCatching {
        val restUrl = supabaseRestUrl()
        val response = httpRequestRaw(
            method = "PATCH",
            url = "$restUrl/license_keys?key=eq.$key",
            headers = supabaseHeaders(),
            body = """{"status":"revoked"}""",
        )
        checkResponseOrThrow(response, "Revoke License")
    }

    suspend fun adminExtendLicense(adminPassword: String = "", key: String, days: Int = 30): Result<LicenseInfo> = runCatching {
        val restUrl = supabaseRestUrl()
        val today = CurrentDateProvider.todayIsoDate()
        val parts = today.split("-").mapNotNull { it.toIntOrNull() }
        var y = parts.getOrElse(0) { 2026 }
        var m = parts.getOrElse(1) { 1 }
        var d = parts.getOrElse(2) { 1 } + days
        while (d > 28) {
            val daysInMonth = when (m) {
                2 -> if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            if (d > daysInMonth) {
                d -= daysInMonth
                m++
                if (m > 12) {
                    m = 1
                    y++
                }
            } else {
                break
            }
        }
        val mStr = if (m < 10) "0$m" else "$m"
        val dStr = if (d < 10) "0$d" else "$d"
        val newExpiresAt = "$y-$mStr-${dStr}T23:59:59Z"

        val response = httpRequestRaw(
            method = "PATCH",
            url = "$restUrl/license_keys?key=eq.$key",
            headers = supabaseHeaders(),
            body = json.encodeToString(mapOf("expires_at" to newExpiresAt, "status" to "active")),
        )
        checkResponseOrThrow(response, "Extend License")

        val updated = json.decodeFromString<List<SupabaseLicenseRecord>>(response.body).first().toLicenseInfo()
        updated
    }
}
