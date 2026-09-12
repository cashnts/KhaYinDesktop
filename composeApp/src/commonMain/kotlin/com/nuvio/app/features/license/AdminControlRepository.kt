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
import com.nuvio.app.core.analytics.PostHogAnalytics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class PostHogFeatureFlag(
    val id: Long,
    val key: String,
    val name: String = "",
    val active: Boolean = false,
)

@Serializable
data class PresetCatalogConfig(
    val key: String,
    val addonId: String = "",
    val addonName: String = "",
    val catalogId: String = "",
    val type: String = "",
    val defaultTitle: String = "",
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = false,
    val order: Int = 0,
)

@Serializable
data class SystemServiceConfig(
    // 1. Operational & Emergency Controls
    val maintenanceMode: Boolean = false,
    val maintenanceNotice: String = "",
    val streamingDisabled: Boolean = false,
    val streamingDisabledNotice: String = "",

    // 2. Global Announcements / Live Notice Banner
    val broadcastTitle: String = "",
    val broadcastMessage: String = "",
    val broadcastSeverity: String = "INFO", // "INFO", "WARNING", "CRITICAL", "PROMO"
    val broadcastTimestamp: Long = 0L,
    val broadcastDismissable: Boolean = true,
    val broadcastActionUrl: String = "",
    val broadcastActionLabel: String = "",

    // 3. Over-The-Air Addon & Catalog Management
    val presetAddons: List<String> = emptyList(),
    val disabledAddons: List<String> = emptyList(), // Instant remote blacklist for broken/malicious addons
    val addonMetadata: Map<String, AddonMetadataOverride> = emptyMap(), // Admin-overridden names/descriptions per addon URL
    val presetCatalogs: List<PresetCatalogConfig> = emptyList(), // Global home catalog layout for all users
    val heroCarouselEnabled: Boolean = true,
    val showCatalogType: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
)

/** Per-addon metadata overrides set by the admin and broadcast to all clients. */
@Serializable
data class AddonMetadataOverride(
    val name: String? = null,
    val description: String? = null,
)

private val DEFAULT_PH_KEY_PARTS = listOf("phx_", "JzxYddY8UjrVtn7hr43Z", "BoEiMykSAQkz2XfVqRKPmXoQsRLA")
val DEFAULT_POSTHOG_PERSONAL_API_KEY: String get() = DEFAULT_PH_KEY_PARTS.joinToString("")

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
    val customer_name: String? = null,
    val location: String? = null,
    val log_level: String? = null,
    val log_message: String? = null,
    val session_id: String? = null,
    val media_title: String? = null,
    val stream_name: String? = null,
    val addon_name: String? = null,
    val search_query: String? = null,
    val duration_ms: Long? = null,
    val position_ms: Long? = null,
    val progress_percent: Float? = null,
    val source: String? = "PostHog",
)

data class PostHogSessionRecord(
    val sessionId: String,
    val licenseKey: String,
    val customerName: String? = null,
    val deviceId: String,
    val platform: String,
    val version: String,
    val location: String?,
    val startTime: String,
    val lastSeenTime: String,
    val durationFormatted: String,
    val totalEvents: Int,
    val recentActivity: String,
    val mediaPlayed: List<String>,
    val searches: List<String>,
    val isLive: Boolean,
    val hasErrors: Boolean,
    val activePlaybackTitle: String? = null,
    val activePlaybackProgress: Float? = null,
    val records: List<LicenseAnalyticsRecord>,
)

object AdminControlRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val _config = MutableStateFlow(SystemServiceConfig())
    val config: StateFlow<SystemServiceConfig> = _config.asStateFlow()

    private val _featureFlags = MutableStateFlow<List<PostHogFeatureFlag>>(emptyList())
    val featureFlags: StateFlow<List<PostHogFeatureFlag>> = _featureFlags.asStateFlow()

    private val _dismissedBroadcastTimestamp = MutableStateFlow(0L)
    val dismissedBroadcastTimestamp: StateFlow<Long> = _dismissedBroadcastTimestamp.asStateFlow()

    private var customPostHogApiKey: String = ""

    fun setPostHogApiKey(key: String) {
        customPostHogApiKey = key.trim()
    }

    fun getEffectivePostHogApiKey(): String {
        if (customPostHogApiKey.isNotBlank()) return customPostHogApiKey
        return DEFAULT_POSTHOG_PERSONAL_API_KEY
    }

    fun isAddonBlocked(manifestOrTransportUrl: String): Boolean {
        if (manifestOrTransportUrl.isBlank()) return false
        val normalized = manifestOrTransportUrl.trim().lowercase()
        return _config.value.disabledAddons.any { disabled ->
            val d = disabled.trim().lowercase()
            d.isNotBlank() && (normalized.contains(d) || d.contains(normalized))
        }
    }

    fun getString(key: String, defaultValue: String = ""): String = defaultValue

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = defaultValue

    fun getInt(key: String, defaultValue: Int = 0): Int = defaultValue

    init {
        refreshDismissedTimestamp()
    }

    fun dismissBroadcast(timestamp: Long) {
        val target = if (timestamp > 0L) timestamp else _config.value.broadcastTimestamp
        _dismissedBroadcastTimestamp.value = target
        LicenseStorage.saveDismissedBroadcastTimestamp(target)
    }

    fun refreshDismissedTimestamp() {
        val stored = LicenseStorage.loadDismissedBroadcastTimestamp()
        if (stored == 0L) {
            val now = com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs()
            _dismissedBroadcastTimestamp.value = now
            LicenseStorage.saveDismissedBroadcastTimestamp(now)
        } else if (stored > _dismissedBroadcastTimestamp.value) {
            _dismissedBroadcastTimestamp.value = stored
        }
    }

    suspend fun fetchConfig(): SystemServiceConfig = fetchRemoteConfig().getOrElse { _config.value }

    suspend fun updateConfig(newConfig: SystemServiceConfig): Result<SystemServiceConfig> = saveRemoteConfig(newConfig)

    fun startPolling(intervalSeconds: Long = 30) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (true) {
                fetchRemoteConfig()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
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


@Serializable
private data class AppSettingsDbRecord(
    val id: String = "global",
    val config: kotlinx.serialization.json.JsonElement? = null,
    val updated_at: String? = null,
)

    suspend fun fetchRemoteConfig(): Result<SystemServiceConfig> = runCatching {
        val restUrl = supabaseRestUrl()

        // 1. Primary: query dedicated app_settings table (row id = 'global')
        val appSettingsUrl = "$restUrl/app_settings?id=eq.global&select=*"
        val appSettingsResponse = httpRequestRaw(
            method = "GET",
            url = appSettingsUrl,
            headers = supabaseHeaders(method = "GET", url = appSettingsUrl),
            body = "",
        )

        if (appSettingsResponse.status in 200..299 && !appSettingsResponse.body.startsWith("<")) {
            val records = runCatching { json.decodeFromString<List<AppSettingsDbRecord>>(appSettingsResponse.body) }.getOrNull()
            val configElement = records?.firstOrNull()?.config
            if (configElement != null) {
                val parsed = runCatching { json.decodeFromJsonElement<SystemServiceConfig>(configElement) }.getOrNull()
                if (parsed != null) {
                    _config.value = parsed
                    // Automatically sync pushed addons & catalogs on client device
                    com.nuvio.app.features.addons.AddonRepository.syncRemotePresetAddons(parsed.presetAddons, parsed.disabledAddons)
                    com.nuvio.app.features.home.HomeCatalogSettingsRepository.applyPresetCatalogs(
                        presetCatalogs = parsed.presetCatalogs,
                        heroCarouselEnabled = parsed.heroCarouselEnabled,
                        showCatalogType = parsed.showCatalogType,
                        hideUnreleasedContent = parsed.hideUnreleasedContent,
                    )
                    return@runCatching parsed
                }
            }
        }

        // 2. Fallback: query legacy license_keys for SYSTEM_CONFIG row
        val licUrl = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG&select=*"
        val licResponse = httpRequestRaw(
            method = "GET",
            url = licUrl,
            headers = supabaseHeaders(method = "GET", url = licUrl),
            body = "",
        )

        if (licResponse.status in 200..299 && !licResponse.body.startsWith("<")) {
            val licRecords = json.decodeFromString<List<SupabaseLicenseRecord>>(licResponse.body)
            val configNote = licRecords.firstOrNull()?.notes
            if (!configNote.isNullOrBlank()) {
                val parsed = json.decodeFromString<SystemServiceConfig>(configNote)
                _config.value = parsed
                // Automatically sync pushed addons & catalogs on client device
                com.nuvio.app.features.addons.AddonRepository.syncRemotePresetAddons(parsed.presetAddons, parsed.disabledAddons)
                com.nuvio.app.features.home.HomeCatalogSettingsRepository.applyPresetCatalogs(
                    presetCatalogs = parsed.presetCatalogs,
                    heroCarouselEnabled = parsed.heroCarouselEnabled,
                    showCatalogType = parsed.showCatalogType,
                    hideUnreleasedContent = parsed.hideUnreleasedContent,
                )
                return@runCatching parsed
            }
        }

        _config.value
    }

    suspend fun saveRemoteConfig(newConfig: SystemServiceConfig): Result<SystemServiceConfig> = runCatching {
        val restUrl = supabaseRestUrl()
        val configJson = json.encodeToString(newConfig)
        val configElement = json.encodeToJsonElement(newConfig)

        // 1. Primary: Upsert / update app_settings table
        val appSettingsPatchUrl = "$restUrl/app_settings?id=eq.global"
        val patchAppSettingsPayload = buildJsonObject {
            put("config", configElement)
        }
        val patchAppSettingsBody = patchAppSettingsPayload.toString()
        val appSettingsPatchResp = httpRequestRaw(
            method = "PATCH",
            url = appSettingsPatchUrl,
            headers = supabaseHeaders(method = "PATCH", url = appSettingsPatchUrl, body = patchAppSettingsBody),
            body = patchAppSettingsBody,
        )

        val appSettingsPatchedCount = runCatching {
            json.decodeFromString<List<AppSettingsDbRecord>>(appSettingsPatchResp.body).size
        }.getOrDefault(0)

        if (appSettingsPatchResp.status !in 200..299 || appSettingsPatchedCount == 0) {
            val appSettingsPostUrl = "$restUrl/app_settings"
            val postAppSettingsPayload = buildJsonObject {
                put("id", "global")
                put("config", configElement)
            }
            val postAppSettingsBody = postAppSettingsPayload.toString()
            httpRequestRaw(
                method = "POST",
                url = appSettingsPostUrl,
                headers = supabaseHeaders(method = "POST", url = appSettingsPostUrl, body = postAppSettingsBody),
                body = postAppSettingsBody,
            )
        }

        // 2. Secondary fallback: Also try to update legacy SYSTEM_CONFIG in license_keys
        runCatching {
            val patchLicUrl = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG"
            val patchLicPayload = buildJsonObject {
                put("notes", configJson)
                put("customer_name", "System Config")
                put("tier", "system")
                put("status", "config")
            }
            val patchLicBody = patchLicPayload.toString()
            val patchLicResp = httpRequestRaw(
                method = "PATCH",
                url = patchLicUrl,
                headers = supabaseHeaders(method = "PATCH", url = patchLicUrl, body = patchLicBody),
                body = patchLicBody,
            )
            val licPatchedCount = runCatching { json.decodeFromString<List<SupabaseLicenseRecord>>(patchLicResp.body).size }.getOrDefault(0)
            if (patchLicResp.status !in 200..299 || licPatchedCount == 0) {
                val postLicUrl = "$restUrl/license_keys"
                httpRequestRaw(
                    method = "POST",
                    url = postLicUrl,
                    headers = supabaseHeaders(method = "POST", url = postLicUrl, body = patchLicBody),
                    body = patchLicBody,
                )
            }
        }

        _config.value = newConfig
        com.nuvio.app.features.addons.AddonRepository.syncRemotePresetAddons(newConfig.presetAddons, newConfig.disabledAddons)
        com.nuvio.app.features.home.HomeCatalogSettingsRepository.applyPresetCatalogs(
            presetCatalogs = newConfig.presetCatalogs,
            heroCarouselEnabled = newConfig.heroCarouselEnabled,
            showCatalogType = newConfig.showCatalogType,
            hideUnreleasedContent = newConfig.hideUnreleasedContent,
        )
        newConfig
    }

    private fun parsePropertiesElement(element: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonObject? {
        if (element == null || element is kotlinx.serialization.json.JsonNull) return null
        if (element is kotlinx.serialization.json.JsonObject) return element
        if (element is kotlinx.serialization.json.JsonPrimitive) {
            val content = element.content.trim()
            if (content.startsWith("{")) {
                return runCatching { json.parseToJsonElement(content) as? kotlinx.serialization.json.JsonObject }.getOrNull()
            }
        }
        return null
    }

    private fun buildRecordFromProps(
        idx: Int,
        event: String,
        distinctId: String,
        timestamp: String,
        props: kotlinx.serialization.json.JsonObject?,
        personProps: kotlinx.serialization.json.JsonObject?,
    ): LicenseAnalyticsRecord {
        val platform = (props?.get("platform") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("os_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$os") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$lib") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: "App"

        val version = (props?.get("app_version") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("version") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$app_version") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: ""

        val deviceId = (props?.get("device_id") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("device_model") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$device_id") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: ""

        val city = (props?.get("\$geoip_city_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (personProps?.get("\$geoip_city_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val country = (props?.get("\$geoip_country_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (personProps?.get("\$geoip_country_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val location = listOfNotNull(city, country).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }

        val customerName = (personProps?.get("customer_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("customer_name") as? kotlinx.serialization.json.JsonPrimitive)?.content

        val logLevel = (props?.get("\$level") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("level") as? kotlinx.serialization.json.JsonPrimitive)?.content

        val logMessage = (props?.get("\$message") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$exception_message") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("error_message") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$screen_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("screen_name") as? kotlinx.serialization.json.JsonPrimitive)?.content

        val sessionId = (props?.get("\$session_id") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("session_id") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("\$window_id") as? kotlinx.serialization.json.JsonPrimitive)?.content

        val mediaTitle = (props?.get("media_title") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("title") as? kotlinx.serialization.json.JsonPrimitive)?.content

        val streamName = (props?.get("stream_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val addonName = (props?.get("addon_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val searchQuery = (props?.get("query") as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (props?.get("search_query") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val durationMs = (props?.get("duration_ms") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
        val positionMs = (props?.get("position_ms") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
        val progressPercent = (props?.get("progress_percent") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toFloat()

        val licenseKey = if (distinctId.isNotBlank() && !distinctId.startsWith("anon_")) {
            distinctId
        } else {
            (props?.get("license_key") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: distinctId
        }

        return LicenseAnalyticsRecord(
            id = idx.toLong() + 1,
            license_key = licenseKey,
            device_id = deviceId.ifBlank { location ?: "Device" },
            platform = platform,
            version = version,
            event = event,
            last_seen_at = timestamp,
            created_at = timestamp,
            customer_name = customerName,
            location = location,
            log_level = logLevel,
            log_message = logMessage,
            session_id = sessionId,
            media_title = mediaTitle,
            stream_name = streamName,
            addon_name = addonName,
            search_query = searchQuery,
            duration_ms = durationMs,
            position_ms = positionMs,
            progress_percent = progressPercent,
            source = "PostHog",
        )
    }

    private fun extractJsonString(element: kotlinx.serialization.json.JsonElement?): String? {
        if (element == null || element is kotlinx.serialization.json.JsonNull) return null
        if (element is kotlinx.serialization.json.JsonPrimitive) {
            val c = element.content
            if (c.equals("null", ignoreCase = true) || c.isBlank()) return null
            return c
        }
        return element.toString()
    }

    private fun extractJsonDouble(element: kotlinx.serialization.json.JsonElement?): Double? {
        if (element == null || element is kotlinx.serialization.json.JsonNull) return null
        if (element is kotlinx.serialization.json.JsonPrimitive) {
            return element.content.toDoubleOrNull()
        }
        return null
    }

    suspend fun fetchPostHogAnalytics(apiKey: String? = null, limit: Int = 200): Result<List<LicenseAnalyticsRecord>> = runCatching {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: getEffectivePostHogApiKey()
        if (key.isBlank()) return@runCatching emptyList()

        val postHogQueryUrl = "https://aa.khayin.dev/api/projects/583868/query/"
        val hogQlQuery = """
            SELECT
                event,
                distinct_id,
                timestamp,
                properties.platform,
                properties.app_version,
                properties.device_id,
                properties.device_model,
                properties.`${'$'}geoip_city_name`,
                properties.`${'$'}geoip_country_name`,
                person.properties.customer_name,
                properties.level,
                properties.message,
                properties.`${'$'}exception_message`,
                properties.session_id,
                properties.media_title,
                properties.stream_name,
                properties.addon_name,
                properties.search_query,
                properties.duration_ms,
                properties.position_ms,
                properties.progress_percent
            FROM events
            ORDER BY timestamp DESC
            LIMIT $limit
        """.trimIndent()

        val queryBody = json.encodeToString(
            mapOf(
                "query" to mapOf(
                    "kind" to "HogQLQuery",
                    "query" to hogQlQuery,
                )
            )
        )

        val response = httpRequestRaw(
            method = "POST",
            url = postHogQueryUrl,
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json",
            ),
            body = queryBody,
            maxResponseBodyBytes = 10 * 1024 * 1024,
        )

        val records = mutableListOf<LicenseAnalyticsRecord>()

        if (response.status in 200..299 && !response.body.startsWith("<")) {
            val root = runCatching {
                json.parseToJsonElement(response.body) as? kotlinx.serialization.json.JsonObject
            }.getOrNull()

            val resultsArray = (root?.get("results") as? kotlinx.serialization.json.JsonArray)
            if (resultsArray != null) {
                resultsArray.forEachIndexed { idx, item ->
                    val row = item as? kotlinx.serialization.json.JsonArray ?: return@forEachIndexed
                    if (row.size >= 15) {
                        // Fast column-projected format
                        val event = extractJsonString(row.getOrNull(0)).orEmpty()
                        val distinctId = extractJsonString(row.getOrNull(1)).orEmpty()
                        val timestamp = extractJsonString(row.getOrNull(2)).orEmpty()
                        val platform = extractJsonString(row.getOrNull(3)) ?: "Desktop / Mobile"
                        val appVersion = extractJsonString(row.getOrNull(4)).orEmpty()
                        val deviceId = extractJsonString(row.getOrNull(5)) ?: extractJsonString(row.getOrNull(6)) ?: "Device"
                        val city = extractJsonString(row.getOrNull(7))
                        val country = extractJsonString(row.getOrNull(8))
                        val location = listOfNotNull(city, country).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
                        val customerName = extractJsonString(row.getOrNull(9))
                        val logLevel = extractJsonString(row.getOrNull(10))
                        val message = extractJsonString(row.getOrNull(11)) ?: extractJsonString(row.getOrNull(12))
                        val sessionId = extractJsonString(row.getOrNull(13))
                        val mediaTitle = extractJsonString(row.getOrNull(14))
                        val streamName = extractJsonString(row.getOrNull(15))
                        val addonName = extractJsonString(row.getOrNull(16))
                        val searchQuery = extractJsonString(row.getOrNull(17))
                        val durationMs = extractJsonDouble(row.getOrNull(18))?.toLong()
                        val positionMs = extractJsonDouble(row.getOrNull(19))?.toLong()
                        val progressPercent = extractJsonDouble(row.getOrNull(20))?.toFloat()

                        val licenseKey = if (distinctId.isNotBlank() && !distinctId.startsWith("anon_")) {
                            distinctId
                        } else {
                            customerName ?: distinctId
                        }

                        records.add(
                            LicenseAnalyticsRecord(
                                id = idx.toLong() + 1,
                                license_key = licenseKey,
                                device_id = deviceId.ifBlank { location ?: "Device" },
                                platform = platform,
                                version = appVersion,
                                event = event,
                                last_seen_at = timestamp,
                                created_at = timestamp,
                                customer_name = customerName,
                                location = location,
                                log_level = logLevel,
                                log_message = message,
                                session_id = sessionId,
                                media_title = mediaTitle,
                                stream_name = streamName,
                                addon_name = addonName,
                                search_query = searchQuery,
                                duration_ms = durationMs,
                                position_ms = positionMs,
                                progress_percent = progressPercent,
                                source = "PostHog",
                            )
                        )
                    } else {
                        // Legacy 5-column format
                        val event = (row.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                        val distinctId = (row.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                        val timestamp = (row.getOrNull(2) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                        val props = parsePropertiesElement(row.getOrNull(3))
                        val personProps = parsePropertiesElement(row.getOrNull(4))

                        records.add(
                            buildRecordFromProps(
                                idx = idx,
                                event = event,
                                distinctId = distinctId,
                                timestamp = timestamp,
                                props = props,
                                personProps = personProps,
                            )
                        )
                    }
                }
            }
        }

        // Fallback to legacy endpoint if query returned empty or failed
        if (records.isEmpty()) {
            val fallbackUrl = "https://aa.khayin.dev/api/projects/583868/events/?limit=$limit&orderBy=%5B%22-timestamp%22%5D"
            val fallbackResp = httpRequestRaw(
                method = "GET",
                url = fallbackUrl,
                headers = mapOf(
                    "Authorization" to "Bearer $key",
                    "Content-Type" to "application/json",
                ),
                body = "",
                maxResponseBodyBytes = 10 * 1024 * 1024,
            )
            if (fallbackResp.status in 200..299 && !fallbackResp.body.startsWith("<")) {
                val root = runCatching {
                    json.parseToJsonElement(fallbackResp.body) as? kotlinx.serialization.json.JsonObject
                }.getOrNull()
                val resultsArray = (root?.get("results") as? kotlinx.serialization.json.JsonArray)
                resultsArray?.forEachIndexed { idx, item ->
                    val obj = item as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
                    val event = (obj["event"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    val distinctId = (obj["distinct_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    val timestamp = (obj["timestamp"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    val props = obj["properties"] as? kotlinx.serialization.json.JsonObject
                    val person = obj["person"] as? kotlinx.serialization.json.JsonObject
                    val personProps = person?.get("properties") as? kotlinx.serialization.json.JsonObject

                    val record = buildRecordFromProps(
                        idx = idx,
                        event = event,
                        distinctId = distinctId,
                        timestamp = timestamp,
                        props = props,
                        personProps = personProps,
                    )
                    records.add(record)
                }
            }
        }

        records.sortedByDescending { parseIsoEpochMs(it.created_at ?: it.last_seen_at) }
    }

    fun parseIsoEpochMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.trim()
        cleaned.toLongOrNull()?.let { num ->
            return if (num < 100_000_000_000L) num * 1000L else num
        }
        val directInstant = runCatching { kotlinx.datetime.Instant.parse(cleaned).toEpochMilliseconds() }.getOrNull()
        if (directInstant != null) return directInstant

        var normalized = cleaned.replace(" ", "T")
        val hasOffset = normalized.length > 10 && (
            normalized.substring(10).contains("+") ||
            normalized.substring(10).contains("-") ||
            normalized.endsWith("Z")
        )
        if (!hasOffset) {
            normalized += "Z"
        }
        return runCatching {
            kotlinx.datetime.Instant.parse(normalized).toEpochMilliseconds()
        }.getOrElse { 0L }
    }

    suspend fun fetchAnalytics(limit: Int = 300): Result<List<LicenseAnalyticsRecord>> =
        fetchPostHogAnalytics(limit = limit)

    suspend fun fetchFeatureFlags(apiKey: String? = null): Result<List<PostHogFeatureFlag>> = runCatching {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: getEffectivePostHogApiKey()
        if (key.isBlank()) return@runCatching emptyList()

        val url = "https://aa.khayin.dev/api/projects/583868/feature_flags/?limit=100"
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json",
            ),
            body = "",
            maxResponseBodyBytes = 2 * 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Failed to fetch feature flags (${response.status})")
        }

        val parsedJson = json.parseToJsonElement(response.body).jsonObject
        val results = parsedJson["results"]?.jsonArray ?: JsonArray(emptyList())
        val parsed = results.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val flagKey = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            val active = obj["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val deleted = obj["deleted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (deleted) null else PostHogFeatureFlag(id = id, key = flagKey, name = name, active = active)
        }.sortedBy { it.key }
        _featureFlags.value = parsed
        parsed
    }

    suspend fun updateFeatureFlagStatus(flagId: Long, active: Boolean, apiKey: String? = null): Result<Unit> = runCatching {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: getEffectivePostHogApiKey()
        val url = "https://aa.khayin.dev/api/projects/583868/feature_flags/$flagId/"
        val payload = buildJsonObject {
            put("active", active)
        }
        val response = httpRequestRaw(
            method = "PATCH",
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json",
            ),
            body = payload.toString(),
            maxResponseBodyBytes = 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Failed to update flag (${response.status})")
        }
        _featureFlags.value = _featureFlags.value.map {
            if (it.id == flagId) it.copy(active = active) else it
        }
        PostHogAnalytics.reloadFeatureFlags()
    }

    suspend fun createFeatureFlag(key: String, name: String = "", active: Boolean = true, apiKey: String? = null): Result<PostHogFeatureFlag> = runCatching {
        val apiKeyToUse = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: getEffectivePostHogApiKey()
        val url = "https://aa.khayin.dev/api/projects/583868/feature_flags/"
        val payload = buildJsonObject {
            put("key", key.trim())
            put("name", name.trim())
            put("active", active)
            put("filters", buildJsonObject {
                put("groups", buildJsonArray {
                    add(buildJsonObject {
                        put("rollout_percentage", 100)
                    })
                })
            })
        }
        val response = httpRequestRaw(
            method = "POST",
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $apiKeyToUse",
                "Content-Type" to "application/json",
            ),
            body = payload.toString(),
            maxResponseBodyBytes = 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Failed to create flag (${response.status})")
        }
        val obj = json.parseToJsonElement(response.body).jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val created = PostHogFeatureFlag(id = id, key = key.trim(), name = name.trim(), active = active)
        _featureFlags.value = (_featureFlags.value.filterNot { it.id == id || it.key == key.trim() } + created).sortedBy { it.key }
        PostHogAnalytics.reloadFeatureFlags()
        created
    }

    fun groupSessions(
        records: List<LicenseAnalyticsRecord>,
        nowEpochMs: Long = com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs()
    ): List<PostHogSessionRecord> {
        if (records.isEmpty()) return emptyList()

        val sessionGroups = records.groupBy { record ->
            record.session_id?.takeIf { it.isNotBlank() }
                ?: "session_${record.license_key ?: "anon"}_${record.device_id ?: "dev"}_${record.created_at?.take(10) ?: "today"}"
        }

        return sessionGroups.map { (sessionId, sessionRecords) ->
            val sortedSessionRecords = sessionRecords.sortedBy { parseIsoEpochMs(it.created_at ?: it.last_seen_at) }
            val first = sortedSessionRecords.first()
            val latest = sortedSessionRecords.last()

            val licenseKey = sessionRecords.firstNotNullOfOrNull { it.license_key?.takeIf { k -> k.isNotBlank() && !k.startsWith("anon_") } }
                ?: sessionRecords.firstNotNullOfOrNull { it.license_key?.takeIf { k -> k.isNotBlank() } }
                ?: "Anonymous"
            val customerName = sessionRecords.firstNotNullOfOrNull { it.customer_name?.takeIf { c -> c.isNotBlank() } }
            val deviceId = sessionRecords.firstNotNullOfOrNull { it.device_id?.takeIf { d -> d.isNotBlank() } } ?: "Device"
            val platform = sessionRecords.firstNotNullOfOrNull { it.platform?.takeIf { p -> p.isNotBlank() } } ?: "Desktop / Mobile"
            val version = sessionRecords.firstNotNullOfOrNull { it.version?.takeIf { v -> v.isNotBlank() } } ?: ""
            val location = sessionRecords.firstNotNullOfOrNull { it.location?.takeIf { l -> l.isNotBlank() } }

            val mediaPlayed = sessionRecords.mapNotNull { it.media_title?.takeIf { m -> m.isNotBlank() } }.distinct()
            val searches = sessionRecords.mapNotNull { it.search_query?.takeIf { q -> q.isNotBlank() } }.distinct()

            val hasErrors = sessionRecords.any { r ->
                val evt = r.event.orEmpty().lowercase()
                evt == "\$exception" || evt.contains("error") || evt == "playback_failed" || r.log_level?.equals("error", ignoreCase = true) == true
            }

            val startIso = first.created_at ?: ""
            val lastSeenIso = latest.last_seen_at ?: latest.created_at ?: ""

            val firstEpoch = parseIsoEpochMs(startIso)
            val lastEpoch = parseIsoEpochMs(lastSeenIso).takeIf { it > 0L } ?: firstEpoch

            val durationSeconds = if (lastEpoch >= firstEpoch && firstEpoch > 0L) (lastEpoch - firstEpoch) / 1000L else 0L
            val durationFormatted = when {
                durationSeconds < 60 -> "${durationSeconds}s"
                durationSeconds < 3600 -> "${durationSeconds / 60}m ${durationSeconds % 60}s"
                else -> "${durationSeconds / 3600}h ${(durationSeconds % 3600) / 60}m"
            }

            // A session is LIVE if an event was received within the last 180 seconds (with clock skew buffer)
            val diffMs = nowEpochMs - lastEpoch
            val isLive = (lastEpoch > 0L) && (diffMs <= 180 * 1000L)

            // Find the most recent meaningful action (ignoring routine heartbeats)
            val latestAction = sortedSessionRecords.lastOrNull {
                val evt = it.event.orEmpty().lowercase()
                evt != "heartbeat" && !evt.startsWith("\$identify") && !evt.startsWith("\$set") && !evt.startsWith("\$create_alias")
            } ?: latest

            var activePlaybackTitle: String? = null
            var activePlaybackProgress: Float? = null

            val recentActivity = when {
                latestAction.event?.startsWith("playback_started", ignoreCase = true) == true ||
                latestAction.event?.startsWith("playback_resumed", ignoreCase = true) == true -> {
                    val title = latestAction.media_title ?: mediaPlayed.lastOrNull() ?: "Media"
                    activePlaybackTitle = title
                    activePlaybackProgress = latestAction.progress_percent
                    val progressStr = latestAction.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                    if (isLive) "Watching: $title$progressStr" else "Watched: $title"
                }
                latestAction.event?.startsWith("playback_paused", ignoreCase = true) == true -> {
                    val title = latestAction.media_title ?: mediaPlayed.lastOrNull() ?: "Media"
                    activePlaybackTitle = title
                    activePlaybackProgress = latestAction.progress_percent
                    val progressStr = latestAction.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                    "Paused: $title$progressStr"
                }
                latestAction.event?.startsWith("playback_stopped", ignoreCase = true) == true ||
                latestAction.event?.startsWith("playback_finished", ignoreCase = true) == true -> {
                    val title = latestAction.media_title ?: mediaPlayed.lastOrNull()
                    if (title != null) "Finished: $title" else "Stopped Playback"
                }
                latestAction.event?.startsWith("playback_failed", ignoreCase = true) == true -> "Playback Failed"
                latestAction.event?.startsWith("search", ignoreCase = true) == true -> {
                    val q = latestAction.search_query ?: searches.lastOrNull()
                    if (q != null) "Searched: \"$q\"" else "Searching"
                }
                latestAction.event?.startsWith("stream_fetch", ignoreCase = true) == true -> {
                    val title = latestAction.media_title ?: "Streams"
                    "Browsing Streams ($title)"
                }
                latestAction.event?.equals("profile_switched", ignoreCase = true) == true -> "Switched Profile"
                latestAction.event?.equals("addon_installed", ignoreCase = true) == true ||
                latestAction.event?.equals("addon_uninstalled", ignoreCase = true) == true -> "Managing Addons"
                latestAction.event?.equals("\$screen", ignoreCase = true) == true -> {
                    val scr = latestAction.log_message?.takeIf { it.isNotBlank() } ?: "App"
                    "Navigating ($scr)"
                }
                isLive -> "Active in App"
                else -> "Session Ended"
            }

            PostHogSessionRecord(
                sessionId = sessionId,
                licenseKey = licenseKey,
                customerName = customerName,
                deviceId = deviceId,
                platform = platform,
                version = version,
                location = location,
                startTime = startIso,
                lastSeenTime = lastSeenIso,
                durationFormatted = durationFormatted,
                totalEvents = sessionRecords.size,
                recentActivity = recentActivity,
                mediaPlayed = mediaPlayed,
                searches = searches,
                isLive = isLive,
                hasErrors = hasErrors,
                activePlaybackTitle = activePlaybackTitle,
                activePlaybackProgress = activePlaybackProgress,
                records = sortedSessionRecords.reversed(),
            )
        }.sortedWith(compareByDescending<PostHogSessionRecord> { it.isLive }.thenByDescending { parseIsoEpochMs(it.lastSeenTime) })
    }
}
