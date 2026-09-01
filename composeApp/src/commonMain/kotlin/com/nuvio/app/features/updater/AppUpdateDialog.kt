package com.nuvio.app.features.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.build.AppVersionPolicy
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_ok
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.action_update
import nuvio.composeapp.generated.resources.updates_check_failed
import nuvio.composeapp.generated.resources.updates_latest_version
import nuvio.composeapp.generated.resources.updates_message_downloading
import nuvio.composeapp.generated.resources.updates_message_ready
import nuvio.composeapp.generated.resources.updates_release_notes
import nuvio.composeapp.generated.resources.updates_title_available
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppUpdateDialog(
    modifier: Modifier = Modifier,
) {
    val state by PlatformAppUpdater.state.collectAsState()

    // Up-to-date feedback or error modal for manual check
    if (state.showUpToDateFeedback) {
        val status = state.status
        when (status) {
            is AppUpdateStatus.UpToDate -> {
                NuvioStatusModal(
                    title = stringResource(Res.string.updates_latest_version),
                    message = "You are currently running version ${AppVersionPolicy.displayVersionName}.",
                    isVisible = true,
                    confirmText = stringResource(Res.string.action_ok),
                    onConfirm = { PlatformAppUpdater.dismissUpToDateFeedback() },
                    onDismiss = { PlatformAppUpdater.dismissUpToDateFeedback() },
                )
            }
            is AppUpdateStatus.Error -> {
                NuvioStatusModal(
                    title = stringResource(Res.string.updates_check_failed),
                    message = status.message,
                    isVisible = true,
                    confirmText = stringResource(Res.string.action_retry),
                    dismissText = stringResource(Res.string.action_close),
                    onConfirm = {
                        PlatformAppUpdater.dismissUpToDateFeedback()
                        PlatformAppUpdater.checkForUpdate(manual = true)
                    },
                    onDismiss = { PlatformAppUpdater.dismissUpToDateFeedback() },
                )
            }
            else -> Unit
        }
    }

    if (!state.isDialogVisible || state.availableUpdate == null) return

    val info = state.availableUpdate ?: return
    val tokens = MaterialTheme.nuvio
    val status = state.status

    BasicAlertDialog(
        onDismissRequest = {
            if (status !is AppUpdateStatus.Downloading) {
                PlatformAppUpdater.dismissDialog()
            }
        },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.dialog),
            color = tokens.colors.surfaceDialog,
            shape = tokens.shapes.dialog,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                // Header row with Icon and Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tokens.colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (status) {
                                is AppUpdateStatus.ReadyToInstall -> Icons.Rounded.CheckCircle
                                is AppUpdateStatus.Downloading -> Icons.Rounded.CloudDownload
                                is AppUpdateStatus.Error -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.SystemUpdate
                            },
                            contentDescription = null,
                            tint = when (status) {
                                is AppUpdateStatus.ReadyToInstall -> Color(0xFF4CAF50)
                                is AppUpdateStatus.Error -> Color(0xFFEF5350)
                                else -> tokens.colors.accent
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.updates_title_available),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tokens.colors.accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "v${info.versionName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.colors.accent,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current: v${AppVersionPolicy.displayVersionName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release notes / description
                if (info.changelog.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.updates_release_notes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.colors.background.copy(alpha = 0.5f))
                            .border(1.dp, tokens.colors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = info.changelog.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textPrimary,
                            lineHeight = 18.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Progress / State info
                when (status) {
                    is AppUpdateStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(Res.string.updates_message_downloading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.colors.textSecondary,
                                )
                                Text(
                                    text = "${(status.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.colors.accent,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { status.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = tokens.colors.accent,
                                trackColor = tokens.colors.surfaceElevated,
                            )
                            if (status.totalBytes > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val downloadedMb = ((status.bytesDownloaded * 10) / (1024 * 1024)) / 10.0
                                val totalMb = ((status.totalBytes * 10) / (1024 * 1024)) / 10.0
                                Text(
                                    text = "$downloadedMb MB / $totalMb MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textSecondary,
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is AppUpdateStatus.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.12f))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.updates_message_ready),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF81C784),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is AppUpdateStatus.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.12f))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE57373),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    else -> Unit
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (status !is AppUpdateStatus.Downloading) {
                        OutlinedButton(
                            onClick = { PlatformAppUpdater.dismissDialog() },
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(tokens.colors.borderDefault),
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.action_cancel),
                                color = tokens.colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    when (status) {
                        is AppUpdateStatus.ReadyToInstall -> {
                            Button(
                                onClick = { PlatformAppUpdater.installUpdate() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Install & Relaunch")
                            }
                        }
                        is AppUpdateStatus.Downloading -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Downloading...")
                            }
                        }
                        else -> {
                            Button(
                                onClick = { PlatformAppUpdater.downloadUpdate() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.colors.accent,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(stringResource(Res.string.action_update))
                            }
                        }
                    }
                }
            }
        }
    }
}
