package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState

@Composable
internal fun PlayerResolutionPanel(
    visible: Boolean,
    streamsUiState: StreamsUiState,
    currentStreamUrl: String?,
    currentStreamName: String?,
    onStreamSelected: (StreamItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val allStreams = remember(streamsUiState.groups) {
        streamsUiState.groups.flatMap { it.streams }
    }
    val resolutionOptions = remember(allStreams, currentStreamUrl, currentStreamName) {
        PlayerResolutionHelper.buildResolutionOptions(
            streams = allStreams,
            currentStreamUrl = currentStreamUrl,
            currentStreamName = currentStreamName,
        )
    }

    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        width = 460.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = "Quality & Resolution",
            ) {
                PlayerDialogButton(
                    label = "Close",
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Switch video resolution. The best source option is automatically selected for each quality tier.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 18.sp,
                ),
            )

            Spacer(Modifier.height(20.dp))

            if (resolutionOptions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (streamsUiState.isAnyLoading) "Loading available resolutions…" else "No resolution options available",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f)),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(resolutionOptions, key = { it.tier.name }) { option ->
                        ResolutionOptionCard(
                            option = option,
                            onClick = {
                                onStreamSelected(option.bestStream)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionOptionCard(
    option: PlayerResolutionOption,
    onClick: () -> Unit,
) {
    val tierColor = when (option.tier) {
        VideoResolutionTier.UHD_4K -> Color(0xFF00E676)
        VideoResolutionTier.QHD_2K -> Color(0xFF00B0FF)
        VideoResolutionTier.FHD_1080P -> Color(0xFF2979FF)
        VideoResolutionTier.HD_720P -> Color(0xFFFF9100)
        VideoResolutionTier.SD_480P -> Color(0xFFFF5252)
        VideoResolutionTier.UNKNOWN -> Color(0xFFAAAAAA)
    }

    val stream = option.bestStream
    val streamSize = stream.behaviorHints.videoSize?.let { bytes ->
        if (bytes > 0L) {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            "${((gb * 10).toInt() / 10.0)} GB"
        } else null
    }

    val cardBorderColor = if (option.isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    val cardBgColor = if (option.isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBgColor)
            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tierColor.copy(alpha = 0.16f))
                .border(1.dp, tierColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = option.tier.shortLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = tierColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = option.tier.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = if (option.isActive) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "CURRENT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }

            val subtitleText = buildString {
                append(stream.addonName.ifBlank { "Primary Server" })
                if (streamSize != null) {
                    append(" • ")
                    append(streamSize)
                }
                if (stream.isDirectDebridStream || stream.isCachedDebridTorrentStream) {
                    append(" • Fast Cloud")
                }
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (option.isActive) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Active",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
