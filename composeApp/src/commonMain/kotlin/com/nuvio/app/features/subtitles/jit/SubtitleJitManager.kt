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
import kotlin.math.abs

object SubtitleJitManager {
    private val log = Logger.withTag("SubtitleJitManager")
    private const val BASE_URL = "https://stream.khayin.net"
    private const val HEARTBEAT_INTERVAL_MS = 12_000L
    // Fast poll while translation is in-flight or actively progressing
    private const val STATUS_POLL_INTERVAL_IN_FLIGHT_MS = 3_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null
    private var seekJob: Job? = null

    private var activeSessionId: String? = null
    private var activeMediaId: String? = null
    private var activeType: String = "movie"
    private var activeSubtitleUrl: String? = null
    private var currentOnNewCuesAvailable: ((url: String) -> Unit)? = null

    private var lastCurrentTimeSec: Double = 0.0
    private var lastDurationSec: Double = 0.0
    private var lastIsPlaying: Boolean = false
    private var wasPlayingSent: Boolean = false
    private var lastLoadedCompletedSections: Int = -1
    private var lastLoadedProgressPercent: Int = -1

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
        val effectiveId = extractEffectiveId(subtitleUrl, mediaId.trim())
        val sessionKey = "$cleanType:$effectiveId:$subtitleUrl"

        if (activeSessionId == sessionKey) {
            currentOnNewCuesAvailable = onNewCuesAvailable
            return
        }

        stopSession()

        activeSessionId = sessionKey
        activeMediaId = effectiveId
        activeType = cleanType
        activeSubtitleUrl = subtitleUrl
        currentOnNewCuesAvailable = onNewCuesAvailable
        lastLoadedCompletedSections = -1
        lastLoadedProgressPercent = -1

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

        // 3. Status polling & progressive reload loop
        ensurePollingActive(effectiveId, sessionKey)
    }

    private fun ensurePollingActive(effectiveId: String, sessionKey: String) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            log.d { "Starting JIT status polling for $effectiveId" }
            while (isActive && activeSessionId == sessionKey) {
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
                        val total = body.totalSections ?: 1
                        val progress = body.progressPercent ?: 0
                        log.d {
                            "JIT status for $effectiveId: complete=${body.isComplete} inFlight=${body.inFlight} " +
                                "sections=$completed/$total progress=$progress%"
                        }

                        val sectionAdvanced = completed > lastLoadedCompletedSections
                        val progressAdvanced = lastLoadedProgressPercent >= 0 && progress >= lastLoadedProgressPercent + 10
                        val firstLoad = lastLoadedCompletedSections == -1

                        if (sectionAdvanced || progressAdvanced || firstLoad) {
                            lastLoadedCompletedSections = completed
                            lastLoadedProgressPercent = progress
                            log.i { "New translated content available ($completed/$total, progress=$progress%) for $effectiveId, triggering reload" }
                            activeSubtitleUrl?.let { url -> currentOnNewCuesAvailable?.invoke(url) }
                        }

                        // Stop polling only when all sections are fully completed and no longer in-flight
                        if (body.isComplete && completed >= total && body.inFlight != true) {
                            log.i { "JIT translation fully complete ($completed/$total) for $effectiveId" }
                            break
                        }
                    }
                } catch (e: Exception) {
                    log.w { "JIT status check error: ${e.message}" }
                }
                delay(STATUS_POLL_INTERVAL_IN_FLIGHT_MS)
            }
        }
    }

    fun updatePlaybackProgress(
        currentTimeSec: Double,
        isPlaying: Boolean,
        durationSec: Double,
        isLoading: Boolean = false
    ) {
        val prevTime = lastCurrentTimeSec
        lastCurrentTimeSec = currentTimeSec
        lastDurationSec = durationSec

        // If media is still loading or buffering, do not misinterpret as user pausing
        if (isLoading) {
            return
        }

        // Seek detection: if currentTime jumped by more than 8 seconds
        val timeJump = abs(currentTimeSec - prevTime)
        val isSeek = prevTime > 0.0 && timeJump > 8.0

        if (isSeek && activeMediaId != null) {
            log.i { "Playback seek detected: ${prevTime.toLong()}s -> ${currentTimeSec.toLong()}s (jump=${timeJump.toLong()}s). Prioritizing JIT translation." }
            handlePlaybackSeek(currentTimeSec, isPlaying)
        }

        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            if (!isPlaying && wasPlayingSent) {
                // User intentionally paused: send a single heartbeat with isPlaying = false immediately
                scope.launch {
                    sendHeartbeat(isPlaying = false)
                }
                wasPlayingSent = false
            }
        }
    }

    private fun handlePlaybackSeek(seekTimeSec: Double, isPlaying: Boolean) {
        val mediaId = activeMediaId ?: return
        val sessionKey = activeSessionId ?: return

        seekJob?.cancel()
        seekJob = scope.launch {
            // 1. Immediately send heartbeat with the seek position so backend session updates right away
            sendHeartbeat(isPlaying = isPlaying, currentTime = seekTimeSec)

            // 2. Call /api/translation/seek to prioritize the target 25-min section on the backend
            try {
                val seekReq = SubtitleSeekRequestDto(
                    id = mediaId,
                    currentTime = seekTimeSec
                )
                val resp = withTimeoutOrNull(3500L) {
                    httpRequestRaw(
                        method = "POST",
                        url = "$BASE_URL/api/translation/seek",
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "Accept" to "application/json",
                            "User-Agent" to "KhaYin/Desktop"
                        ),
                        body = json.encodeToString(seekReq)
                    )
                }

                if (resp != null && resp.status in 200..299 && resp.body.isNotBlank()) {
                    val seekRes = json.decodeFromString<SubtitleSeekResponseDto>(resp.body)
                    log.i { "Seek API result for $mediaId at ${seekTimeSec.toLong()}s: section=${seekRes.targetSection}, isReady=${seekRes.isReady}" }

                    if (seekRes.isReady) {
                        // Target section is already translated on the server: reload immediately!
                        log.i { "Target section for seek is already ready! Reloading subtitle immediately." }
                        activeSubtitleUrl?.let { url -> currentOnNewCuesAvailable?.invoke(url) }
                    }
                }
            } catch (e: Exception) {
                log.w { "Failed to notify seek API: ${e.message}" }
            }

            // 3. Ensure status polling is active to catch new section cues as soon as translation finishes
            ensurePollingActive(mediaId, sessionKey)
        }
    }

    private suspend fun sendHeartbeat(isPlaying: Boolean, currentTime: Double = lastCurrentTimeSec) {
        val mediaId = activeMediaId ?: return
        try {
            val req = SubtitleHeartbeatRequestDto(
                slug = "anonymous",
                type = activeType,
                id = mediaId,
                currentTime = currentTime,
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
        seekJob?.cancel()
        seekJob = null

        activeSessionId = null
        activeMediaId = null
        activeSubtitleUrl = null
        currentOnNewCuesAvailable = null
        wasPlayingSent = false
        lastLoadedCompletedSections = -1
        lastLoadedProgressPercent = -1

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
