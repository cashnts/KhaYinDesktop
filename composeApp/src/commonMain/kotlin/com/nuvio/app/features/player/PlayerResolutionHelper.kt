package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import kotlin.math.abs
import kotlin.math.min

enum class VideoResolutionTier(
    val label: String,
    val shortLabel: String,
    val rank: Int,
) {
    UHD_4K("4K Ultra HD (2160p)", "4K", 1),
    QHD_2K("2K Quad HD (1440p)", "2K", 2),
    FHD_1080P("1080p Full HD", "1080p", 3),
    HD_720P("720p HD", "720p", 4),
    SD_480P("480p / SD", "SD", 5),
    UNKNOWN("Auto / Other", "Auto", 6),
}

data class PlayerResolutionOption(
    val tier: VideoResolutionTier,
    val bestStream: StreamItem,
    val isActive: Boolean,
)

object PlayerResolutionHelper {

    private val regex4K = Regex("""(?i)\b(2160p?|4k|uhd|ultra[\s._-]?hd)\b""")
    private val regex2K = Regex("""(?i)\b(1440p?|2k|qhd|quad[\s._-]?hd)\b""")
    private val regex1080p = Regex("""(?i)\b(1080p?|1080i|fhd|full[\s._-]?hd)\b""")
    private val regex720p = Regex("""(?i)\b(720p?|hd)\b""")
    private val regex480p = Regex("""(?i)\b(480p?|576p?|sd)\b""")

    fun detectResolutionTierFromText(text: String): VideoResolutionTier {
        if (text.isBlank()) return VideoResolutionTier.UNKNOWN
        val clean = text.lowercase()
        return when {
            regex4K.containsMatchIn(clean) -> VideoResolutionTier.UHD_4K
            regex2K.containsMatchIn(clean) -> VideoResolutionTier.QHD_2K
            regex1080p.containsMatchIn(clean) -> VideoResolutionTier.FHD_1080P
            regex720p.containsMatchIn(clean) && !isFalsePositiveHd(clean) -> VideoResolutionTier.HD_720P
            regex480p.containsMatchIn(clean) -> VideoResolutionTier.SD_480P
            else -> VideoResolutionTier.UNKNOWN
        }
    }

    private fun isFalsePositiveHd(lower: String): Boolean {
        if (lower.contains("720")) return false
        val words = lower.split(Regex("[^a-z0-9]+"))
        return words.none { it == "hd" }
    }

