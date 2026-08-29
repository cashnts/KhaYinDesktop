package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.player.PlayerResolutionHelper
import com.nuvio.app.features.player.VideoResolutionTier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
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
        timeoutMs: Long = 1200L,
    ): StreamItem? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        val cachedCandidates = candidates.filter { !it.isUncachedStream }
        val streamPool = if (cachedCandidates.isNotEmpty()) cachedCandidates else candidates
        if (streamPool.size == 1) return@coroutineScope streamPool.first()

        // Failsafe Racer: Probe up to 25 candidate streams in parallel
        val probePool = streamPool.take(25)
        val channel = Channel<StreamProbeResult>(capacity = probePool.size)

        val probeJobs = probePool.map { stream ->
            launch {
                val result = probeStream(stream, timeoutMs)
                channel.send(result)
            }
        }

        var bestStream: StreamItem? = null
        var bestTierRank: Int = 99
        var bestSize: Long = -1L

        val deadline = TimeSource.Monotonic.markNow()
        var receivedCount = 0

        while (receivedCount < probePool.size) {
            val remainingMs = timeoutMs - deadline.elapsedNow().inWholeMilliseconds
            if (remainingMs <= 0 && bestStream != null) {
                break
            }
            val result = withTimeoutOrNull(remainingMs.coerceAtLeast(50L).milliseconds) {
                channel.receiveCatching().getOrNull()
            } ?: break

            receivedCount++
            if (result.isLive && !result.stream.isLowQualitySource && !result.stream.isUncachedStream) {
                val tier = PlayerResolutionHelper.detectResolutionTier(result.stream)
                val size = result.stream.behaviorHints.videoSize ?: 0L

                // Instant match for high-quality instant stream (4K or 1080p confirmed cached / direct)
                if ((result.stream.isConfirmedCached || result.stream.playableDirectUrl != null) &&
                    (tier == VideoResolutionTier.UHD_4K || tier == VideoResolutionTier.QHD_2K || tier == VideoResolutionTier.FHD_1080P)
                ) {
                    probeJobs.forEach { it.cancel() }
                    return@coroutineScope result.stream
                }

                if (tier.rank < bestTierRank || (tier.rank == bestTierRank && size > bestSize)) {
                    bestStream = result.stream
                    bestTierRank = tier.rank
                    bestSize = size
                }
            }
        }

        probeJobs.forEach { it.cancel() }
        bestStream
            ?: streamPool.firstOrNull { !it.isUncachedStream && !it.isLowQualitySource }
            ?: streamPool.firstOrNull()
    }
}
