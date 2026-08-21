package com.nuvio.app.features.license

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.settings.AppBrandWordmark

@Composable
fun LicenseExpiredScreen(
    licenseInfo: LicenseInfo,
    onEnterNewKey: () -> Unit,
    onOpenAdminPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isRevoked = licenseInfo.status == "revoked"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11)),
        contentAlignment = Alignment.Center,
    ) {
        // Warning Ambient Glow
        Box(
            modifier = Modifier
                .size(600.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isRevoked) Color(0x22FF4444) else Color(0x22FFAA00),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBrandWordmark(
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Icon(
                imageVector = if (isRevoked) Icons.Rounded.Block else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (isRevoked) Color(0xFFFF5252) else Color(0xFFFFB300),
                modifier = Modifier.size(56.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isRevoked) "License Revoked" else "License Expired",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRevoked) {
                    "This license key has been revoked by an administrator. Streaming and profile synchronization are disabled."
                } else {
                    "Your subscription period has ended. Your profile and saved items are frozen in read-only mode, and stream playback is restricted."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF9E9EA7),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // License Details Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LICENSE KEY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF888899),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = licenseInfo.key,
                        style = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                    )
                }

                if (!licenseInfo.customerName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "ASSIGNED TO",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888899)),
                        )
                        Text(
                            text = licenseInfo.customerName,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFDDDDDD)),
                        )
                    }
                }

                if (!licenseInfo.expiresAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "EXPIRED AT",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888899)),
                        )
                        Text(
                            text = licenseInfo.expiresAt.take(10),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isRevoked) Color(0xFFFF5252) else Color(0xFFFFB300),
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onEnterNewKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E699),
                    contentColor = Color.Black,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter New License Key",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                }
            }

            if (com.nuvio.app.core.build.AppFeaturePolicy.isAdminClient) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenAdminPanel)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color(0xFF6B6B7F),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Administrator Portal",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF6B6B7F),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}
