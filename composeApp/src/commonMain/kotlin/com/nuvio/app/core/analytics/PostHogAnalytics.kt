package com.nuvio.app.core.analytics

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

object PostHogAnalytics {
    private val log = Logger.withTag("PostHogAnalytics")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    const val API_KEY = "phc_BbmKpZksuoFxSHLj5PS8tbZttzcwkFU82AsQdyLiTsrd"
    const val HOST = "https://us.i.posthog.com"
    private const val CAPTURE_ENDPOINT = "$HOST/capture/"

    private var currentSessionId: String = generateSessionId()
    private var currentDistinctId: String = generateAnonymousId()
    private var defaultPlatform: String = "Desktop"
    private var appVersion: String = ""
    private val superProperties = mutableMapOf<String, Any>()

    private val isAnalyticsDisabled: Boolean
        get() = AppFeaturePolicy.isAdminClient

    fun initialize(
        platform: String = "Desktop",
        version: String = "",
        distinctId: String? = null,
        deviceType: String = "desktop",
        osName: String = "Unknown",
        osVersion: String = "Unknown",
        deviceModel: String = "Unknown",
        deviceBrand: String = "Unknown",
        serviceName: String = "khayin-desktop"
    ) {
        if (isAnalyticsDisabled) {
            log.d { "Analytics disabled for Admin Client." }
            return
        }
        defaultPlatform = platform
        appVersion = version
        if (!distinctId.isNullOrBlank()) {
            currentDistinctId = distinctId
        }
        superProperties["platform"] = platform
        superProperties["device_type"] = deviceType
        if (version.isNotBlank()) {
            superProperties["app_version"] = version
        }
        if (osName.isNotBlank() && osName != "Unknown") {
            superProperties["os_name"] = osName
        }
        if (osVersion.isNotBlank() && osVersion != "Unknown") {
            superProperties["os_version"] = osVersion
        }
        if (deviceModel.isNotBlank() && deviceModel != "Unknown") {
            superProperties["device_model"] = deviceModel
        }
        if (deviceBrand.isNotBlank() && deviceBrand != "Unknown") {
            superProperties["device_brand"] = deviceBrand
        }

        PostHogLogger.initialize(
            service = serviceName,
            version = version,
            platformName = platform,
            deviceTypeName = deviceType,
            os = osName,
            osVer = osVersion,
            model = deviceModel,
            brand = deviceBrand
        )

        PostHogTracer.initialize(
            service = serviceName,
            version = version,
            platformName = platform,
            deviceTypeName = deviceType,
            os = osName,
            osVer = osVersion,
            model = deviceModel,
            brand = deviceBrand
        )

        log.i { "Initialized PostHog for $platform ($deviceType, version=$version, distinctId=$currentDistinctId, sessionId=$currentSessionId)" }
    }

    fun getDistinctId(): String = currentDistinctId
    fun getSessionId(): String = currentSessionId

    fun rotateSession(): String {
        currentSessionId = generateSessionId()
        return currentSessionId
    }

    fun identify(
        distinctId: String,
        userProperties: Map<String, Any>? = null
    ) {
        if (isAnalyticsDisabled || distinctId.isBlank()) return
        val anonId = currentDistinctId
        currentDistinctId = distinctId
        capture(
            event = "\$identify",
            properties = buildMap {
                put("distinct_id", distinctId)
                if (anonId.startsWith("anon_") && anonId != distinctId) {
                    put("\$anon_distinct_id", anonId)
                }
                if (userProperties != null) {
                    put("\$set", userProperties)
                }
            }
        )
    }

    fun d(tag: String, message: String, properties: Map<String, Any>? = null) {
        log(level = "DEBUG", tag = tag, message = message, properties = properties)
    }

    fun i(tag: String, message: String, properties: Map<String, Any>? = null) {
        log(level = "INFO", tag = tag, message = message, properties = properties)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, properties: Map<String, Any>? = null) {
        log(level = "WARN", tag = tag, message = message, throwable = throwable, properties = properties)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, properties: Map<String, Any>? = null) {
        log(level = "ERROR", tag = tag, message = message, throwable = throwable, properties = properties)
    }

    fun log(
        level: String = "INFO",
        tag: String = "App",
        message: String,
        throwable: Throwable? = null,
        properties: Map<String, Any>? = null
    ) {
        // Forward to PostHog Logs (OTLP)
        PostHogLogger.log(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            attributes = properties
        )

        // If error/fatal, also report into PostHog Error Tracking ($exception)
        if (level.equals("ERROR", ignoreCase = true) || level.equals("FATAL", ignoreCase = true) || throwable != null) {
            val exProps = buildMap<String, Any> {
                put("\$exception_message", throwable?.message ?: message)
                put("\$exception_type", throwable?.let { it::class.simpleName } ?: "ApplicationError")
                put("tag", tag)
                put("\$exception_handled", !level.equals("FATAL", ignoreCase = true))
                if (throwable != null) {
                    put("\$exception_stack_trace_raw", throwable.stackTraceToString().take(6000))
                }
                if (properties != null) {
                    putAll(properties)
                }
            }
            capture(event = "\$exception", properties = exProps)
        }
    }

