package com.nuvio.app.features.tmdb

const val DEFAULT_TMDB_API_KEY = "4144d576cc2293c9669ce8fb31025950"

data class TmdbSettings(
    val enabled: Boolean = true,
    val apiKey: String = DEFAULT_TMDB_API_KEY,
    val language: String = "en",
    val useTrailers: Boolean = true,
    val useArtwork: Boolean = true,
    val useBasicInfo: Boolean = true,
    val useDetails: Boolean = true,
    val useReleaseDates: Boolean = false,
    val useCredits: Boolean = true,
    val useProductions: Boolean = true,
    val useNetworks: Boolean = true,
    val useEpisodes: Boolean = true,
    val useSeasonPosters: Boolean = true,
    val useMoreLikeThis: Boolean = true,
    val useCollections: Boolean = true,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()
}
