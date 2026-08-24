package com.nuvio.app.features.license

import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseConfig
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SystemServiceConfig(
    val maintenanceMode: Boolean = false,
    val streamingDisabled: Boolean = false,
    val broadcastMessage: String = "",
    val broadcastTimestamp: Long = 0L,
    val presetAddons: List<String> = emptyList(),
)

@Serializable
data class LicenseAnalyticsRecord(
    val id: Long? = null,
    val license_key: String? = null,
    val device_id: String? = null,
    val platform: String? = null,
    val version: String? = null,
    val event: String? = null,
    val last_seen_at: String? = null,
    val created_at: String? = null,
)

@Serializable
data class AppSettingsRecord(
    val id: String = "global",
    val config: kotlinx.serialization.json.JsonElement? = null,
    val updated_at: String? = null,
)

object AdminControlRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val _config = MutableStateFlow(SystemServiceConfig())
    val config: StateFlow<SystemServiceConfig> = _config.asStateFlow()

    private val _dismissedBroadcastTimestamp = MutableStateFlow(0L)
    val dismissedBroadcastTimestamp: StateFlow<Long> = _dismissedBroadcastTimestamp.asStateFlow()

    fun dismissBroadcast(timestamp: Long) {
        val target = if (timestamp > 0L) timestamp else _config.value.broadcastTimestamp
        _dismissedBroadcastTimestamp.value = target
        LicenseStorage.saveDismissedBroadcastTimestamp(target)
    }

    fun refreshDismissedTimestamp() {
        val stored = LicenseStorage.loadDismissedBroadcastTimestamp()
        if (stored > _dismissedBroadcastTimestamp.value) {
            _dismissedBroadcastTimestamp.value = stored
        }
    }

    fun startPolling() {
        refreshDismissedTimestamp()
        if (pollingJob != null) return
        pollingJob = scope.launch {
            fetchConfig()
            while (true) {
                delay(5_000L) // poll service config every 5s for near real-time broadcasts
                fetchConfig()
            }
        }
    }

    private fun supabaseRestUrl(): String {
        val custom = ServerConfigurationRepository.active.value.backendUrl.trimEnd('/')
        val base = if (custom.isNotBlank()) custom else SupabaseConfig.URL
        return "$base/rest/v1"
    }

    private fun supabaseHeaders(method: String = "GET", url: String = "", body: String = ""): Map<String, String> {
        val key = ServerConfigurationRepository.active.value.publishableKey.ifBlank { SupabaseConfig.ANON_KEY }
        val token = runCatching { SupabaseProvider.client.auth.currentAccessTokenOrNull() }.getOrNull()?.takeIf { it.isNotBlank() } ?: key
        val baseHeaders = mapOf(
            "apikey" to key,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation,resolution=merge-duplicates",
        )
        return com.nuvio.app.core.security.KhaYinSecurityBridge.buildSecureHeaders(method, url, body, baseHeaders).first
    }

    suspend fun fetchConfig(): SystemServiceConfig {
        refreshDismissedTimestamp()
        return runCatching {
            val restUrl = supabaseRestUrl()
            
            // 1. Try dedicated app_settings table first
            val appSettingsUrl = "$restUrl/app_settings?id=eq.global&select=*"
            val appSettingsResponse = httpRequestRaw(
                method = "GET",
                url = appSettingsUrl,
                headers = supabaseHeaders(method = "GET", url = appSettingsUrl),
                body = "",
            )
            if (appSettingsResponse.status in 200..299 && !appSettingsResponse.body.startsWith("<")) {
                val records = runCatching { json.decodeFromString<List<AppSettingsRecord>>(appSettingsResponse.body) }.getOrNull()
                if (!records.isNullOrEmpty()) {
                    val rawConfig = records.first().config
                    val parsed = when (rawConfig) {
                        is kotlinx.serialization.json.JsonObject -> runCatching { json.decodeFromJsonElement(SystemServiceConfig.serializer(), rawConfig) }.getOrNull()
                        is kotlinx.serialization.json.JsonPrimitive -> runCatching { json.decodeFromString(SystemServiceConfig.serializer(), rawConfig.content) }.getOrNull()
                        else -> null
                    }
                    if (parsed != null) {
                        _config.value = parsed
                        return@runCatching parsed
                    }
                }
            }

            // 2. Fallback to legacy SYSTEM_CONFIG row in license_keys
            val legacyUrl = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG&select=*"
            val legacyResponse = httpRequestRaw(
                method = "GET",
                url = legacyUrl,
                headers = supabaseHeaders(method = "GET", url = legacyUrl),
                body = "",
            )
            if (legacyResponse.status in 200..299 && !legacyResponse.body.startsWith("<")) {
                val records = runCatching { json.decodeFromString<List<SupabaseLicenseRecord>>(legacyResponse.body) }.getOrNull()
                if (!records.isNullOrEmpty()) {
                    val notes = records.first().notes
                    if (!notes.isNullOrBlank()) {
                        val parsed = runCatching { json.decodeFromString<SystemServiceConfig>(notes) }.getOrNull()
                        if (parsed != null) {
                            _config.value = parsed
                            return@runCatching parsed
                        }
                    }
                }
            }
            _config.value
        }.getOrElse { _config.value }
    }

    suspend fun updateConfig(newConfig: SystemServiceConfig): Result<SystemServiceConfig> {
        return runCatching {
            val restUrl = supabaseRestUrl()
            _config.value = newConfig
            
            // 1. Save to dedicated app_settings table
            val appSettingsPayload = kotlinx.serialization.json.buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive("global"))
                put("config", json.encodeToJsonElement(SystemServiceConfig.serializer(), newConfig))
            }
            val appSettingsBody = json.encodeToString(appSettingsPayload)
            val appSettingsUrl = "$restUrl/app_settings"
            val appSettingsResponse = httpRequestRaw(
                method = "POST",
                url = appSettingsUrl,
                headers = supabaseHeaders(method = "POST", url = appSettingsUrl, body = appSettingsBody),
                body = appSettingsBody,
            )

            // If POST fails, try PATCH or fallback
            if (appSettingsResponse.status !in 200..299) {
                val patchUrl = "$restUrl/app_settings?id=eq.global"
                val patchResponse = httpRequestRaw(
                    method = "PATCH",
                    url = patchUrl,
                    headers = supabaseHeaders(method = "PATCH", url = patchUrl, body = appSettingsBody),
                    body = appSettingsBody,
                )
                if (patchResponse.status !in 200..299) {
                    // Fallback to writing to license_keys if app_settings table doesn't exist yet
                    val legacyPayload = SupabaseLicenseRecord(
                        key = "SYSTEM_CONFIG",
                        status = "config",
                        customerName = "System",
                        tier = "system",
                        notes = json.encodeToString(newConfig),
                    )
                    val legacyBody = json.encodeToString(legacyPayload)
                    val legacyPostUrl = "$restUrl/license_keys"
                    httpRequestRaw(
                        method = "POST",
                        url = legacyPostUrl,
                        headers = supabaseHeaders(method = "POST", url = legacyPostUrl, body = legacyBody),
                        body = legacyBody,
                    )
                }
            }

            // 2. Automatically delete legacy SYSTEM_CONFIG row from license_keys table
            cleanLegacyDatabase()

            newConfig
        }
    }

    suspend fun cleanLegacyDatabase(): Result<Boolean> = runCatching {
        val restUrl = supabaseRestUrl()
        val delUrl = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG"
        val response = httpRequestRaw(
            method = "DELETE",
            url = delUrl,
            headers = supabaseHeaders(method = "DELETE", url = delUrl),
            body = "",
        )
        response.status in 200..299
    }

    suspend fun fetchAnalytics(limit: Int = 100): Result<List<LicenseAnalyticsRecord>> = runCatching {
        val restUrl = supabaseRestUrl()
        val analyticsUrl = "$restUrl/license_analytics?select=*&order=id.desc&limit=$limit"
        val response = httpRequestRaw(
            method = "GET",
            url = analyticsUrl,
            headers = supabaseHeaders(method = "GET", url = analyticsUrl),
            body = "",
        )
        if (response.status in 200..299 && !response.body.startsWith("<")) {
            val list = runCatching { json.decodeFromString<List<LicenseAnalyticsRecord>>(response.body) }.getOrNull()
            if (!list.isNullOrEmpty()) {
                return@runCatching list
            }
        }

        // Fallback to synthesizing status from license registry when analytics is unavailable
        val licUrl = "$restUrl/license_keys?select=*&order=created_at.desc&limit=$limit"
        val licResponse = httpRequestRaw(
            method = "GET",
            url = licUrl,
            headers = supabaseHeaders(method = "GET", url = licUrl),
            body = "",
        )
        if (licResponse.status in 200..299 && !licResponse.body.startsWith("<")) {
            val licRecords = json.decodeFromString<List<SupabaseLicenseRecord>>(licResponse.body)
            val currentAppVersion = com.nuvio.app.core.build.AppVersionPolicy.displayVersionName
            val mapped = licRecords
                .filter { it.key != "SYSTEM_CONFIG" }
                .mapIndexed { idx, lic ->
                    val isRevoked = lic.status.equals("revoked", ignoreCase = true)
                    val shortKey = lic.key.takeLast(6)
                    LicenseAnalyticsRecord(
                        id = idx.toLong() + 1,
                        license_key = lic.key,
                        device_id = "Device-$shortKey",
                        platform = "Desktop / Mobile",
                        version = currentAppVersion,
                        event = if (isRevoked) "revoked" else "offline",
                        last_seen_at = lic.createdAt ?: "Offline",
                        created_at = lic.createdAt ?: "Offline",
                    )
                }
            return@runCatching mapped
        }

        emptyList()
    }
}
