package com.nuvio.app.features.license

import com.nuvio.app.features.player.PlayerResolutionHelper
import com.nuvio.app.features.player.VideoResolutionTier
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem

object FreeTierQualityLimiter {

    /**
     * Filters addon stream groups for Free users on movie content:
     * - Capped to 720p if 720p or lower is available.
     * - Capped to 1080p if 720p is not available.
     */
    fun filterAddonStreamsForFreeTier(
        groups: List<AddonStreamGroup>,
        isMovie: Boolean,
    ): List<AddonStreamGroup> {
        if (!LicenseRepository.isFreeUser || !isMovie || groups.isEmpty()) {
            return groups
        }

        val allStreams = groups.flatMap { it.streams }
        val has720pOrBelow = allStreams.any { stream ->
            val tier = PlayerResolutionHelper.detectResolutionTier(stream)
            tier == VideoResolutionTier.HD_720P || tier == VideoResolutionTier.SD_480P
        }

        return groups.mapNotNull { group ->
            val filtered = group.streams.filter { stream ->
                val tier = PlayerResolutionHelper.detectResolutionTier(stream)
                if (has720pOrBelow) {
                    tier == VideoResolutionTier.HD_720P ||
                        tier == VideoResolutionTier.SD_480P ||
                        tier == VideoResolutionTier.UNKNOWN
                } else {
                    tier != VideoResolutionTier.UHD_4K && tier != VideoResolutionTier.QHD_2K
                }
            }
            if (filtered.isNotEmpty()) group.copy(streams = filtered) else null
        }
    }

    /**
     * Filters a flat list of streams for Free users on movie content.
     */
    fun filterStreamsForFreeTier(
        streams: List<StreamItem>,
        isMovie: Boolean,
    ): List<StreamItem> {
        if (!LicenseRepository.isFreeUser || !isMovie || streams.isEmpty()) {
            return streams
        }

        val has720pOrBelow = streams.any { stream ->
            val tier = PlayerResolutionHelper.detectResolutionTier(stream)
            tier == VideoResolutionTier.HD_720P || tier == VideoResolutionTier.SD_480P
        }

        return streams.filter { stream ->
            val tier = PlayerResolutionHelper.detectResolutionTier(stream)
            if (has720pOrBelow) {
                tier == VideoResolutionTier.HD_720P ||
                    tier == VideoResolutionTier.SD_480P ||
                    tier == VideoResolutionTier.UNKNOWN
            } else {
                tier != VideoResolutionTier.UHD_4K && tier != VideoResolutionTier.QHD_2K
            }
        }
    }
}
