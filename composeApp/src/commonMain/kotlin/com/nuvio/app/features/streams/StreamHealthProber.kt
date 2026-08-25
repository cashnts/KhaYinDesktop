package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.player.PlayerResolutionHelper
import com.nuvio.app.features.player.VideoResolutionTier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class StreamProbeResult(
    val stream: StreamItem,
    val isLive: Boolean,
    val latencyMs: Long,
    val httpStatus: Int,
)

object StreamHealthProber {

    suspend fun probeStream(stream: StreamItem, timeoutMs: Long = 1200L): StreamProbeResult {
        if (stream.isUncachedStream) {
            return StreamProbeResult(
                stream = stream,
                isLive = false,
                latencyMs = Long.MAX_VALUE,
                httpStatus = 404,
            )
        }

        val url = stream.playableDirectUrl ?: stream.url ?: return StreamProbeResult(
            stream = stream,
            isLive = false,
            latencyMs = Long.MAX_VALUE,
            httpStatus = -1,
        )

        if (url.contains("torrent_not_downloaded", ignoreCase = true) ||
            url.contains("exceptions/", ignoreCase = true) ||
            url.contains("uncached", ignoreCase = true)
        ) {
            return StreamProbeResult(
                stream = stream,
                isLive = false,
                latencyMs = Long.MAX_VALUE,
                httpStatus = 404,
            )
        }

        // Torrent/P2P magnet links are resolved via Debrid/engine
        if (url.startsWith("magnet:", ignoreCase = true) || url.startsWith("torrent:", ignoreCase = true)) {
            val isCached = stream.isDirectDebridStream || stream.isCachedDebridTorrentStream
            return StreamProbeResult(
                stream = stream,
                isLive = isCached,
                latencyMs = if (isCached) 150L else 5000L,
                httpStatus = if (isCached) 200 else -1,
            )
        }

        val headers = buildMap<String, String> {
            put("Range", "bytes=0-1024")
            stream.behaviorHints.proxyHeaders?.request?.forEach { (k, v) -> put(k, v) }
        }

        val mark = TimeSource.Monotonic.markNow()
        val response = withTimeoutOrNull(timeoutMs.milliseconds) {
            runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers,
                    body = "",
                    followRedirects = true,
                    maxResponseBodyBytes = 1024,
                )
            }.getOrNull()
        }
        val latency = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)

        val isUncachedNotice = response?.let { resp ->
            val location = resp.headers["location"] ?: resp.headers["Location"]
            val disposition = resp.headers["content-disposition"] ?: resp.headers["Content-Disposition"]
            val bodyPreview = resp.body.take(256).lowercase()
            (location?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                (location?.contains("exceptions/", ignoreCase = true) == true) ||
                (location?.contains("uncached", ignoreCase = true) == true) ||
                (disposition?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                bodyPreview.contains("torrent_not_downloaded") ||
                bodyPreview.contains("caching in progress")
        } ?: false

        val isSuccess = response != null &&
            !isUncachedNotice &&
            (response.status in 200..299 || response.status in 300..399)

        return StreamProbeResult(
            stream = stream,
            isLive = isSuccess,
            latencyMs = if (isSuccess) latency else Long.MAX_VALUE,
            httpStatus = response?.status ?: -1,
        )
    }

    suspend fun findFastestLivingStream(
        candidates: List<StreamItem>,
        timeoutMs: Long = 1500L,
    ): StreamItem? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        val cachedCandidates = candidates.filter { !it.isUncachedStream }
        val streamPool = if (cachedCandidates.isNotEmpty()) cachedCandidates else candidates
        if (streamPool.size == 1) return@coroutineScope streamPool.first()

        val topCandidates = streamPool.take(6)
        val probeJobs = topCandidates.map { stream ->
            async { probeStream(stream, timeoutMs) }
        }

        val results = probeJobs.awaitAll()
        val liveResults = results.filter { it.isLive }

        if (liveResults.isNotEmpty()) {
            val best = liveResults.minWithOrNull(
                compareBy<StreamProbeResult> {
                    val tier = PlayerResolutionHelper.detectResolutionTier(it.stream)
                    tier.rank
                }.thenBy {
                    it.latencyMs
                }.thenByDescending {
                    it.stream.behaviorHints.videoSize ?: 0L
                }
            )
            return@coroutineScope best?.stream
        }

        streamPool.firstOrNull()
    }
}