    fun detectResolutionTier(stream: StreamItem): VideoResolutionTier {
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.streamLabel).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(stream.behaviorHints.filename.orEmpty())
        }
        return detectResolutionTierFromText(text)
    }

    fun calculateStreamQualityScore(stream: StreamItem, targetTier: VideoResolutionTier? = null): Long {
        var score = 0L

        // 1. Extreme cached vs uncached separation
        if (stream.isUncachedStream) {
            score -= 10_000_000L
        } else if (stream.isConfirmedCached || stream.isDirectDebridStream || stream.isCachedDebridTorrentStream) {
            score += 1_000_000L
        }

        // 2. Heavy penalty for low quality source (CAM, TS, HDCAM)
        if (stream.isLowQualitySource) {
            score -= 5_000_000L
        }

        // 3. Playable direct URL preference
        if (stream.playableDirectUrl != null) {
            score += 100_000L
        }

        // 4. Resolution matching score
        val tier = detectResolutionTier(stream)
        if (targetTier != null) {
            if (tier == targetTier) {
                score += 500_000L
            } else {
                score -= abs(tier.rank - targetTier.rank) * 100_000L
            }
        } else {
            score += when (tier) {
                VideoResolutionTier.UHD_4K -> 40_000L
                VideoResolutionTier.QHD_2K -> 30_000L
                VideoResolutionTier.FHD_1080P -> 20_000L
                VideoResolutionTier.HD_720P -> 10_000L
                VideoResolutionTier.SD_480P -> 2_000L
                VideoResolutionTier.UNKNOWN -> 0L
            }
        }

        // 5. Video Codec preference
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.streamLabel).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(stream.behaviorHints.filename.orEmpty())
        }.lowercase()

        when {
            text.contains("av1") -> score += 15_000L
            text.contains("hevc") || text.contains("x265") || text.contains("h.265") || text.contains("h265") -> score += 12_000L
            text.contains("x264") || text.contains("h.264") || text.contains("h264") || text.contains("avc") -> score += 8_000L
        }

        // 6. Audio feature bonus
        if (text.contains("atmos") || text.contains("truehd") || text.contains("dts-hd") || text.contains("dts:x")) {
            score += 8_000L
        } else if (text.contains("5.1") || text.contains("7.1") || text.contains("ddp") || text.contains("eac3") || text.contains("ac3")) {
            score += 4_000L
        }

        // 7. Seeders count bonus (for P2P torrents)
        val seeders = stream.seedersCount ?: 0
        score += min(seeders, 100) * 50L

        // 8. Balanced File Size (Sweet spot sizing)
        val sizeBytes = stream.behaviorHints.videoSize ?: 0L
        if (sizeBytes > 0L) {
            val sizeGb = sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            score += when (tier) {
                VideoResolutionTier.UHD_4K -> when {
                    sizeGb in 6.0..35.0 -> 10_000L
                    sizeGb in 3.0..50.0 -> 6_000L
                    sizeGb > 50.0 -> 2_000L
                    else -> 1_000L
                }
                VideoResolutionTier.FHD_1080P, VideoResolutionTier.QHD_2K -> when {
                    sizeGb in 1.5..12.0 -> 10_000L
                    sizeGb in 0.8..20.0 -> 6_000L
                    sizeGb > 20.0 -> 3_000L
                    else -> 1_000L
                }
                VideoResolutionTier.HD_720P -> when {
                    sizeGb in 0.6..4.0 -> 8_000L
                    sizeGb > 4.0 -> 3_000L
                    else -> 1_000L
                }
                else -> 1_000L
            }
        }

        return score
    }

    fun buildResolutionOptions(
        streams: List<StreamItem>,
        currentStreamUrl: String?,
        currentStreamName: String?,
    ): List<PlayerResolutionOption> {
        if (streams.isEmpty()) return emptyList()

        val grouped = streams.groupBy { detectResolutionTier(it) }

        return VideoResolutionTier.entries
            .filter { it != VideoResolutionTier.UNKNOWN }
            .mapNotNull { tier ->
                val tierStreams = grouped[tier] ?: return@mapNotNull null
                val best = pickBestStreamForTier(tierStreams) ?: return@mapNotNull null
                val isActive = (currentStreamUrl != null && best.playableDirectUrl == currentStreamUrl) ||
                    (currentStreamName != null && best.streamLabel == currentStreamName)
                PlayerResolutionOption(
                    tier = tier,
                    bestStream = best,
                    isActive = isActive,
                )
            }
    }

    fun pickBestStreamForTier(streams: List<StreamItem>): StreamItem? {
        if (streams.isEmpty()) return null
        return streams.maxByOrNull { calculateStreamQualityScore(it) }
    }

    fun filterBestStreams(streams: List<StreamItem>): List<StreamItem> {
        if (streams.isEmpty()) return emptyList()
        val grouped = streams.groupBy { detectResolutionTier(it) }
        val results = VideoResolutionTier.entries
            .filter { it != VideoResolutionTier.UNKNOWN }
            .mapNotNull { tier ->
                val tierStreams = grouped[tier] ?: return@mapNotNull null
                pickBestStreamForTier(tierStreams)
            }
        return if (results.isNotEmpty()) {
            results
        } else {
            listOfNotNull(pickBestStreamForTier(streams))
        }
    }

    fun filterBestStreamsWithGroup(streamsWithGroup: List<Pair<String, StreamItem>>): List<Pair<String, StreamItem>> {
        if (streamsWithGroup.isEmpty()) return emptyList()
        val grouped = streamsWithGroup.groupBy { (_, stream) -> detectResolutionTier(stream) }
        val results = VideoResolutionTier.entries
            .filter { it != VideoResolutionTier.UNKNOWN }
            .mapNotNull { tier ->
                val tierList = grouped[tier] ?: return@mapNotNull null
                tierList.maxByOrNull { (_, stream) -> calculateStreamQualityScore(stream) }
            }
        return if (results.isNotEmpty()) {
            results
        } else {
            listOfNotNull(streamsWithGroup.maxByOrNull { (_, stream) -> calculateStreamQualityScore(stream) })
        }
    }

    fun formatStreamVideoSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 0.1) {
            val rounded = (gb * 10).toLong() / 10.0
            "${rounded} GB"
        } else {
            val mb = (bytes.toDouble() / (1024.0 * 1024.0)).toLong()
            "${mb} MB"
        }
    }
}

