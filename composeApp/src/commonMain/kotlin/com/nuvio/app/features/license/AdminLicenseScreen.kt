package com.nuvio.app.features.license

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_admin_control_hub_title
import nuvio.composeapp.generated.resources.settings_admin_media_server_operations
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import kotlinx.coroutines.launch

private enum class AdminHubTab(val label: String) {
    Licenses("License Keys"),
    Analytics("Analytics & Telemetry"),
    ServiceControls("Service Controls"),
    UserDevices("User Devices"),
    MassAddons("Mass-Addon Push"),
}

@Composable
fun AdminLicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adminPassword by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(AdminHubTab.Licenses) }

    var licenses by remember { mutableStateOf<List<LicenseInfo>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Analytics State
    var analyticsRecords by remember { mutableStateOf<List<LicenseAnalyticsRecord>>(emptyList()) }
    var isLoadingAnalytics by remember { mutableStateOf(false) }

    // Generator Form State
    var customerName by remember { mutableStateOf("") }
    var durationDays by remember { mutableStateOf(30) }
    var maxDevices by remember { mutableStateOf(1) }
    var tier by remember { mutableStateOf("standard") }
    var notes by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var newlyCreatedLicense by remember { mutableStateOf<LicenseInfo?>(null) }
    var actionToast by remember { mutableStateOf<String?>(null) }
    var licenseToDelete by remember { mutableStateOf<String?>(null) }

    // Mass-Addon Push Form State
    var addonManifestUrls by remember {
        mutableStateOf(
            "https://v3-cinemeta.strem.io/manifest.json\nhttps://stream.khayin.net/manifest.json"
        )
    }
    var isPushingAddons by remember { mutableStateOf(false) }
    var addonPushStatus by remember { mutableStateOf<String?>(null) }

    // Service Controls State
    var maintenanceModeEnabled by remember { mutableStateOf(false) }
    var maintenanceNotice by remember { mutableStateOf("") }
    var streamingDisabled by remember { mutableStateOf(false) }
    var streamingNotice by remember { mutableStateOf("") }
    var broadcastAlertMessage by remember { mutableStateOf("") }
    var broadcastSeverity by remember { mutableStateOf("INFO") }
    var disabledAddonsText by remember { mutableStateOf("") }
    var serviceStatusMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun loadAnalytics() {
        isLoadingAnalytics = true
        scope.launch {
            AdminControlRepository.fetchAnalytics(300).fold(
                onSuccess = { list ->
                    isLoadingAnalytics = false
                    analyticsRecords = list
                },
                onFailure = {
                    isLoadingAnalytics = false
                },
            )
        }
    }

    fun refreshList() {
        isLoadingList = true
        scope.launch {
            LicenseRepository.adminListLicenses(adminPassword).fold(
                onSuccess = { list ->
                    isLoadingList = false
                    licenses = list
                },
                onFailure = { err ->
                    isLoadingList = false
                    authError = err.message
                },
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        refreshList()
        loadAnalytics()
        val cfg = AdminControlRepository.fetchConfig()
        maintenanceModeEnabled = cfg.maintenanceMode
        maintenanceNotice = cfg.maintenanceNotice
        streamingDisabled = cfg.streamingDisabled
        streamingNotice = cfg.streamingDisabledNotice
        broadcastAlertMessage = cfg.broadcastMessage
        broadcastSeverity = cfg.broadcastSeverity
        disabledAddonsText = cfg.disabledAddons.joinToString("\n")
        if (cfg.presetAddons.isNotEmpty()) {
            addonManifestUrls = cfg.presetAddons.joinToString("\n")
        }
    }

    // Unlocked Admin Management UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13131A))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onBack),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(Res.string.settings_admin_control_hub_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                        Text(
                            text = "KhaYin Media Server Operations",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF888899),
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }

                Button(
                    onClick = {
                        refreshList()
                        loadAnalytics()
                        scope.launch {
                            val cfg = AdminControlRepository.fetchConfig()
                            maintenanceModeEnabled = cfg.maintenanceMode
                            maintenanceNotice = cfg.maintenanceNotice
                            streamingDisabled = cfg.streamingDisabled
                            streamingNotice = cfg.streamingDisabledNotice
                            broadcastAlertMessage = cfg.broadcastMessage
                            broadcastSeverity = cfg.broadcastSeverity
                            disabledAddonsText = cfg.disabledAddons.joinToString("\n")
                            if (cfg.presetAddons.isNotEmpty()) {
                                addonManifestUrls = cfg.presetAddons.joinToString("\n")
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222230),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", fontSize = 13.sp)
                }
            }

            // Navigation Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161622))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminHubTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF00E699) else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = tab.label,
                            style = TextStyle(
                                color = if (selected) Color.Black else Color(0xFF9E9EA7),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }

            // Toast Alert Bar
            actionToast?.let { toast ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B2E24))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(toast, color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF888899),
                            modifier = Modifier.size(16.dp).clickable { actionToast = null },
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                AdminHubTab.Licenses -> {
                    LicensesTabContent(
                        licenses = licenses,
                        isLoading = isLoadingList,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        customerName = customerName,
                        onCustomerNameChange = { customerName = it },
                        durationDays = durationDays,
                        onDurationChange = { durationDays = it },
                        maxDevices = maxDevices,
                        onMaxDevicesChange = { maxDevices = it },
                        tier = tier,
                        onTierChange = { tier = it },
                        isGenerating = isGenerating,
                        newlyCreatedLicense = newlyCreatedLicense,
                        onGenerate = {
                            isGenerating = true
                            scope.launch {
                                LicenseRepository.adminCreateLicense(
                                    adminPassword = adminPassword,
                                    request = AdminLicenseCreateRequest(
                                        customerName = customerName.ifBlank { null },
                                        durationDays = if (durationDays > 0) durationDays else null,
                                        maxDevices = maxDevices,
                                        tier = tier,
                                        notes = notes.ifBlank { null },
                                    ),
                                ).fold(
                                    onSuccess = { created ->
                                        isGenerating = false
                                        newlyCreatedLicense = created
                                        customerName = ""
                                        notes = ""
                                        actionToast = "Generated license: ${created.key}"
                                        refreshList()
                                    },
                                    onFailure = { err ->
                                        isGenerating = false
                                        actionToast = "Error: ${err.message}"
                                    },
                                )
                            }
                        },
                        onCopyKey = { key ->
                            clipboardManager.setText(AnnotatedString(key))
                            actionToast = "Copied $key to clipboard"
                        },
                        onExtendKey = { key ->
                            scope.launch {
                                LicenseRepository.adminExtendLicense(adminPassword, key, 30).fold(
                                    onSuccess = {
                                        actionToast = "Extended $key by 30 days"
                                        refreshList()
                                    },
                                    onFailure = { err -> actionToast = "Error: ${err.message}" },
                                )
                            }
                        },
                        onRevokeKey = { key ->
                            scope.launch {
                                LicenseRepository.adminRevokeLicense(adminPassword, key).fold(
                                    onSuccess = {
                                        actionToast = "Revoked $key"
                                        refreshList()
                                    },
                                    onFailure = { err -> actionToast = "Error: ${err.message}" },
                                )
                            }
                        },
                        onUnrevokeKey = { key ->
                            scope.launch {
                                LicenseRepository.adminUnrevokeLicense(adminPassword, key).fold(
                                    onSuccess = {
                                        actionToast = "Unrevoked $key"
                                        refreshList()
                                    },
                                    onFailure = { err -> actionToast = "Error: ${err.message}" },
                                )
                            }
                        },
                        onDeleteKey = { key ->
                            licenseToDelete = key
                        },
                    )
                }
                AdminHubTab.MassAddons -> {
                    MassAddonsTabContent(
                        addonManifestUrls = addonManifestUrls,
                        onUrlsChange = { addonManifestUrls = it },
                        isPushing = isPushingAddons,
                        pushStatus = addonPushStatus,
                        onPush = {
                            isPushingAddons = true
                            scope.launch {
                                val urls = addonManifestUrls.lines().map { it.trim() }.filter { it.isNotBlank() }
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(presetAddons = urls),
                                ).fold(
                                    onSuccess = {
                                        isPushingAddons = false
                                        addonPushStatus = "Broadcasted ${urls.size} manifests."
                                    },
                                    onFailure = { err ->
                                        isPushingAddons = false
                                        addonPushStatus = "Error: ${err.message}"
                                    },
                                )
                            }
                        },
                    )
                }
                AdminHubTab.ServiceControls -> {
                    ServiceControlsTabContent(
                        maintenanceMode = maintenanceModeEnabled,
                        onMaintenanceToggle = { toggle ->
                            maintenanceModeEnabled = toggle
                            scope.launch {
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        maintenanceMode = toggle,
                                        maintenanceNotice = maintenanceNotice.trim(),
                                    ),
                                )
                                serviceStatusMessage = if (toggle) {
                                    "Maintenance mode ENABLED. Client apps frozen."
                                } else {
                                    "Maintenance mode DISABLED."
                                }
                            }
                        },
                        maintenanceNotice = maintenanceNotice,
                        onMaintenanceNoticeChange = { maintenanceNotice = it },
                        streamingDisabled = streamingDisabled,
                        onStreamingDisabledToggle = { toggle ->
                            streamingDisabled = toggle
                            scope.launch {
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        streamingDisabled = toggle,
                                        streamingDisabledNotice = streamingNotice.trim(),
                                    ),
                                )
                                serviceStatusMessage = if (toggle) {
                                    "Streaming DISABLED on client apps."
                                } else {
                                    "Streaming ENABLED."
                                }
                            }
                        },
                        streamingNotice = streamingNotice,
                        onStreamingNoticeChange = { streamingNotice = it },
                        broadcastMessage = broadcastAlertMessage,
                        onBroadcastMessageChange = { broadcastAlertMessage = it },
                        broadcastSeverity = broadcastSeverity,
                        onBroadcastSeverityChange = { broadcastSeverity = it },
                        onSendBroadcast = {
                            scope.launch {
                                val ts = com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs()
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        broadcastMessage = broadcastAlertMessage.trim(),
                                        broadcastSeverity = broadcastSeverity,
                                        broadcastTimestamp = ts,
                                    ),
                                ).fold(
                                    onSuccess = {
                                        serviceStatusMessage = "Live broadcast banner sent to all connected clients!"
                                    },
                                    onFailure = { err ->
                                        serviceStatusMessage = "Failed to broadcast: ${err.message}"
                                    },
                                )
                            }
                        },
                        onClearBroadcast = {
                            broadcastAlertMessage = ""
                            scope.launch {
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        broadcastMessage = "",
                                        broadcastTimestamp = 0L,
                                    ),
                                ).fold(
                                    onSuccess = {
                                        serviceStatusMessage = "Broadcast banner cleared from all clients."
                                    },
                                    onFailure = { err ->
                                        serviceStatusMessage = "Failed to clear banner: ${err.message}"
                                    },
                                )
                            }
                        },
                        disabledAddonsText = disabledAddonsText,
                        onDisabledAddonsTextChange = { disabledAddonsText = it },
                        statusMessage = serviceStatusMessage,
                        onPublishControls = {
                            scope.launch {
                                val ts = if (broadcastAlertMessage.isNotBlank()) com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs() else 0L
                                val disabledList = disabledAddonsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        maintenanceMode = maintenanceModeEnabled,
                                        maintenanceNotice = maintenanceNotice.trim(),
                                        streamingDisabled = streamingDisabled,
                                        streamingDisabledNotice = streamingNotice.trim(),
                                        broadcastMessage = broadcastAlertMessage.trim(),
                                        broadcastSeverity = broadcastSeverity,
                                        broadcastTimestamp = ts,
                                        disabledAddons = disabledList,
                                    ),
                                ).fold(
                                    onSuccess = {
                                        serviceStatusMessage = "Service controls published to all active client devices!"
                                    },
                                    onFailure = { err ->
                                        serviceStatusMessage = "Failed to publish: ${err.message}"
                                    },
                                )
                            }
                        },
                    )
                }
                AdminHubTab.Analytics -> {
                    AnalyticsTabContent(
                        analytics = analyticsRecords,
                        licenses = licenses,
                        isLoading = isLoadingAnalytics,
                        onRefresh = { loadAnalytics() },
                    )
                }
                AdminHubTab.UserDevices -> {
                    UserDevicesTabContent(licenses = licenses)
                }
            }
        }
    }

    if (licenseToDelete != null) {
        val targetKey = licenseToDelete!!
        AlertDialog(
            onDismissRequest = { licenseToDelete = null },
            title = {
                Text(
                    text = "Delete License Key",
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete license key '$targetKey'? All associated client sessions and data will be removed.",
                    style = TextStyle(color = Color(0xFFCCCEDD), fontSize = 14.sp),
                )
            },
            containerColor = Color(0xFF1C1C26),
            confirmButton = {
                Button(
                    onClick = {
                        val key = targetKey
                        licenseToDelete = null
                        scope.launch {
                            LicenseRepository.adminDeleteLicense(adminPassword, key).fold(
                                onSuccess = {
                                    actionToast = "Permanently deleted $key"
                                    refreshList()
                                },
                                onFailure = { err -> actionToast = "Error: ${err.message}" },
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { licenseToDelete = null }) {
                    Text("Cancel", color = Color(0xFF888899))
                }
            },
        )
    }
}

@Composable
private fun LicensesTabContent(
    licenses: List<LicenseInfo>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    durationDays: Int,
    onDurationChange: (Int) -> Unit,
    maxDevices: Int,
    onMaxDevicesChange: (Int) -> Unit,
    tier: String,
    onTierChange: (String) -> Unit,
    isGenerating: Boolean,
    newlyCreatedLicense: LicenseInfo?,
    onGenerate: () -> Unit,
    onCopyKey: (String) -> Unit,
    onExtendKey: (String) -> Unit,
    onRevokeKey: (String) -> Unit,
    onUnrevokeKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. License Generator Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "GENERATE NEW LICENSE KEY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF00E699),
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Text("Quick Preset Templates", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2A))
                            .border(1.dp, if (tier == "standard" && maxDevices == 3) Color(0xFF00E699) else Color(0xFF323248), RoundedCornerShape(8.dp))
                            .clickable {
                                onTierChange("standard")
                                onMaxDevicesChange(3)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Standard Template", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            Text("Standard Tier • 3 Devices", style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2A))
                            .border(1.dp, if (tier == "plus" && maxDevices == 6) Color(0xFF00E699) else Color(0xFF323248), RoundedCornerShape(8.dp))
                            .clickable {
                                onTierChange("plus")
                                onMaxDevicesChange(6)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Plus Template", style = TextStyle(color = Color(0xFF00E699), fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            Text("Plus Tier • 6 Devices", style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Package / Plan (Customizable)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        "standard" to "Standard",
                        "plus" to "Plus",
                    ).forEach { (pkgKey, pkgTitle) ->
                        val selected = tier.equals(pkgKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF00E699) else Color(0xFF0F0F16))
                                .border(1.dp, if (selected) Color(0xFF00E699) else Color(0xFF323244), RoundedCornerShape(8.dp))
                                .clickable {
                                    onTierChange(pkgKey)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = pkgTitle,
                                style = TextStyle(
                                    color = if (selected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Customer / Client Name", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = customerName,
                        onValueChange = onCustomerNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF00E699)),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (customerName.isEmpty()) Text("e.g. VIP User / John", color = Color(0xFF555566), fontSize = 14.sp)
                            inner()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Duration", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        7 to "7 Days",
                        30 to "30 Days",
                        90 to "90 Days",
                        365 to "1 Year",
                        0 to "Lifetime",
                    ).forEach { (days, label) ->
                        val selected = durationDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF00E699) else Color(0xFF0F0F16))
                                .border(1.dp, if (selected) Color(0xFF00E699) else Color(0xFF323244), RoundedCornerShape(8.dp))
                                .clickable { onDurationChange(days) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    color = if (selected) Color.Black else Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Max Allowed Devices", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1, 2, 3, 5, 6, 10).forEach { devs ->
                        val selected = maxDevices == devs
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF00E699) else Color(0xFF0F0F16))
                                .border(1.dp, if (selected) Color(0xFF00E699) else Color(0xFF323244), RoundedCornerShape(8.dp))
                                .clickable { onMaxDevicesChange(devs) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "$devs Device" + if (devs > 1) "s" else "",
                                style = TextStyle(
                                    color = if (selected) Color.Black else Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E699),
                        contentColor = Color.Black,
                    ),
                    enabled = !isGenerating,
                ) {
                    if (isGenerating) {
                        NuvioLoadingIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate License Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                newlyCreatedLicense?.let { lic ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1B2E24))
                            .border(1.dp, Color(0xFF00E699), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("NEW LICENSE CREATED", style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lic.key,
                                    style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                )
                            }

                            Button(
                                onClick = { onCopyKey(lic.key) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E699),
                                    contentColor = Color.Black,
                                ),
                            ) {
                                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Licenses Header & Search
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "ISSUED LICENSES (${licenses.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Row(
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF16161E))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF888899), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search...", color = Color(0xFF666677), fontSize = 12.sp)
                            inner()
                        },
                    )
                }
            }
        }

        // 3. License Items
        val filteredList = licenses.filter {
            searchQuery.isBlank() ||
                it.key.contains(searchQuery, ignoreCase = true) ||
                (it.customerName ?: "").contains(searchQuery, ignoreCase = true)
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isLoading) "Loading licenses..." else "No licenses found",
                        style = TextStyle(color = Color(0xFF666677), fontSize = 14.sp),
                    )
                }
            }
        } else {
            items(filteredList) { lic ->
                LicenseCardItem(
                    license = lic,
                    onCopy = { onCopyKey(lic.key) },
                    onExtend = { onExtendKey(lic.key) },
                    onRevoke = { onRevokeKey(lic.key) },
                    onUnrevoke = { onUnrevokeKey(lic.key) },
                    onDelete = { onDeleteKey(lic.key) },
                )
            }
        }
    }
}

