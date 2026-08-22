package com.nuvio.app.features.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.settings.AppBrandWordmark
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_continue
import nuvio.composeapp.generated.resources.action_later
import nuvio.composeapp.generated.resources.updates_message_allow_installs
import nuvio.composeapp.generated.resources.updates_no_release_notes
import nuvio.composeapp.generated.resources.updates_release_notes
import nuvio.composeapp.generated.resources.updates_title_allow_installs
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppUpdaterHost(
    controller: AppUpdaterController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (AppFeaturePolicy.isAdminClient || !AppFeaturePolicy.inAppUpdaterEnabled || !AppUpdaterPlatform.isSupported) {
        content()
        return
    }

    val state by controller.uiState.collectAsStateWithLifecycle()
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.ensureAutoCheckStarted()
    }
    LaunchedEffect(state.update?.tag) {
        showReleaseNotes = false
    }

    val update = state.update
    val showModal = state.showDialog && update != null

    Box(modifier = modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = showModal,
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            update?.let { availableUpdate ->
                AppUpdateScreen(
                    state = state,
                    update = availableUpdate,
                    onDownload = controller::downloadUpdate,
                    onInstall = controller::installDownloadedUpdate,
                    onShowReleaseNotes = { showReleaseNotes = true },
                    onDismiss = controller::dismissDialog,
                )
            }
        }
    }

    if (showReleaseNotes && update != null) {
        ReleaseNotesDialog(
            update = update,
            onDismiss = { showReleaseNotes = false },
        )
    }

    if (state.showInstallPermissionDialog) {
        UnknownSourcesDialog(
            onContinue = controller::resumeInstallation,
            onDismiss = controller::dismissDialog,
        )
    }
}

@Composable
fun AppUpdateScreen(
    state: AppUpdaterUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (state.downloadProgress ?: 0f).coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt().coerceIn(0, 100)
    val isInstalling = state.downloadedUpdatePath != null
    val animatedProgress by animateFloatAsState(
        targetValue = if (isInstalling) 1f else progress,
        animationSpec = tween(durationMillis = 200),
        label = "updateProgress",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11))
            .zIndex(100f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(600.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x2200E699),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBrandWordmark(
                modifier = Modifier
                    .height(90.dp)
                    .padding(bottom = 16.dp),
            )

            Icon(
                imageVector = if (isInstalling) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF00E699),
                modifier = Modifier.size(56.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isInstalling) "Update Ready" else "Updating KhaYin",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isInstalling) {
                    "Version ${update.tag} has been downloaded and is ready to install."
                } else if (state.isDownloading) {
                    "Downloading version ${update.tag}... Please keep the app open."
                } else {
                    "A new version of KhaYin (v${update.tag}) is available."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF9E9EA7),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF222228)),
                ) {
                    if (state.isDownloading || isInstalling) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(maxOf(animatedProgress, 0.04f))
                                .fillMaxSize()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00B0FF),
                                            Color(0xFF00E699),
                                        ),
                                    ),
                                ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isInstalling) "Ready to install" else if (state.isDownloading) "Downloading..." else "Pending",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF7A7A85)),
                    )
                    Text(
                        text = if (isInstalling) "100%" else "$percentage%",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isDownloading || !isInstalling) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF222228),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = if (state.isDownloading) "Background" else "Later",
                            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        )
                    }
                }

                Button(
                    onClick = if (isInstalling) onInstall else onDownload,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E699),
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = if (isInstalling) "Install & Restart" else if (state.isDownloading) "Downloading..." else "Update Now",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseNotesDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.updates_release_notes),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = update.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = update.notes.ifBlank { stringResource(Res.string.updates_no_release_notes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownSourcesDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.updates_title_allow_installs),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.updates_message_allow_installs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_later))
                    }
                    Button(onClick = onContinue) {
                        Text(stringResource(Res.string.action_continue))
                    }
                }
            }
        }
    }
}
