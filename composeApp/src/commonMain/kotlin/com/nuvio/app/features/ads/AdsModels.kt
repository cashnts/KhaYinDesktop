package com.nuvio.app.features.ads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdsNextResponseDto(
    val success: Boolean = true,
    val ad: AdItemDto? = null,
    val mode: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class AdItemDto(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val duration: Int? = null,
    @SerialName("skippableAfter") val skippableAfter: Int? = null,
    val index: Int? = null,
)

data class AdItem(
    val id: String,
    val title: String,
    val url: String,
    val duration: Int,
    val skippableAfter: Int,
    val index: Int,
)

sealed interface AdsResult {
    data class Available(val ad: AdItem) : AdsResult
    data object Disabled : AdsResult
    data class Unavailable(val message: String) : AdsResult
}