    fun captureException(
        throwable: Throwable,
        tag: String = "Error",
        isUnhandled: Boolean = false,
        properties: Map<String, Any>? = null
    ) {
        val exProps = buildMap<String, Any> {
            put("tag", tag)
            put("\$exception_type", throwable::class.simpleName ?: "Exception")
            put("\$exception_message", throwable.message ?: (throwable::class.simpleName ?: "Exception"))
            put("\$exception_stack_trace_raw", throwable.stackTraceToString().take(8000))
            put("\$exception_handled", !isUnhandled)
            if (properties != null) putAll(properties)
        }
        capture(event = "\$exception", properties = exProps)

        PostHogLogger.log(
            level = if (isUnhandled) "FATAL" else "ERROR",
            tag = tag,
            message = "${throwable::class.simpleName}: ${throwable.message}",
            throwable = throwable,
            attributes = properties
        )
    }

    fun screen(
        screenName: String,
        properties: Map<String, Any>? = null
    ) {
        capture(
            event = "\$screen",
            properties = buildMap {
                put("\$screen_name", screenName)
                if (properties != null) {
                    putAll(properties)
                }
            }
        )
    }

    fun trackPlaybackStarted(
        mediaTitle: String,
        contentType: String? = null,
        videoId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        isP2p: Boolean = false,
        streamName: String? = null,
        addonName: String? = null,
    ) {
        capture(
            event = "playback_started",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (contentType != null) put("content_type", contentType)
                if (videoId != null) put("video_id", videoId)
                if (season != null && season > 0) put("season", season)
                if (episode != null && episode > 0) put("episode", episode)
                put("duration_ms", durationMs)
                put("position_ms", positionMs)
                put("is_p2p", isP2p)
                if (streamName != null) put("stream_name", streamName)
                if (addonName != null) put("addon_name", addonName)
            }
        )
    }

    fun trackPlaybackPaused(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        progressPercent: Float = 0f,
    ) {
        capture(
            event = "playback_paused",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
            }
        )
    }

    fun trackPlaybackResumed(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
    ) {
        capture(
            event = "playback_resumed",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
            }
        )
    }

    fun trackPlaybackFinished(
        mediaTitle: String,
        videoId: String? = null,
        durationMs: Long = 0L,
        progressPercent: Float = 100f,
    ) {
        capture(
            event = "playback_finished",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
                put("completed", true)
            }
        )
    }

    fun trackPlaybackStopped(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        progressPercent: Float = 0f,
        completed: Boolean = false,
    ) {
        capture(
            event = if (completed) "playback_finished" else "playback_stopped",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
                put("completed", completed)
            }
        )
    }

    fun trackPlaybackFailed(
        mediaTitle: String,
        videoId: String? = null,
        errorMessage: String,
        sourceUrl: String? = null,
    ) {
        capture(
            event = "playback_failed",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("error_message", errorMessage)
                if (sourceUrl != null) put("source_url", sourceUrl.take(300))
            }
        )
        log(
            level = "ERROR",
            tag = "Player",
            message = "Playback failed for '$mediaTitle': $errorMessage",
            properties = mapOf("video_id" to (videoId ?: ""), "source_url" to (sourceUrl ?: ""))
        )
    }

    fun trackStreamFetchStarted(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        addonCount: Int = 0,
        pluginCount: Int = 0,
    ) {
        capture(
            event = "stream_fetch_started",
            properties = buildMap {
                put("media_type", type)
                put("video_id", videoId)
                if (season != null && season > 0) put("season", season)
                if (episode != null && episode > 0) put("episode", episode)
                put("addon_count", addonCount)
                put("plugin_count", pluginCount)
            }
        )
    }

    fun trackStreamFetchCompleted(
        type: String,
        videoId: String,
        totalStreams: Int,
        groupCount: Int,
        durationMs: Long? = null,
        isEmpty: Boolean = false,
        emptyReason: String? = null,
    ) {
        capture(
            event = "stream_fetch_completed",
            properties = buildMap {
                put("media_type", type)
                put("video_id", videoId)
                put("total_streams", totalStreams)
                put("group_count", groupCount)
                if (durationMs != null) put("duration_ms", durationMs)
                put("is_empty", isEmpty)
                if (emptyReason != null) put("empty_reason", emptyReason)
            }
        )
    }

    fun trackStreamSelected(
        mediaTitle: String,
        videoId: String? = null,
        streamName: String,
        addonName: String? = null,
        resolution: String? = null,
        isDebrid: Boolean = false,
        isP2p: Boolean = false,
    ) {
        capture(
            event = "stream_selected",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("stream_name", streamName)
                if (addonName != null) put("addon_name", addonName)
                if (resolution != null) put("resolution", resolution)
                put("is_debrid", isDebrid)
                put("is_p2p", isP2p)
            }
        )
    }

    fun trackSearch(
        query: String,
        totalResults: Int,
        sectionCount: Int = 0,
        hasError: Boolean = false,
    ) {
        capture(
            event = "search_performed",
            properties = mapOf(
                "query" to query,
                "total_results" to totalResults,
                "section_count" to sectionCount,
                "has_error" to hasError
            )
        )
    }

    fun trackAddonInstalled(
        addonName: String,
        addonId: String,
        manifestUrl: String,
    ) {
        capture(
            event = "addon_installed",
            properties = mapOf(
                "addon_name" to addonName,
                "addon_id" to addonId,
                "manifest_url" to manifestUrl
            )
        )
    }

    fun trackAddonUninstalled(
        manifestUrl: String,
    ) {
        capture(
            event = "addon_uninstalled",
            properties = mapOf(
                "manifest_url" to manifestUrl
            )
        )
    }

    fun trackProfileSwitched(
        profileIndex: Int,
        profileName: String,
        isKid: Boolean = false,
    ) {
        capture(
            event = "profile_switched",
            properties = mapOf(
                "profile_index" to profileIndex,
                "profile_name" to profileName,
                "is_kid" to isKid
            )
        )
    }

    fun trackSubtitleError(
        errorType: String,
        errorMessage: String,
        subtitleId: String? = null,
        subtitleUrl: String? = null,
        language: String? = null,
        addonName: String? = null,
        mimeType: String? = null,
        throwable: Throwable? = null,
        extra: Map<String, Any>? = null,
    ) {
        val props = buildMap<String, Any> {
            put("error_type", errorType)
            put("error_message", errorMessage)
            if (subtitleId != null) put("subtitle_id", subtitleId)
            if (subtitleUrl != null) put("subtitle_url", subtitleUrl.take(300))
            if (language != null) put("language", language)
            if (addonName != null) put("addon_name", addonName)
            if (mimeType != null) put("mime_type", mimeType)
            if (extra != null) putAll(extra)
        }
        capture(event = "subtitle_error", properties = props)
        log(
            level = "ERROR",
            tag = "Subtitle",
            message = "Subtitle error [$errorType]: $errorMessage (lang=$language, addon=$addonName, id=$subtitleId)",
            throwable = throwable,
            properties = props
        )
    }

    fun capture(
        event: String,
        properties: Map<String, Any>? = null
    ) {
        if (isAnalyticsDisabled) return
        scope.launch {
            try {
                val mergedProps = mutableMapOf<String, Any>()
                mergedProps.putAll(superProperties)
                mergedProps["distinct_id"] = currentDistinctId
                mergedProps["\$session_id"] = currentSessionId
                mergedProps["\$window_id"] = currentSessionId
                mergedProps["session_id"] = currentSessionId
                mergedProps["\$lib"] = "posthog-kmp-desktop"
                if (properties != null) {
                    mergedProps.putAll(properties)
                }

                val payloadObject = buildJsonObject {
                    put("api_key", API_KEY)
                    put("event", event)
                    put("distinct_id", currentDistinctId)
                    put("properties", anyMapToJsonObject(mergedProps))
                }

                val bodyString = payloadObject.toString()
                val headers = mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to "Nuvio-Desktop/$appVersion"
                )

                val response = httpRequestRaw(
                    method = "POST",
                    url = CAPTURE_ENDPOINT,
                    headers = headers,
                    body = bodyString
                )

                if (response.status !in 200..299) {
                    log.w { "PostHog capture HTTP ${response.status}: ${response.body}" }
                }
            } catch (e: Throwable) {
                log.w { "PostHog capture error for $event: ${e.message}" }
            }
        }
    }

    fun reset() {
        currentDistinctId = generateAnonymousId()
        currentSessionId = generateSessionId()
    }

    private fun generateSessionId(): String {
        val hex = "0123456789abcdef"
        fun randHex(len: Int) = (1..len).map { hex[Random.nextInt(hex.length)] }.joinToString("")
        return "${randHex(8)}-${randHex(4)}-4${randHex(3)}-${randHex(4)}-${randHex(12)}"
    }

    private fun generateAnonymousId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "anon_" + (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun anyMapToJsonObject(map: Map<String, Any>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, anyToJsonElement(value))
            }
        }
    }

    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> {
                buildJsonObject {
                    value.forEach { (k, v) ->
                        if (k != null) {
                            put(k.toString(), anyToJsonElement(v))
                        }
                    }
                }
            }
            is List<*> -> {
                JsonArray(value.map { anyToJsonElement(it) })
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}
