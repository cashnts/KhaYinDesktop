package com.nuvio.app.features.ads

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.license.LicenseRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamPreroll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

object AdsRepository {
    private val log = Logger.withTag("AdsRepository")
    private const val ADS_NEXT_URL = "https://stream.khayin.net/api/ads/next"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getNextAd(): AdsResult {
        com.nuvio.app.core.analytics.PostHogAnalytics.trackAdRequested(
            adType = "preroll",
            isFreeUser = LicenseRepository.isFreeUser
        )
        return try {
            val responseText = withTimeoutOrNull(8000L) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = ADS_NEXT_URL,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "User-Agent" to "KhaYin/Desktop"
                    ),
                    body = ""
                )
                if (response.status in 200..299) response.body else null
            }

            if (responseText.isNullOrBlank()) {
                log.w { "Failed to fetch ad or timed out" }
                val reason = "Ad response empty or timed out"
                com.nuvio.app.core.analytics.PostHogAnalytics.trackAdUnavailable(reason = reason, adType = "preroll")
                return AdsResult.Unavailable(reason)
            }

            val parsed = json.decodeFromString<AdsNextResponseDto>(responseText)
            if (!parsed.enabled) {
                log.i { "Ad killswitch active (enabled=false)" }
                com.nuvio.app.core.analytics.PostHogAnalytics.trackAdUnavailable(reason = "Disabled by server killswitch", adType = "preroll")
                return AdsResult.Disabled
            }

            val adDto = parsed.ad
            if (adDto?.url.isNullOrBlank()) {
                log.w { "Ad payload missing URL" }
                val reason = "Ad payload missing URL"
                com.nuvio.app.core.analytics.PostHogAnalytics.trackAdUnavailable(reason = reason, adType = "preroll")
                return AdsResult.Unavailable(reason)
            }

            log.i { "Fetched ad id=${adDto.id}, title=${adDto.title}, url=${adDto.url}" }
            val adItem = AdItem(
                id = adDto.id?.takeIf { it.isNotBlank() } ?: adDto.url ?: "preroll",
                title = adDto.title?.takeIf { it.isNotBlank() } ?: "KhaYin Spotlight",
                url = adDto.url,
                duration = adDto.duration ?: 15,
                skippableAfter = adDto.skippableAfter ?: 5,
                index = adDto.index ?: 0
            )
            com.nuvio.app.core.analytics.PostHogAnalytics.trackAdLoaded(
                adId = adItem.id,
                adTitle = adItem.title,
                adUrl = adItem.url,
                durationSeconds = adItem.duration,
                skippableAfter = adItem.skippableAfter,
                adType = "preroll"
            )
            AdsResult.Available(adItem)
        } catch (e: Exception) {
            log.w(e) { "Error fetching next ad: ${e.message}" }
            val reason = e.message ?: "Unknown error"
            com.nuvio.app.core.analytics.PostHogAnalytics.trackAdUnavailable(reason = reason, adType = "preroll")
            AdsResult.Unavailable(reason)
        }
    }

    suspend fun getNextPrerollAd(stream: StreamItem?): StreamPreroll? {
        if (!LicenseRepository.isFreeUser) {
            log.d { "Skipping preroll: user is paid/licensed (isFreeUser=false)" }
            return null
        }

        return when (val result = getNextAd()) {
            is AdsResult.Available -> {
                log.i { "Applying preroll ad: ${result.ad.title} (${result.ad.url})" }
                StreamPreroll(
                    url = result.ad.url,
                    duration = result.ad.duration,
                    title = result.ad.title,
                    skippableAfter = result.ad.skippableAfter,
                    id = result.ad.id
                )
            }
            is AdsResult.Disabled -> null
            is AdsResult.Unavailable -> {
                // Fallback to stream's own preroll if present
                stream?.preroll ?: stream?.behaviorHints?.prerollUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    StreamPreroll(
                        url = url,
                        duration = stream.behaviorHints.prerollDuration ?: 15,
                        title = stream.behaviorHints.prerollTitle ?: "KhaYin Spotlight",
                        skippableAfter = 5
                    )
                }
            }
        }
    }
}
