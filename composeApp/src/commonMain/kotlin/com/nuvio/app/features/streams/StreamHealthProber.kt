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

    suspend fun probeStream(stream: StreamItem, timeoutMs: Long = 1800L): StreamProbeResult {
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
            url.contains("uncached", ignoreCase = true) ||
            url.contains("not_cached", ignoreCase = true) ||
            url.contains("caching_in_progress", ignoreCase = true) ||
            url.contains("static/exceptions", ignoreCase = true)
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
            val finalUrl = resp.url
            val location = resp.headers["location"] ?: resp.headers["Location"]
            val disposition = resp.headers["content-disposition"] ?: resp.headers["Content-Disposition"]
            val contentType = resp.headers["content-type"] ?: resp.headers["Content-Type"]
            val bodyPreview = resp.body.take(512).lowercase()

            finalUrl.contains("torrent_not_downloaded", ignoreCase = true) ||
                finalUrl.contains("exceptions/", ignoreCase = true) ||
                finalUrl.contains("uncached", ignoreCase = true) ||
                finalUrl.contains("not_cached", ignoreCase = true) ||
                finalUrl.contains("caching_in_progress", ignoreCase = true) ||
                finalUrl.contains("download_in_progress", ignoreCase = true) ||
                finalUrl.contains("downloading_", ignoreCase = true) ||
                finalUrl.contains("playback_error", ignoreCase = true) ||
                finalUrl.contains("static/exceptions", ignoreCase = true) ||
                (location?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                (location?.contains("exceptions/", ignoreCase = true) == true) ||
                (location?.contains("uncached", ignoreCase = true) == true) ||
                (location?.contains("not_cached", ignoreCase = true) == true) ||
                (location?.contains("caching_in_progress", ignoreCase = true) == true) ||
                (location?.contains("downloading", ignoreCase = true) == true) ||
                (disposition?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                (disposition?.contains("exceptions/", ignoreCase = true) == true) ||
                bodyPreview.contains("torrent_not_downloaded") ||
                bodyPreview.contains("caching in progress") ||
                bodyPreview.contains("cache in progress") ||
                bodyPreview.contains("not cached") ||
                bodyPreview.contains("uncached") ||
                (contentType?.contains("text/html", ignoreCase = true) == true &&
                    (bodyPreview.contains("error") || bodyPreview.contains("exception") || bodyPreview.contains("not downloaded")))
        } ?: false

        val isSuccess = response != null &&
            !isUncachedNotice &&
            (response.status in 200..299 || response.status in 300..399)

        com.nuvio.app.core.network.NetworkQualityTracker.recordProbeLatency(latency, isSuccess)

        return StreamProbeResult(
            stream = stream,
            isLive = isSuccess,
            latencyMs = if (isSuccess) latency else Long.MAX_VALUE,
            httpStatus = response?.status ?: -1,
        )
    }

    suspend fun findFastestLivingStream(
        candidates: List<StreamItem>,
        timeoutMs: Long = 1800L,
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
        var bestScore: Long = Long.MIN_VALUE

        val deadline = TimeSource.Monotonic.markNow()
        var receivedCount = 0
        val currentNetTier = com.nuvio.app.core.network.NetworkQualityTracker.currentTier

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
                val baseScore = PlayerResolutionHelper.calculateStreamQualityScore(result.stream)
                val tier = PlayerResolutionHelper.detectResolutionTier(result.stream)

                // Network line quality adaptive scoring
                val adjustedScore = when (currentNetTier) {
                    com.nuvio.app.core.network.NetworkQualityTier.POOR -> {
                        // Bad line / high jitter: heavily penalize 4K/2K, reward 720p/1080p, heavily penalize slow response times
                        val resAdjustment = when (tier) {
                            VideoResolutionTier.UHD_4K -> -60_000L
                            VideoResolutionTier.QHD_2K -> -30_000L
                            VideoResolutionTier.FHD_1080P -> 5_000L
                            VideoResolutionTier.HD_720P -> 25_000L
                            VideoResolutionTier.SD_480P -> 15_000L
                            else -> 0L
                        }
                        val latencyPenalty = (result.latencyMs * 20L).coerceAtMost(200_000L)
                        baseScore + resAdjustment - latencyPenalty
                    }
                    com.nuvio.app.core.network.NetworkQualityTier.MODERATE -> {
                        val resAdjustment = when (tier) {
                            VideoResolutionTier.UHD_4K -> -20_000L
                            VideoResolutionTier.QHD_2K -> 0L
                            VideoResolutionTier.FHD_1080P -> 15_000L
                            VideoResolutionTier.HD_720P -> 10_000L
                            else -> 0L
                        }
                        val latencyPenalty = (result.latencyMs * 10L).coerceAtMost(100_000L)
                        baseScore + resAdjustment - latencyPenalty
                    }
                    com.nuvio.app.core.network.NetworkQualityTier.GOOD -> {
                        val latencyPenalty = (result.latencyMs * 5L).coerceAtMost(50_000L)
                        baseScore - latencyPenalty
                    }
                    com.nuvio.app.core.network.NetworkQualityTier.EXCELLENT -> {
                        val latencyPenalty = (result.latencyMs * 2L).coerceAtMost(20_000L)
                        baseScore - latencyPenalty
                    }
                }

                // Instant match check:
                // On POOR network, DO NOT instant match 4K! Instant match 720p or fast 1080p.
                // On MODERATE network, DO NOT instant match 4K! Instant match 1080p or 720p.
                // On GOOD / EXCELLENT network, 4K/2K/1080p instant match when confirmed cached.
                val canInstantMatch = when (currentNetTier) {
                    com.nuvio.app.core.network.NetworkQualityTier.POOR -> {
                        result.stream.isConfirmedCached &&
                            (tier == VideoResolutionTier.HD_720P || (tier == VideoResolutionTier.FHD_1080P && result.latencyMs < 350L))
                    }
                    com.nuvio.app.core.network.NetworkQualityTier.MODERATE -> {
                        result.stream.isConfirmedCached &&
                            (tier == VideoResolutionTier.FHD_1080P || tier == VideoResolutionTier.HD_720P || tier == VideoResolutionTier.QHD_2K)
                    }
                    com.nuvio.app.core.network.NetworkQualityTier.GOOD,
                    com.nuvio.app.core.network.NetworkQualityTier.EXCELLENT -> {
                        result.stream.isConfirmedCached &&
                            (tier == VideoResolutionTier.UHD_4K || tier == VideoResolutionTier.QHD_2K || tier == VideoResolutionTier.FHD_1080P)
                    }
                }

                if (canInstantMatch) {
                    probeJobs.forEach { it.cancel() }
                    return@coroutineScope result.stream
                }

                if (adjustedScore > bestScore) {
                    bestStream = result.stream
                    bestScore = adjustedScore
                }
            }
        }

        probeJobs.forEach { it.cancel() }
        bestStream
            ?: streamPool.firstOrNull { it.isConfirmedCached }
            ?: streamPool.firstOrNull { !it.isUncachedStream && !it.isLowQualitySource }
            ?: streamPool.firstOrNull()
    }
}
