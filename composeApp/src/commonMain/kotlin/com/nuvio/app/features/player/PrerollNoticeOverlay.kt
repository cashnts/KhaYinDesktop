package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.PlatformBackHandler

@Composable
fun PrerollNoticeOverlay(
    visible: Boolean,
    title: String?,
    notice: String?,
    canSkip: Boolean,
    skippableAfter: Int = 5,
    onSkip: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    if (onBack != null) {
        PlatformBackHandler(enabled = true, onBack = onBack)
    }

    val initialSeconds = skippableAfter.coerceAtLeast(0)
    var secondsLeft by remember(skippableAfter) { mutableIntStateOf(initialSeconds) }
    LaunchedEffect(visible, skippableAfter) {
        secondsLeft = initialSeconds
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        // Top-left: Back button and notice banner
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                NuvioBackButton(
                    onClick = onBack,
                    containerColor = Color(0xEE1E1F24),
                    contentColor = Color.White,
                    buttonSize = 44.dp,
                    iconSize = 22.dp,
                    contentDescription = "Back to menu",
                    modifier = Modifier.border(
                        BorderStroke(1.dp, Color(0x33FFFFFF)),
                        RoundedCornerShape(12.dp),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEE1E1F24),
                                Color(0xCC2A2B32),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(1.dp, Color(0x33FFFFFF)),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300)),
                )
                Column {
                    Text(
                        text = title?.takeIf { it.isNotBlank() } ?: "KhaYin Spotlight",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                    Text(
                        text = notice?.takeIf { it.isNotBlank() } ?: "Spotlight • Movie starts shortly",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFB0B3C0),
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }

        // Bottom-right Skip Intro button (or countdown)
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            if (canSkip) {
                Button(
                    onClick = onSkip,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE5A00D),
                        contentColor = Color.Black,
                    ),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    modifier = Modifier.padding(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Skip Intro",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            ),
                        )
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Skip Intro",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xAA1E1F24))
                        .border(
                            BorderStroke(1.dp, Color(0x22FFFFFF)),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Skip in ${secondsLeft}s",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}