@Composable
private fun MassAddonsTabContent(
    addonManifestUrls: String,
    onUrlsChange: (String) -> Unit,
    isPushing: Boolean,
    pushStatus: String?,
    onPush: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF16161E))
                .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BROADCAST ADDON BUNDLES TO ALL USERS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    )
                    Text(
                        text = "Addons configured here will be pushed and automatically installed on all user devices when they launch or sync.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Manifest URLs (One per line):", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
            Spacer(modifier = Modifier.height(8.dp))

            // Quick preset pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Quick Add:", style = TextStyle(color = Color(0xFF666677), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                listOf(
                    "Cinemeta" to "https://v3-cinemeta.strem.io/manifest.json",
                    "KhaYin Streams" to "https://stream.khayin.net/manifest.json",
                    "Archive.org" to "https://dev.nebulawp.org/stremio/archive.org-addon/manifest.json",
                    "OpenSubtitles" to "https://opensubtitles-v3.strem.io/manifest.json",
                ).forEach { (label, url) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222230))
                            .border(1.dp, Color(0xFF333344), RoundedCornerShape(6.dp))
                            .clickable {
                                val currentLines = addonManifestUrls.lines().map { it.trim() }.filter { it.isNotBlank() }
                                if (url !in currentLines) {
                                    val updated = (currentLines + url).joinToString("\n")
                                    onUrlsChange(updated)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "+ $label",
                            style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F16))
                    .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                BasicTextField(
                    value = addonManifestUrls,
                    onValueChange = onUrlsChange,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color(0xFF00E699)),
                    decorationBox = { inner ->
                        if (addonManifestUrls.isEmpty()) {
                            Text("https://v3-cinemeta.strem.io/manifest.json\nhttps://stream.khayin.net/manifest.json", color = Color(0xFF555566), fontSize = 13.sp)
                        }
                        inner()
                    },
                )
            }

            pushStatus?.let { status ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D2818))
                        .border(1.dp, Color(0xFF00E699).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(text = status, style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E699),
                    contentColor = Color.Black,
                ),
                enabled = !isPushing,
            ) {
                if (isPushing) {
                    NuvioLoadingIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                } else {
                    Text("Push Addon Bundles to All Devices", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ServiceControlsTabContent(
    maintenanceMode: Boolean,
    onMaintenanceToggle: (Boolean) -> Unit,
    maintenanceNotice: String,
    onMaintenanceNoticeChange: (String) -> Unit,
    streamingDisabled: Boolean,
    onStreamingDisabledToggle: (Boolean) -> Unit,
    streamingNotice: String,
    onStreamingNoticeChange: (String) -> Unit,
    broadcastMessage: String,
    onBroadcastMessageChange: (String) -> Unit,
    broadcastSeverity: String,
    onBroadcastSeverityChange: (String) -> Unit,
    onSendBroadcast: () -> Unit,
    onClearBroadcast: () -> Unit,
    disabledAddonsText: String,
    onDisabledAddonsTextChange: (String) -> Unit,
    statusMessage: String?,
    onPublishControls: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Emergency Controls
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Text("EMERGENCY KILLSWITCHES & SERVICE ACCESS", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(16.dp))

                // Maintenance Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Emergency Maintenance Mode", style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
                        Text("Locks all user apps and displays a full-screen maintenance overlay", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                    }
                    Switch(
                        checked = maintenanceMode,
                        onCheckedChange = onMaintenanceToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF5252)),
                    )
                }

                if (maintenanceMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        BasicTextField(
                            value = maintenanceNotice,
                            onValueChange = onMaintenanceNoticeChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFFFF5252)),
                            decorationBox = { inner ->
                                if (maintenanceNotice.isEmpty()) Text("Custom maintenance message for users...", color = Color(0xFF555566), fontSize = 13.sp)
                                inner()
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Streaming Killswitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Disable Media Streaming Resolvers", style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
                        Text("Blocks stream link fetching during scheduled server upgrades", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                    }
                    Switch(
                        checked = streamingDisabled,
                        onCheckedChange = onStreamingDisabledToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFFAA00)),
                    )
                }

                if (streamingDisabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        BasicTextField(
                            value = streamingNotice,
                            onValueChange = onStreamingNoticeChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFFFFAA00)),
                            decorationBox = { inner ->
                                if (streamingNotice.isEmpty()) Text("Reason for streaming pause...", color = Color(0xFF555566), fontSize = 13.sp)
                                inner()
                            },
                        )
                    }
                }
            }
        }

        // Live Broadcast Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Campaign, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("LIVE BROADCAST NOTICE BANNER", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        Text("Displays a global notification banner across the top header of all user apps.", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    BasicTextField(
                        value = broadcastMessage,
                        onValueChange = onBroadcastMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF00E699)),
                        decorationBox = { inner ->
                            if (broadcastMessage.isEmpty()) Text("Type announcement to broadcast live on all apps (leave empty to clear)...", color = Color(0xFF555566), fontSize = 13.sp)
                            inner()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Severity Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Severity:", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    listOf("INFO" to Color(0xFF3399FF), "WARNING" to Color(0xFFFFAA00), "CRITICAL" to Color(0xFFFF5252), "PROMO" to Color(0xFF00E699)).forEach { (sev, col) ->
                        val isSelected = broadcastSeverity.equals(sev, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) col.copy(alpha = 0.25f) else Color(0xFF0F0F16))
                                .border(1.dp, if (isSelected) col else Color(0xFF262633), RoundedCornerShape(6.dp))
                                .clickable { onBroadcastSeverityChange(sev) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(sev, style = TextStyle(color = if (isSelected) col else Color(0xFF888899), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Send & Clear action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSendBroadcast,
                        enabled = broadcastMessage.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E699),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF222230),
                            disabledContentColor = Color(0xFF555566),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Broadcast Now", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }

                    if (broadcastMessage.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClearBroadcast,
                            border = BorderStroke(1.dp, Color(0xFF444455)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Clear Banner", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // Blacklisted Addons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Text("BLACKLISTED ADDONS (BLOCK INSTANTLY)", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Addons listed here will be blocked and automatically uninstalled from all client apps.", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    BasicTextField(
                        value = disabledAddonsText,
                        onValueChange = onDisabledAddonsTextChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                        textStyle = TextStyle(color = Color(0xFFFF8888), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFFFF5252)),
                        decorationBox = { inner ->
                            if (disabledAddonsText.isEmpty()) Text("e.g. https://malicious-addon.com/manifest.json (one per line)", color = Color(0xFF555566), fontSize = 12.sp)
                            inner()
                        },
                    )
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = msg, style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onPublishControls,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E699), contentColor = Color.Black),
                ) {
                    Text("Publish Service Controls to All Devices", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun UserDevicesTabContent(licenses: List<LicenseInfo>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "ACTIVE DEVICE REGISTRATIONS (${licenses.sumOf { it.activeDevices }} Devices)",
                style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
            )
        }

        items(licenses) { lic ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = lic.customerName ?: "User Client", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Key: ${lic.key} • Tier: ${if (lic.isPlus) "PLUS" else "STANDARD"}",
                        style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF222230))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${lic.activeDevices}/${lic.maxDevices} Devices",
                        style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseCardItem(
    license: LicenseInfo,
    onCopy: () -> Unit,
    onExtend: () -> Unit,
    onRevoke: () -> Unit,
    onUnrevoke: () -> Unit,
    onDelete: () -> Unit,
) {
    val isRevoked = license.status == "revoked"
    val isExpired = license.status == "expired"
    val isPlus = license.isPlus

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161E))
            .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = license.key,
                        style = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isRevoked -> Color(0x22FF5252)
                                    isExpired -> Color(0x22FFAA00)
                                    else -> Color(0x2200E699)
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = when {
                                isRevoked -> "REVOKED"
                                isExpired -> "EXPIRED"
                                else -> "ACTIVE"
                            },
                            style = TextStyle(
                                color = when {
                                    isRevoked -> Color(0xFFFF5252)
                                    isExpired -> Color(0xFFFFAA00)
                                    else -> Color(0xFF00E699)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isPlus) Color(0x2200E699) else Color(0x22888899),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (isPlus) "PLUS" else "STANDARD",
                            style = TextStyle(
                                color = if (isPlus) Color(0xFF00E699) else Color(0xFFCCCEDD),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${license.customerName ?: "Member"} • ${if (isPlus) "Plus" else "Standard"} • ${if (license.expiresAt != null) "Expires " + license.expiresAt.take(10) else "Lifetime"} • ${license.activeDevices}/${license.maxDevices} device(s)",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899), fontSize = 12.sp),
                )
            }

            // Quick Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onCopy,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = Color.White),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                }

                Button(
                    onClick = onExtend,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = Color(0xFF00E699)),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("+30d", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (!isRevoked) {
                    Button(
                        onClick = onRevoke,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FF5252), contentColor = Color(0xFFFF5252)),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Revoke", fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = onUnrevoke,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2200E699), contentColor = Color(0xFF00E699)),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FF5252), contentColor = Color(0xFFFF5252)),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

enum class AnalyticsViewMode {
    SESSIONS,
    EVENTS,
    DIAGNOSTICS,
}

@Composable
private fun AnalyticsTabContent(
    analytics: List<LicenseAnalyticsRecord>,
    licenses: List<LicenseInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    var searchFilter by remember { mutableStateOf("") }
    var selectedEventFilter by remember { mutableStateOf<String?>("ALL") }
    var selectedPlatformFilter by remember { mutableStateOf<String?>("ALL") }
    var viewMode by remember { mutableStateOf(AnalyticsViewMode.SESSIONS) }
    var expandedSessionId by remember { mutableStateOf<String?>(null) }
    var expandedEventIdx by remember { mutableStateOf<Int?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // Auto-refresh telemetry every 15 seconds while active
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            onRefresh()
        }
    }

    val totalEvents = analytics.size

    // Aggregate PostHog Sessions
    val sessions = remember(analytics) {
        AdminControlRepository.groupSessions(analytics)
    }

    val liveSessionsCount = sessions.count { it.isLive }
    val totalPlaybacksCount = analytics.count { it.event == "playback_started" || it.event == "playback_stopped" || it.event == "playback_resumed" }
    val playbackStartsCount = analytics.count { it.event == "playback_started" }
    val playbackFinishedCount = analytics.count { it.event == "playback_finished" || it.event == "playback_stopped" }
    val totalSearchesCount = analytics.count { it.event == "search_performed" || it.event?.startsWith("search") == true }
    val streamQueriesCount = analytics.count { it.event == "stream_fetch_completed" || it.event == "stream_fetch_started" }
    val errorRecords = remember(analytics) {
        analytics.filter { r ->
            val evt = r.event.orEmpty().lowercase()
            evt == "\$exception" || evt.contains("error") || evt == "playback_failed" || r.log_level?.equals("error", ignoreCase = true) == true
        }
    }

    // Platform Filter helper
    fun matchesPlatform(platformStr: String?, filter: String?): Boolean {
        if (filter == null || filter == "ALL") return true
        val p = platformStr.orEmpty().lowercase()
        return when (filter) {
            "DESKTOP" -> p.contains("mac") || p.contains("win") || p.contains("linux") || p.contains("desktop")
            "TV" -> p.contains("tv") || p.contains("leanback") || p.contains("aft")
            "MOBILE" -> (p.contains("android") && !p.contains("tv")) || p.contains("ios") || p.contains("iphone") || p.contains("ipad") || p.contains("mobile")
            else -> true
        }
    }

    // Filtered Sessions
    val filteredSessions = remember(sessions, searchFilter, selectedPlatformFilter) {
        sessions.filter { s ->
            val matchesPlat = matchesPlatform(s.platform, selectedPlatformFilter)
            val matchesSearch = searchFilter.isBlank() ||
                s.licenseKey.contains(searchFilter, ignoreCase = true) ||
                (s.customerName?.contains(searchFilter, ignoreCase = true) == true) ||
                s.deviceId.contains(searchFilter, ignoreCase = true) ||
                (s.location?.contains(searchFilter, ignoreCase = true) == true) ||
                s.mediaPlayed.any { it.contains(searchFilter, ignoreCase = true) } ||
                s.searches.any { it.contains(searchFilter, ignoreCase = true) } ||
                s.platform.contains(searchFilter, ignoreCase = true) ||
                s.recentActivity.contains(searchFilter, ignoreCase = true)
            matchesPlat && matchesSearch
        }
    }

    // Filtered Events
    val filteredEvents = remember(analytics, searchFilter, selectedEventFilter, selectedPlatformFilter) {
        analytics.filter { record ->
            val matchesPlat = matchesPlatform(record.platform, selectedPlatformFilter)
            val matchesSearch = searchFilter.isBlank() ||
                (record.license_key?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.customer_name?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.device_id?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.location?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.media_title?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.search_query?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.log_message?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.platform?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.event?.contains(searchFilter, ignoreCase = true) == true)

            val rawEvt = record.event.orEmpty()
            val matchesEvent = when (selectedEventFilter) {
                null, "ALL" -> true
                "PLAYBACK" -> rawEvt.startsWith("playback_", ignoreCase = true)
                "STREAMS" -> rawEvt.startsWith("stream_", ignoreCase = true)
                "SEARCH" -> rawEvt.startsWith("search", ignoreCase = true)
                "LAUNCH" -> rawEvt.equals("app_launched", ignoreCase = true) || rawEvt.equals("license_activated", ignoreCase = true)
                "IDENTIFY" -> rawEvt.equals("\$identify", ignoreCase = true)
                "SCREENS" -> rawEvt.equals("\$screen", ignoreCase = true)
                "LOGS" -> rawEvt.equals("\$log", ignoreCase = true) || rawEvt.equals("log", ignoreCase = true)
                "ERRORS" -> rawEvt.equals("\$exception", ignoreCase = true) || rawEvt.contains("error", ignoreCase = true) || rawEvt.equals("playback_failed", ignoreCase = true) || record.log_level?.equals("error", ignoreCase = true) == true
                else -> rawEvt.equals(selectedEventFilter, ignoreCase = true)
            }
            matchesPlat && matchesSearch && matchesEvent
        }
    }

    // Filtered Errors
    val filteredErrors = remember(errorRecords, searchFilter, selectedPlatformFilter) {
        errorRecords.filter { r ->
            val matchesPlat = matchesPlatform(r.platform, selectedPlatformFilter)
            val matchesSearch = searchFilter.isBlank() ||
                (r.license_key?.contains(searchFilter, ignoreCase = true) == true) ||
                (r.log_message?.contains(searchFilter, ignoreCase = true) == true) ||
                (r.event?.contains(searchFilter, ignoreCase = true) == true) ||
                (r.platform?.contains(searchFilter, ignoreCase = true) == true)
            matchesPlat && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "REAL-TIME TELEMETRY & SESSIONS",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E699).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E699)),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE FEED (15s)",
                                    style = TextStyle(
                                        color = Color(0xFF00E699),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Real-time user session journeys, playback telemetry, stream resolutions, and diagnostic logs from PostHog.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // View Mode Switcher
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161622))
                            .border(1.dp, Color(0xFF262638), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                    ) {
                        listOf(
                            AnalyticsViewMode.SESSIONS to "SESSIONS (${sessions.size})",
                            AnalyticsViewMode.EVENTS to "EVENT STREAM (${analytics.size})",
                            AnalyticsViewMode.DIAGNOSTICS to "DIAGNOSTICS (${errorRecords.size})",
                        ).forEach { (mode, label) ->
                            val isSelected = viewMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00E699) else Color.Transparent)
                                    .clickable { viewMode = mode }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = TextStyle(
                                        color = if (isSelected) Color.Black else Color(0xFF888899),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = Color(0xFF00E699)),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isLoading) "Updating..." else "Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Top Summary Metric Cards (4 cards in grid)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnalyticsMetricCard(
                    title = "Active Live Users",
                    value = "$liveSessionsCount",
                    subtitle = if (liveSessionsCount > 0) "$liveSessionsCount active in past 3m" else "No active sessions right now",
                    accentColor = if (liveSessionsCount > 0) Color(0xFF00E699) else Color(0xFF888899),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Total Sessions",
                    value = "${sessions.size}",
                    subtitle = "$liveSessionsCount Live • ${sessions.size - liveSessionsCount} Completed",
                    accentColor = Color(0xFF3399FF),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Media Playbacks",
                    value = "$totalPlaybacksCount",
                    subtitle = "$playbackStartsCount started • $playbackFinishedCount completed",
                    accentColor = Color(0xFFAA77FF),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Searches & Streams",
                    value = "$totalSearchesCount",
                    subtitle = "$totalSearchesCount queries • $streamQueriesCount stream fetches",
                    accentColor = Color(0xFFFFCC00),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Filter / Search Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                // Platform Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PLATFORM:",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        )
                        listOf(
                            "ALL" to "ALL PLATFORMS",
                            "DESKTOP" to "DESKTOP",
                            "TV" to "ANDROID TV",
                            "MOBILE" to "MOBILE",
                        ).forEach { (platKey, platLabel) ->
                            val isSelected = (selectedPlatformFilter == platKey) || (platKey == "ALL" && (selectedPlatformFilter == null || selectedPlatformFilter == "ALL"))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00E699).copy(alpha = 0.2f) else Color(0xFF222230))
                                    .border(1.dp, if (isSelected) Color(0xFF00E699) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedPlatformFilter = if (platKey == "ALL") null else platKey }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = platLabel,
                                    style = TextStyle(
                                        color = if (isSelected) Color(0xFF00E699) else Color(0xFFCCCEDD),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }

                    if (viewMode == AnalyticsViewMode.EVENTS) {
                        Text(
                            text = "Showing ${filteredEvents.size} of $totalEvents events",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp),
                        )
                    }
                }

                if (viewMode == AnalyticsViewMode.EVENTS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "EVENT TYPE:",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        )

                        // Event Filter Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "ALL" to "ALL",
                                "PLAYBACK" to "PLAYBACK",
                                "STREAMS" to "STREAMS",
                                "SEARCH" to "SEARCH",
                                "LAUNCH" to "LAUNCH",
                                "SCREENS" to "SCREENS",
                                "LOGS" to "LOGS",
                                "ERRORS" to "ERRORS",
                            ).forEach { (evKey, evLabel) ->
                                val isSelected = (selectedEventFilter == evKey) || (evKey == "ALL" && (selectedEventFilter == null || selectedEventFilter == "ALL"))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF00E699) else Color(0xFF222230))
                                        .clickable { selectedEventFilter = if (evKey == "ALL") null else evKey }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = evLabel,
                                        style = TextStyle(
                                            color = if (isSelected) Color.Black else Color(0xFFCCCEDD),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Filter Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = Color(0xFF888899), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF00E699)),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchFilter.isEmpty()) {
                                Text(
                                    when (viewMode) {
                                        AnalyticsViewMode.SESSIONS -> "Search sessions by customer name, license key, device, media title, search query, or location..."
                                        AnalyticsViewMode.EVENTS -> "Filter events by license key, customer, media title, query, device, or log message..."
                                        AnalyticsViewMode.DIAGNOSTICS -> "Search errors by exception message, license key, or platform..."
                                    },
                                    style = TextStyle(color = Color(0xFF666677), fontSize = 13.sp),
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (searchFilter.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF888899),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { searchFilter = "" },
                        )
                    }
                }
            }
        }

        // Loading State
        if (isLoading && analytics.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NuvioLoadingIndicator()
                }
            }
        } else if (viewMode == AnalyticsViewMode.SESSIONS) {
            // SESSIONS LIST
            if (filteredSessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF16161E))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchFilter.isNotBlank()) "No sessions matching '$searchFilter'" else "No active sessions recorded yet. User client activity will appear here in real-time.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888899)),
                        )
                    }
                }
            } else {
                items(filteredSessions) { session ->
                    val isExpanded = expandedSessionId == session.sessionId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF16161E))
                            .border(1.dp, if (session.isLive) Color(0xFF00E699).copy(alpha = 0.5f) else Color(0xFF262633), RoundedCornerShape(10.dp))
                            .clickable { expandedSessionId = if (isExpanded) null else session.sessionId }
                            .padding(14.dp),
                    ) {
                        // Session Top Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (session.isLive) Color(0xFF00E699).copy(alpha = 0.15f) else Color(0xFF222230))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (session.isLive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E699)),
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        Text(
                                            text = if (session.isLive) "LIVE NOW" else "COMPLETED",
                                            style = TextStyle(
                                                color = if (session.isLive) Color(0xFF00E699) else Color(0xFF888899),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                if (!session.customerName.isNullOrBlank()) {
                                    Text(
                                        text = session.customerName,
                                        style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${session.licenseKey})",
                                        style = TextStyle(color = Color(0xFF00E699), fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    )
                                } else {
                                    Text(
                                        text = session.licenseKey,
                                        style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                    )
                                }
                                if (!session.location.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• ${session.location}",
                                        style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Duration: ${session.durationFormatted} • ${session.totalEvents} events",
                                    style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                )
                                Text(
                                    text = if (isExpanded) "Collapse" else "Expand",
                                    style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Session Highlights Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${session.platform} • ${session.deviceId} • v${session.version}",
                                style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                            )

                            Text(
                                text = session.recentActivity,
                                style = TextStyle(
                                    color = if (session.mediaPlayed.isNotEmpty()) Color(0xFF00D4FF) else Color(0xFFCCCEDD),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }

                        // Active playback progress bar if currently playing
                        if (session.activePlaybackProgress != null && session.activePlaybackProgress > 0f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = (session.activePlaybackProgress / 100f).coerceIn(0f, 1f)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Playback Progress: ${session.activePlaybackTitle ?: "Media"}",
                                        style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    )
                                    Text(
                                        text = "${session.activePlaybackProgress.toInt()}%",
                                        style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF00E699),
                                    trackColor = Color(0xFF222230),
                                )
                            }
                        }

                        // Expanded Session Events Timeline
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F0F16))
                                    .border(1.dp, Color(0xFF222230), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "SESSION JOURNEY TIMELINE",
                                        style = TextStyle(color = Color(0xFF88AAFF), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = "Session ID: ${session.sessionId}",
                                        style = TextStyle(color = Color(0xFF555566), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                    )
                                }

                                val timelineEntries = remember(session.records) { formatSessionTimeline(session.records) }
                                timelineEntries.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(entry.color.copy(alpha = 0.18f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = entry.title,
                                                    style = TextStyle(
                                                        color = entry.color,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                )
                                            }
                                            if (!entry.detail.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = entry.detail,
                                                    style = TextStyle(
                                                        color = if (entry.isImportant) Color.White else Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        fontWeight = if (entry.isImportant) FontWeight.SemiBold else FontWeight.Normal,
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = entry.time,
                                            style = TextStyle(
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (viewMode == AnalyticsViewMode.DIAGNOSTICS) {
            // DIAGNOSTICS & ERRORS VIEW
            if (filteredErrors.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF16161E))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All systems healthy! No errors or exceptions recorded.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            } else {
                items(filteredErrors) { record ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1418))
                            .border(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFF4D4D).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = record.event?.uppercase() ?: "EXCEPTION",
                                        style = TextStyle(color = Color(0xFFFF4D4D), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = record.license_key ?: "UNKNOWN",
                                    style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                )
                                if (!record.location.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "• ${record.location}", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                                }
                            }

                            Text(
                                text = record.created_at?.take(19)?.replace("T", " ") ?: "",
                                style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${record.platform} • ${record.device_id} • v${record.version}",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                        )

                        if (!record.log_message.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0D0D14))
                                    .border(1.dp, Color(0xFF332228), RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = record.log_message,
                                    style = TextStyle(
                                        color = Color(0xFFFF9999),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // EVENTS STREAM LIST
            if (filteredEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF16161E))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchFilter.isNotBlank()) "No events matching '$searchFilter'" else "No telemetry events captured yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888899)),
                        )
                    }
                }
            } else {
                items(filteredEvents.mapIndexed { idx, item -> idx to item }) { (idx, record) ->
                    val isExpanded = expandedEventIdx == idx
                    val rawEvt = record.event.orEmpty()
                    val (eventName, eventColor) = when {
                        rawEvt.equals("app_launched", ignoreCase = true) -> "LAUNCHED" to Color(0xFF00D4FF)
                        rawEvt.equals("license_activated", ignoreCase = true) -> "ACTIVATED" to Color(0xFF3399FF)
                        rawEvt.equals("\$identify", ignoreCase = true) -> "IDENTIFY" to Color(0xFFA855F7)
                        rawEvt.startsWith("playback_started", ignoreCase = true) -> "PLAY START" to Color(0xFF00E699)
                        rawEvt.startsWith("playback_stopped", ignoreCase = true) -> "PLAY STOP" to Color(0xFF88AAFF)
                        rawEvt.startsWith("playback_paused", ignoreCase = true) -> "PLAY PAUSE" to Color(0xFFFFCC00)
                        rawEvt.startsWith("playback_resumed", ignoreCase = true) -> "PLAY RESUME" to Color(0xFF00E699)
                        rawEvt.startsWith("playback_finished", ignoreCase = true) -> "PLAY FINISH" to Color(0xFF00E699)
                        rawEvt.startsWith("playback_failed", ignoreCase = true) -> "PLAY FAIL" to Color(0xFFFF4D4D)
                        rawEvt.startsWith("stream_fetch", ignoreCase = true) -> "STREAMS" to Color(0xFFA855F7)
                        rawEvt.startsWith("search", ignoreCase = true) -> "SEARCH" to Color(0xFFFF9900)
                        rawEvt.equals("\$screen", ignoreCase = true) -> "SCREEN" to Color(0xFF818CF8)
                        rawEvt.equals("\$log", ignoreCase = true) || rawEvt.equals("log", ignoreCase = true) -> ("LOG: " + (record.log_level?.uppercase() ?: "INFO")) to Color(0xFFF59E0B)
                        rawEvt.equals("\$exception", ignoreCase = true) || rawEvt.contains("error", ignoreCase = true) -> "EXCEPTION" to Color(0xFFFF4D4D)
                        else -> rawEvt.uppercase().take(14) to Color(0xFF888899)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF16161E))
                            .border(1.dp, Color(0xFF262633), RoundedCornerShape(10.dp))
                            .clickable { expandedEventIdx = if (isExpanded) null else idx }
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(eventColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = eventName,
                                        style = TextStyle(color = eventColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildString {
                                        if (!record.customer_name.isNullOrBlank()) {
                                            append(record.customer_name)
                                            append(" • ")
                                        }
                                        append(record.license_key ?: "ANONYMOUS")
                                    },
                                    style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                )
                            }

                            val timeText = record.created_at?.take(19)?.replace("T", " ") ?: ""
                            Text(
                                text = timeText,
                                style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Details / Hardware row
                        val detailText = buildString {
                            append(formatTelemetryHardware(record, "1.0.6"))
                            if (!record.media_title.isNullOrBlank()) {
                                append(" • ")
                                append(record.media_title)
                            }
                            if (!record.stream_name.isNullOrBlank()) {
                                append(" (")
                                append(record.stream_name)
                                append(")")
                            }
                            if (!record.search_query.isNullOrBlank()) {
                                append(" • Search: \"")
                                append(record.search_query)
                                append("\"")
                            }
                            if (record.progress_percent != null && record.progress_percent > 0f) {
                                append(" • ${record.progress_percent.toInt()}%")
                            }
                            if (!record.location.isNullOrBlank()) {
                                append(" • ")
                                append(record.location)
                            }
                        }

                        Text(
                            text = detailText,
                            style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                        )

                        if (!record.log_message.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0D0D14))
                                    .border(1.dp, Color(0xFF222230), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = record.log_message,
                                    style = TextStyle(
                                        color = if (eventName == "EXCEPTION" || record.log_level?.equals("error", ignoreCase = true) == true) Color(0xFFFF8888) else Color(0xFFCCD0E0),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTelemetryHardware(record: LicenseAnalyticsRecord, defaultVersion: String): String {
    val rawPlatform = record.platform?.trim().orEmpty()
    val rawDevice = record.device_id?.trim().orEmpty()
    val version = record.version?.takeIf { it.isNotBlank() } ?: defaultVersion

    val osName = when {
        rawPlatform.contains("Mac", ignoreCase = true) || rawPlatform.contains("Darwin", ignoreCase = true) -> "macOS"
        rawPlatform.contains("Windows", ignoreCase = true) || rawPlatform.contains("Win", ignoreCase = true) -> "Windows"
        rawPlatform.contains("Android TV", ignoreCase = true) || rawPlatform.contains("Leanback", ignoreCase = true) -> "Android TV"
        rawPlatform.contains("Android", ignoreCase = true) -> "Android"
        rawPlatform.contains("iOS", ignoreCase = true) || rawPlatform.contains("iPad", ignoreCase = true) -> "iOS"
        rawPlatform.contains("Linux", ignoreCase = true) -> "Linux"
        else -> null
    }

    val cleanDevice = when {
        rawDevice.isBlank() ||
            rawDevice.equals("Active Client", ignoreCase = true) ||
            rawDevice.equals("Offline", ignoreCase = true) ||
            rawDevice.equals("Client", ignoreCase = true) ||
            rawDevice.equals("Desktop Client", ignoreCase = true) ||
            rawDevice.equals("Mobile Client", ignoreCase = true) ||
            rawDevice.equals("Registered Device", ignoreCase = true) -> null
        rawDevice.length > 24 && rawDevice.contains("-") -> "ID: " + rawDevice.take(8).uppercase()
        else -> rawDevice
    }

    val hardwareLabel = when {
        osName != null && cleanDevice != null && !cleanDevice.contains(osName, ignoreCase = true) ->
            "$cleanDevice ($osName)"
        osName != null && cleanDevice != null ->
            cleanDevice
        osName != null ->
            osName
        cleanDevice != null ->
            cleanDevice
        else ->
            "Device"
    }

    return "$hardwareLabel • v$version"
}

private fun isDateExpired(expiresAt: String?): Boolean {
    if (expiresAt.isNullOrBlank()) return false
    val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate()
    return expiresAt.take(10) < today
}

@Composable
private fun AnalyticsMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161E))
            .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888899)))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(color = accentColor, fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF666677), fontSize = 11.sp))
    }
}

