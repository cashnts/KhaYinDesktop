package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamCard
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.streams.isSelectableForPlayback
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_streams_found
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight


@Composable
internal fun PlayerStreamList(
    streamsUiState: StreamsUiState,
    onStreamSelected: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = 8.dp,
        top = 14.dp,
        end = 8.dp,
        bottom = 8.dp,
    ),
    currentStreamUrl: String? = null,
    currentStreamName: String? = null,
    currentLabel: String? = null,
) {
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val streamBadgeSettings by remember {
        StreamBadgeSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val streams: List<StreamItem> = remember(streamsUiState.allStreams) {
        PlayerResolutionHelper.filterBestStreams(streamsUiState.allStreams)
    }
    val visibleGroups = streamsUiState.filteredGroups

    when {
        streams.isEmpty() && streamsUiState.isAnyLoading -> {
            PlayerModalLoading(modifier = Modifier.padding(vertical = 24.dp))
        }

        streams.isEmpty() -> {
            val error = visibleGroups.firstOrNull { it.error != null }?.error
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = error ?: stringResource(Res.string.compose_player_no_streams_found),
                    color = Color.White.copy(alpha = if (error == null) 0.7f else 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        else -> {
            val streamKeys = remember(streams) { streams.stablePlayerKeys() }
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = streams,
                    key = { index, _ -> streamKeys[index] },
                ) { _, stream ->
                    val isCurrent = stream.isCurrentPlayerStream(currentStreamUrl, currentStreamName)
                    val tier = PlayerResolutionHelper.detectResolutionTier(stream)
                    val title = if (tier != VideoResolutionTier.UNKNOWN) tier.label else stream.streamLabel
                    val sizeText = if (streamBadgeSettings.showFileSizeBadges) {
                        PlayerResolutionHelper.formatStreamVideoSize(stream.behaviorHints.videoSize)
                    } else null
                    val isEnabled = stream.isSelectableForPlayback(debridSettings.canResolvePlayableLinks)

                    Surface(
                        onClick = { onStreamSelected(stream) },
                        enabled = isEnabled,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                        border = if (isCurrent) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        } else {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Box(modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.9f),
                                )
                            }
                            if (!sizeText.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (streamsUiState.isAnyLoading) {
                    item {
                        PlayerModalLoading(modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
            }
        }
    }
}
