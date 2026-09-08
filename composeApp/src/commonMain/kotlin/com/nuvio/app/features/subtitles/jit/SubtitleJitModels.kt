package com.nuvio.app.features.subtitles.jit

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleHeartbeatRequestDto(
    val slug: String = "anonymous",
    val type: String,
    val id: String,
    val currentTime: Double,
    val isPlaying: Boolean,
    val duration: Double? = null
)

@Serializable
data class SubtitleHeartbeatResponseDto(
    val status: String? = null,
    val activeUsers: Int? = null,
    val isActivelyWatched: Boolean? = null
)

@Serializable
data class SubtitleTranslationStatusDto(
    val mediaId: String? = null,
    val inFlight: Boolean? = null,
    val isPaused: Boolean? = null,
    val completedSections: Int? = null,
    val totalSections: Int? = null,
    val progressPercent: Int? = null,
    val isComplete: Boolean = false
)
