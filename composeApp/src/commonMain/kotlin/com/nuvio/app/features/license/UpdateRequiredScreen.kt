package com.nuvio.app.features.license

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlinx.coroutines.launch

@Composable
fun UpdateRequiredScreen(
    currentVersion: String,
    minVersion: String = "",
    unsupportedThreshold: String = "",
    notice: String = "",
    updateUrl: String = "",
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var isChecking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(600.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x2200D2FF),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBrandWordmark(
                modifier = Modifier
                    .height(90.dp)
                    .padding(bottom = 14.dp),
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141926))
                    .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Update Required",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Version info badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF262638), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Installed: v$currentVersion",
                    style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = "•",
                    style = TextStyle(color = Color(0xFF444455), fontSize = 12.sp),
                )
                val requiredLabel = when {
                    unsupportedThreshold.isNotBlank() -> "Unsupported: ≤ v$unsupportedThreshold"
                    minVersion.isNotBlank() -> "Required: ≥ v$minVersion"
                    else -> "Newer version required"
                }
                Text(
                    text = requiredLabel,
                    style = TextStyle(color = Color(0xFFFF8844), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice or default description
            Text(
                text = if (notice.isNotBlank()) notice else "Your installed version of KhaYin is no longer supported. Please update to the latest release to continue enjoying streaming and library features.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFB0B0C0),
                    lineHeight = 20.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            val effectiveUrl = updateUrl.ifBlank { "https://github.com/aungzayphyo/KhaYin/releases" }
            Button(
                onClick = {
                    runCatching { uriHandler.openUri(effectiveUrl) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D2FF),
                    contentColor = Color.Black,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Latest Update",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    if (isChecking) return@OutlinedButton
                    isChecking = true
                    scope.launch {
                        AdminControlRepository.fetchConfig()
                        isChecking = false
                        onCheckAgain()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333344)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFAAAAAA),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isChecking) "Checking Status..." else "Check Server Status",
                        style = TextStyle(color = Color(0xFFDDDDDD), fontSize = 14.sp),
                    )
                }
            }
        }
    }
}
