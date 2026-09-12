package com.nuvio.app.features.streams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.features.debrid.DebridProviders

@Composable
internal fun StreamCard(
    stream: StreamItem,
    enabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    showFileSizeBadges: Boolean,
    showAddonLogo: Boolean,
    badgePlacement: StreamBadgePlacement,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    currentLabel: String? = null,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val details = remember(stream) { StreamCardPresentation.format(stream) }
    val badgeImages = stream.badges.filter { it.imageURL.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .shadow(
                elevation = 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(cardShape)
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
            )
            .then(
                if (isCurrent) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Header Row: ⚡ Server Name (Resolution)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = details.headerTitle,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isCurrent && !currentLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CurrentStreamBadge(label = currentLabel)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body Detail Fields: Name, Languages, Audio
            if (!details.mediaName.isNullOrBlank()) {
                Text(
                    text = "Name: ${details.mediaName}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!details.languages.isNullOrBlank()) {
                Text(
                    text = "Languages: ${details.languages}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!details.audio.isNullOrBlank()) {
                Text(
                    text = "Audio: ${details.audio}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Gap before Bitrate
            if (!details.bitrate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bitrate: ${details.bitrate}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Size badge & any custom badges row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!details.sizeLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "SIZE ${details.sizeLabel}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                        )
                    }
                }
                badgeImages.forEach { badge ->
                    StreamBadgeImage(badge = badge)
                }
            }
        }
    }
}

@Composable
private fun StreamCardBadgeRow(
    badgeImages: List<StreamBadge>,
    stream: StreamItem,
    showFileSizeBadges: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .nuvioDesktopDragScroll(scrollState)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badgeImages.forEach { badge ->
            StreamBadgeImage(badge = badge)
        }
        if (showFileSizeBadges) {
            StreamFileSizeBadge(stream = stream)
        }
    }
}

@Composable
private fun StreamNameWithInstantService(
    stream: StreamItem,
    appendInstantServiceToDefaultName: Boolean,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
    val instantLabel = if (appendInstantServiceToDefaultName) {
        stream.instantServiceLabel()
    } else {
        null
    }
    val showInstantLabel = instantLabel != null
    val visibleState = remember(stream.streamLabel) {
        MutableTransitionState(showInstantLabel)
    }
    visibleState.targetState = showInstantLabel

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stream.streamLabel,
            modifier = Modifier.weight(1f, fill = false),
            style = nameStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 260)) +
                expandHorizontally(
                    animationSpec = tween(durationMillis = 260),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                shrinkHorizontally(
                    animationSpec = tween(durationMillis = 120),
                    shrinkTowards = Alignment.Start,
                ),
            label = "streamNameInstantService",
        ) {
            Text(
                text = " ${instantLabel.orEmpty()}",
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        trailingContent()
    }
}

@Composable
private fun CurrentStreamBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun StreamItem.instantServiceLabel(): String? {
    val status = debridCacheStatus ?: return null
    if (status.state != StreamDebridCacheState.CACHED) return null
    val providerLabel = DebridProviders.shortName(status.providerId)
        .ifBlank { status.providerName.trim() }
        .ifBlank { DebridProviders.displayName(status.providerId) }
    return "- $providerLabel Instant"
}