private data class CleanTimelineEntry(
    val title: String,
    val detail: String? = null,
    val color: Color,
    val time: String,
    val isImportant: Boolean = false,
)

private fun formatSessionTimeline(records: List<LicenseAnalyticsRecord>): List<CleanTimelineEntry> {
    val meaningful = records.filter {
        val evt = it.event.orEmpty().lowercase()
        evt != "heartbeat" && !evt.startsWith("\$identify") && !evt.startsWith("\$set") && !evt.startsWith("\$create_alias")
    }

    if (meaningful.isEmpty()) {
        val latestTime = records.lastOrNull()?.created_at?.take(19)?.replace("T", " ") ?: ""
        return listOf(
            CleanTimelineEntry(
                title = "Session Active (Idle)",
                detail = "Telemetry connection live · No user actions performed yet",
                color = Color(0xFF00E699),
                time = latestTime,
            )
        )
    }

    val entries = mutableListOf<CleanTimelineEntry>()
    var lastScreen: String? = null

    meaningful.forEach { evt ->
        val rawEvt = evt.event.orEmpty()
        val time = evt.created_at?.take(19)?.replace("T", " ")?.substringAfter(" ") ?: ""

        when {
            rawEvt.startsWith("playback_started", ignoreCase = true) -> {
                val progress = evt.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                val stream = evt.stream_name?.let { " • $it" } ?: ""
                entries.add(
                    CleanTimelineEntry(
                        title = "Started Watching",
                        detail = (evt.media_title ?: "Media") + progress + stream,
                        color = Color(0xFF00E699),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            rawEvt.startsWith("playback_stopped", ignoreCase = true) -> {
                val progress = evt.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                entries.add(
                    CleanTimelineEntry(
                        title = "Stopped Watching",
                        detail = (evt.media_title ?: "Media") + progress,
                        color = Color(0xFF88AAFF),
                        time = time,
                    )
                )
            }
            rawEvt.startsWith("playback_paused", ignoreCase = true) -> {
                val progress = evt.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                entries.add(
                    CleanTimelineEntry(
                        title = "Paused",
                        detail = (evt.media_title ?: "Media") + progress,
                        color = Color(0xFFFFCC00),
                        time = time,
                    )
                )
            }
            rawEvt.startsWith("playback_resumed", ignoreCase = true) -> {
                val progress = evt.progress_percent?.let { " (${it.toInt()}%)" } ?: ""
                entries.add(
                    CleanTimelineEntry(
                        title = "Resumed Watching",
                        detail = (evt.media_title ?: "Media") + progress,
                        color = Color(0xFF00E699),
                        time = time,
                    )
                )
            }
            rawEvt.startsWith("playback_finished", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Finished Watching",
                        detail = evt.media_title ?: "Media",
                        color = Color(0xFF00E699),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            rawEvt.startsWith("playback_failed", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Playback Failed",
                        detail = evt.media_title?.takeIf { it.isNotBlank() } ?: evt.log_message,
                        color = Color(0xFFFF4D4D),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            rawEvt.startsWith("stream_fetch", ignoreCase = true) -> {
                val media = evt.media_title?.let { "for $it" } ?: ""
                val addon = evt.addon_name?.let { "($it)" } ?: ""
                entries.add(
                    CleanTimelineEntry(
                        title = "Fetched Streams",
                        detail = listOf(media, addon).filter { it.isNotBlank() }.joinToString(" "),
                        color = Color(0xFFA855F7),
                        time = time,
                    )
                )
            }
            rawEvt.startsWith("search", ignoreCase = true) -> {
                val q = evt.search_query?.takeIf { it.isNotBlank() }
                entries.add(
                    CleanTimelineEntry(
                        title = "Search",
                        detail = if (q != null) "\"$q\"" else null,
                        color = Color(0xFFFFB800),
                        time = time,
                    )
                )
            }
            rawEvt.equals("profile_switched", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Profile Switched",
                        detail = evt.customer_name?.takeIf { it.isNotBlank() },
                        color = Color(0xFFA855F7),
                        time = time,
                    )
                )
            }
            rawEvt.equals("addon_installed", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Installed Addon",
                        detail = evt.addon_name?.takeIf { it.isNotBlank() },
                        color = Color(0xFF2DD4BF),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            rawEvt.equals("addon_uninstalled", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Removed Addon",
                        detail = evt.addon_name?.takeIf { it.isNotBlank() },
                        color = Color(0xFFFF9900),
                        time = time,
                    )
                )
            }
            rawEvt.equals("app_launched", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "App Launched",
                        detail = evt.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
                        color = Color(0xFF00D4FF),
                        time = time,
                    )
                )
            }
            rawEvt.equals("license_activated", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "License Activated",
                        detail = evt.license_key?.takeIf { it.isNotBlank() },
                        color = Color(0xFF3399FF),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            rawEvt.equals("\$screen", ignoreCase = true) -> {
                val screenName = evt.log_message?.takeIf { it.isNotBlank() } ?: "Screen"
                if (screenName != lastScreen) {
                    lastScreen = screenName
                    entries.add(
                        CleanTimelineEntry(
                            title = "Viewed Screen",
                            detail = screenName,
                            color = Color(0xFF818CF8),
                            time = time,
                        )
                    )
                }
            }
            rawEvt.contains("exception", ignoreCase = true) || rawEvt.contains("error", ignoreCase = true) -> {
                entries.add(
                    CleanTimelineEntry(
                        title = "Error Occurred",
                        detail = evt.log_message?.takeIf { it.isNotBlank() } ?: rawEvt,
                        color = Color(0xFFFF4D4D),
                        time = time,
                        isImportant = true,
                    )
                )
            }
            else -> {
                val cleanTitle = rawEvt.replace("_", " ").split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                entries.add(
                    CleanTimelineEntry(
                        title = cleanTitle,
                        detail = evt.media_title ?: evt.search_query ?: evt.log_message,
                        color = Color(0xFF94A3B8),
                        time = time,
                    )
                )
            }
        }
    }

    return entries
}
