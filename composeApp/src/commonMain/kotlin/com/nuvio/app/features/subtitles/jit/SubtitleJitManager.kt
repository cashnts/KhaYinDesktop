package com.nuvio.app.features.subtitles.jit

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SubtitleJitManager {
    private val log = Logger.withTag("SubtitleJitManager")
    private const val BASE_URL = "https://stream.khayin.net"
    private const val HEARTBEAT_INTERVAL_MS = 12_000L
    // Fast poll while translation is in-flight; stops once complete
    private const val STATUS_POLL_INTERVAL_IN_FLIGHT_MS = 4_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null

    private var activeSessionId: String? = null
    private var activeMediaId: String? = null
    private var activeType: String = "movie"
    private var activeSubtitleUrl: String? = null

    private var lastCurrentTimeSec: Double = 0.0
    private var lastDurationSec: Double = 0.0
    private var lastIsPlaying: Boolean = false
    private var wasPlayingSent: Boolean = false

    fun isJitSubtitle(url: String?, addonName: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("stream.khayin.net", ignoreCase = true) ||
            addonName?.contains("KhaYin", ignoreCase = true) == true
    }

    /**
     * Extract the real media/episode ID from the subtitle URL.
     * e.g. https://stream.khayin.net/subtitles/vtt/series/tt1234567:1:1.vtt?extra=...
     * -> "tt1234567:1:1"
     * Falls back to [mediaId] if the URL filename cannot be parsed.
     */
    private fun extractEffectiveId(subtitleUrl: String, mediaId: String): String {
        return try {
            subtitleUrl
                .substringBefore("?")
                .substringAfterLast("/")
                .removeSuffix(".vtt")
                .removeSuffix(".srt")
                .removeSuffix(".json")
                .takeIf { it.isNotBlank() } ?: mediaId
        } catch (_: Exception) {
            mediaId
        }
    }

    fun startSession(
        mediaId: String,
        type: String,
        subtitleUrl: String,
        onNewCuesAvailable: ((url: String) -> Unit)? = null
    ) {
        val cleanType = if (type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)) {
            "series"
        } else {
            "movie"
        }
        // Use the ID embedded in the subtitle URL (e.g. tt1234567:1:1 for an episode)
        // so status checks hit the right endpoint even when parentMetaId is the show ID only.
        val effectiveId = extractEffectiveId(subtitleUrl, mediaId.trim())
        val sessionKey = "$cleanType:$effectiveId:$subtitleUrl"

        if (activeSessionId == sessionKey) {
            return
        }

        stopSession()

        activeSessionId = sessionKey
        activeMediaId = effectiveId
        activeType = cleanType
        activeSubtitleUrl = subtitleUrl

        log.d { "Starting JIT session for $cleanType $effectiveId ($subtitleUrl)" }

        // 1. Send an immediate heartbeat so the backend starts/continues translation right away
        scope.launch {
            sendHeartbeat(isPlaying = true)
            wasPlayingSent = true
            lastIsPlaying = true
        }

        // 2. Heartbeat loop (every 12s while playing). First beat already sent above.
        heartbeatJob = scope.launch {
            delay(HEARTBEAT_INTERVAL_MS)
            while (isActive && activeSessionId == sessionKey) {
                if (lastIsPlaying) {
                    sendHeartbeat(isPlaying = true)
                    wasPlayingSent = true
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        // 3. Status polling & progressive reload loop – starts immediately (no initial delay)
        pollingJob = scope.launch {
            var lastCompletedSections = -1
            var isFinished = false

            while (isActive && activeSessionId == sessionKey && !isFinished) {
                try {
                    val statusUrl = "$BASE_URL/api/translation/status/$effectiveId"
                    val resp = withTimeoutOrNull(4000L) {
                        httpRequestRaw(
                            method = "GET",
                            url = statusUrl,
                            headers = mapOf(
                                "Accept" to "application/json",
                                "User-Agent" to "KhaYin/Desktop"
                            ),
                            body = ""
                        )
                    }

                    if (resp != null && resp.status in 200..299 && resp.body.isNotBlank()) {
                        val body = json.decodeFromString<SubtitleTranslationStatusDto>(resp.body)
                        val completed = body.completedSections ?: 0
                        log.d {
                            "JIT status for $effectiveId: complete=${body.isComplete} inFlight=${body.inFlight} " +
                                "sections=$completed/${body.totalSections} progress=${body.progressPercent}%"
                        }

                        if (body.isComplete) {
                            isFinished = true
                            onNewCuesAvailable?.invoke(subtitleUrl)
                            log.d { "JIT translation complete for $effectiveId" }
                            break
                        } else if (completed > lastCompletedSections || lastCompletedSections == -1) {
                            lastCompletedSections = completed
                            // Trigger reload whenever new sections arrive (even at 0 to load initial cues)
                            onNewCuesAvailable?.invoke(subtitleUrl)
                        }
                    }
                } catch (e: Exception) {
                    log.w { "JIT status check error: ${e.message}" }
                }
                // Fast poll while in-flight; loop exits via break when complete
                delay(STATUS_POLL_INTERVAL_IN_FLIGHT_MS)
            }
        }
    }

    fun updatePlaybackProgress(
        currentTimeSec: Double,
        isPlaying: Boolean,
        durationSec: Double
    ) {
        lastCurrentTimeSec = currentTimeSec
        lastDurationSec = durationSec

        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            if (!isPlaying && wasPlayingSent) {
                // User paused: send a single heartbeat with isPlaying = false immediately
                scope.launch {
                    sendHeartbeat(isPlaying = false)
                }
                wasPlayingSent = false
            }
        }
    }

    private suspend fun sendHeartbeat(isPlaying: Boolean) {
        val mediaId = activeMediaId ?: return
        try {
            val req = SubtitleHeartbeatRequestDto(
                slug = "anonymous",
                type = activeType,
                id = mediaId,
                currentTime = lastCurrentTimeSec,
                isPlaying = isPlaying,
                duration = if (lastDurationSec > 0.0) lastDurationSec else null
            )
            val jsonPayload = json.encodeToString(req)
            withTimeoutOrNull(3500L) {
                httpRequestRaw(
                    method = "POST",
                    url = "$BASE_URL/api/playback/heartbeat",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                        "User-Agent" to "KhaYin/Desktop"
                    ),
                    body = jsonPayload
                )
            }
        } catch (e: Exception) {
            log.w { "Failed to send heartbeat: ${e.message}" }
        }
    }

    fun stopSession() {
        if (activeSessionId == null) return
        log.d { "Stopping JIT session: $activeSessionId" }

        val hadActiveHeartbeat = wasPlayingSent
        val prevMediaId = activeMediaId
        val prevType = activeType
        val prevCurrentTime = lastCurrentTimeSec
        val prevDuration = lastDurationSec

        heartbeatJob?.cancel()
        heartbeatJob = null
        pollingJob?.cancel()
        pollingJob = null

        activeSessionId = null
        activeMediaId = null
        activeSubtitleUrl = null
        wasPlayingSent = false

        if (hadActiveHeartbeat && prevMediaId != null) {
            scope.launch {
                try {
                    val req = SubtitleHeartbeatRequestDto(
                        slug = "anonymous",
                        type = prevType,
                        id = prevMediaId,
                        currentTime = prevCurrentTime,
                        isPlaying = false,
                        duration = if (prevDuration > 0.0) prevDuration else null
                    )
                    val jsonPayload = json.encodeToString(req)
                    withTimeoutOrNull(3000L) {
                        httpRequestRaw(
                            method = "POST",
                            url = "$BASE_URL/api/playback/heartbeat",
                            headers = mapOf(
                                "Content-Type" to "application/json",
                                "Accept" to "application/json",
                                "User-Agent" to "KhaYin/Desktop"
                            ),
                            body = jsonPayload
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
