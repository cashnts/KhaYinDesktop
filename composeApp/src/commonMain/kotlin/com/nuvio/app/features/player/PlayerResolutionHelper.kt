package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem

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

    fun detectResolutionTierFromText(text: String): VideoResolutionTier {
        val lower = text.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> VideoResolutionTier.UHD_4K
            lower.contains("1440") || lower.contains("2k") || lower.contains("qhd") -> VideoResolutionTier.QHD_2K
            lower.contains("1080") || lower.contains("fhd") -> VideoResolutionTier.FHD_1080P
            lower.contains("720") || lower.contains("hd") -> VideoResolutionTier.HD_720P
            lower.contains("480") || lower.contains("576") || lower.contains("sd") -> VideoResolutionTier.SD_480P
            else -> VideoResolutionTier.UNKNOWN
        }
    }

    fun detectResolutionTier(stream: StreamItem): VideoResolutionTier {
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.streamLabel).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(stream.behaviorHints.filename.orEmpty())
        }
        return detectResolutionTierFromText(text)
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
        val validStreams = streams.filter { !it.isUncachedStream }
        val pool = if (validStreams.isNotEmpty()) validStreams else streams
        return pool.maxWithOrNull(
            compareBy<StreamItem> { !it.isUncachedStream }
                .thenBy { !it.isLowQualitySource }
                .thenBy { it.isDirectDebridStream || it.isCachedDebridTorrentStream }
                .thenBy { it.playableDirectUrl != null }
                .thenBy { it.behaviorHints.videoSize ?: 0L }
        )
    }

    fun filterBestStreams(streams: List<StreamItem>): List<StreamItem> {
        if (streams.isEmpty()) return emptyList()
        val hasCachedStreams = streams.any { !it.isUncachedStream }
        val sourceStreams = if (hasCachedStreams) streams.filter { !it.isUncachedStream } else streams
        val grouped = sourceStreams.groupBy { detectResolutionTier(it) }
        val results = VideoResolutionTier.entries
            .filter { it != VideoResolutionTier.UNKNOWN }
            .mapNotNull { tier ->
                val tierStreams = grouped[tier] ?: return@mapNotNull null
                pickBestStreamForTier(tierStreams)
            }
        return if (results.isNotEmpty()) {
            results
        } else {
            listOfNotNull(pickBestStreamForTier(sourceStreams))
        }
    }

    fun filterBestStreamsWithGroup(streamsWithGroup: List<Pair<String, StreamItem>>): List<Pair<String, StreamItem>> {
        if (streamsWithGroup.isEmpty()) return emptyList()
        val hasCachedStreams = streamsWithGroup.any { !it.second.isUncachedStream }
        val sourceStreams = if (hasCachedStreams) streamsWithGroup.filter { !it.second.isUncachedStream } else streamsWithGroup
        val grouped = sourceStreams.groupBy { (_, stream) -> detectResolutionTier(stream) }
        val results = VideoResolutionTier.entries
            .filter { it != VideoResolutionTier.UNKNOWN }
            .mapNotNull { tier ->
                val tierList = grouped[tier] ?: return@mapNotNull null
                val best = tierList.maxWithOrNull(
                    compareBy<Pair<String, StreamItem>> { (_, it) -> !it.isUncachedStream }
                        .thenBy { (_, it) -> !it.isLowQualitySource }
                        .thenBy { (_, it) -> it.isDirectDebridStream || it.isCachedDebridTorrentStream }
                        .thenBy { (_, it) -> it.playableDirectUrl != null }
                        .thenBy { (_, it) -> it.behaviorHints.videoSize ?: 0L }
                )
                best
            }
        return if (results.isNotEmpty()) {
            results
        } else {
            listOfNotNull(
                sourceStreams.maxWithOrNull(
                    compareBy<Pair<String, StreamItem>> { (_, it) -> !it.isUncachedStream }
                        .thenBy { (_, it) -> !it.isLowQualitySource }
                        .thenBy { (_, it) -> it.isDirectDebridStream || it.isCachedDebridTorrentStream }
                        .thenBy { (_, it) -> it.playableDirectUrl != null }
                        .thenBy { (_, it) -> it.behaviorHints.videoSize ?: 0L }
                )
            )
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
