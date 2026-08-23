package com.nuvio.app.features.license

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    var streamingDisabled by remember { mutableStateOf(false) }
    var broadcastAlertMessage by remember { mutableStateOf("") }
    var serviceStatusMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun loadAnalytics() {
        isLoadingAnalytics = true
        scope.launch {
            AdminControlRepository.fetchAnalytics(100).fold(
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
        streamingDisabled = cfg.streamingDisabled
        broadcastAlertMessage = cfg.broadcastMessage
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
                            text = "Admin Control Hub",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                        Text(
                            text = "KhaYin Media Server Operations",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888899)),
                        )
                    }
                }

                Button(
                    onClick = { refreshList() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E28),
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
                        Text(text = toast, style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp))
                        Text(
                            text = "Dismiss",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp),
                            modifier = Modifier.clickable { actionToast = null },
                        )
                    }
                }
            }

            // Main Tab Content
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
                                    onSuccess = { lic ->
                                        isGenerating = false
                                        newlyCreatedLicense = lic
                                        actionToast = "Generated key: ${lic.key}"
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
                            actionToast = "Copied $key"
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
                                        actionToast = "Activated $key"
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
                    MassAddonPushTabContent(
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
                                        addonPushStatus = "Successfully broadcasted ${urls.size} addon manifest(s) to all clients!"
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
                                    AdminControlRepository.config.value.copy(maintenanceMode = toggle),
                                )
                                serviceStatusMessage = if (toggle) {
                                    "Maintenance mode ENABLED. Client apps frozen."
                                } else {
                                    "Maintenance mode DISABLED."
                                }
                            }
                        },
                        streamingDisabled = streamingDisabled,
                        onStreamingDisabledToggle = { toggle ->
                            streamingDisabled = toggle
                            scope.launch {
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(streamingDisabled = toggle),
                                )
                                serviceStatusMessage = if (toggle) {
                                    "Streaming DISABLED on client apps."
                                } else {
                                    "Streaming ENABLED."
                                }
                            }
                        },
                        broadcastMessage = broadcastAlertMessage,
                        onBroadcastMessageChange = { broadcastAlertMessage = it },
                        statusMessage = serviceStatusMessage,
                        onPublishBroadcast = {
                            scope.launch {
                                val ts = if (broadcastAlertMessage.isNotBlank()) com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs() else 0L
                                AdminControlRepository.updateConfig(
                                    AdminControlRepository.config.value.copy(
                                        broadcastMessage = broadcastAlertMessage.trim(),
                                        broadcastTimestamp = ts,
                                    ),
                                )
                                serviceStatusMessage = if (broadcastAlertMessage.isNotBlank()) {
                                    "Broadcast alert published to all active client apps."
                                } else {
                                    "Broadcast alert cleared."
                                }
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
private fun MassAddonPushTabContent(
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
                        text = "BROADCAST ADDON BUNDLES TO USERS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    )
                    Text(
                        text = "Addons configured here will be pushed and automatically installed on all user clients.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Manifest URLs (One per line)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
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
                )
            }

            pushStatus?.let { status ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = status, style = TextStyle(color = Color(0xFF00E699), fontSize = 13.sp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
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
                    Text("Push Addon Manifests to All Clients", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ServiceControlsTabContent(
    maintenanceMode: Boolean,
    onMaintenanceToggle: (Boolean) -> Unit,
    streamingDisabled: Boolean,
    onStreamingDisabledToggle: (Boolean) -> Unit,
    broadcastMessage: String,
    onBroadcastMessageChange: (String) -> Unit,
    statusMessage: String?,
    onPublishBroadcast: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Toggle switches
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF16161E))
                .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                .padding(20.dp),
        ) {
            Text("SERVICE AVAILABILITY & TOGGLES", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Maintenance Mode (Emergency Killswitch)", style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
                    Text("Temporarily freezes client apps with a maintenance notice", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                }
                Switch(
                    checked = maintenanceMode,
                    onCheckedChange = onMaintenanceToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF5252)),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable Media Streaming", style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
                    Text("Blocks stream link fetching during scheduled server upgrades", style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp))
                }
                Switch(
                    checked = streamingDisabled,
                    onCheckedChange = onStreamingDisabledToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFFAA00)),
                )
            }
        }

        // Broadcast Alert Card
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
                Text("EMERGENCY BROADCAST MESSAGE", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
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
                        if (broadcastMessage.isEmpty()) Text("Type alert message to show on client apps...", color = Color(0xFF555566), fontSize = 13.sp)
                        inner()
                    },
                )
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = msg, style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onPublishBroadcast,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E699), contentColor = Color.Black),
            ) {
                Text("Publish Broadcast", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

@Composable
private fun AnalyticsTabContent(
    analytics: List<LicenseAnalyticsRecord>,
    licenses: List<LicenseInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    var searchFilter by remember { mutableStateOf("") }
    var selectedEventFilter by remember { mutableStateOf<String?>("ALL") }

    val totalHeartbeats = analytics.size
    val uniqueKeys = analytics.mapNotNull { it.license_key }.distinct().size
    val uniqueDevices = analytics.mapNotNull { it.device_id }.distinct().size

    // License metrics
    val totalLicenses = licenses.size
    val activeLicenses = licenses.count { it.status.equals("active", ignoreCase = true) && !isDateExpired(it.expiresAt) }
    val expiredLicenses = licenses.count { isDateExpired(it.expiresAt) && !it.status.equals("revoked", ignoreCase = true) }
    val revokedLicenses = licenses.count { it.status.equals("revoked", ignoreCase = true) }

    val totalActiveDevices = licenses.sumOf { it.activeDevices }
    val totalMaxDevices = licenses.sumOf { it.maxDevices }
    val utilizationFraction = if (totalMaxDevices > 0) (totalActiveDevices.toFloat() / totalMaxDevices.toFloat()).coerceIn(0f, 1f) else 0f
    val utilizationPct = (utilizationFraction * 100).toInt()

    // Platform Breakdown
    val platforms = remember(analytics) {
        val groups = analytics.groupBy { (it.platform ?: "Desktop Client").trim() }
        groups.map { (name, list) ->
            val count = list.size
            val pct = if (totalHeartbeats > 0) (count.toFloat() / totalHeartbeats.toFloat()) else 0f
            Triple(name, count, pct)
        }.sortedByDescending { it.second }
    }

    // Filtered Telemetry Records
    val filteredAnalytics = remember(analytics, searchFilter, selectedEventFilter) {
        analytics.filter { record ->
            val matchesSearch = searchFilter.isBlank() ||
                (record.license_key?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.device_id?.contains(searchFilter, ignoreCase = true) == true) ||
                (record.platform?.contains(searchFilter, ignoreCase = true) == true)
            val matchesEvent = selectedEventFilter == "ALL" || selectedEventFilter == null ||
                record.event.equals(selectedEventFilter, ignoreCase = true)
            matchesSearch && matchesEvent
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "LIVE TELEMETRY & SYSTEM ANALYTICS",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Real-time client device connections, license slot allocations, and hardware fleet telemetry.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                    )
                }

                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = Color(0xFF00E699)),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    title = "Licenses Issued",
                    value = "$totalLicenses",
                    subtitle = "$activeLicenses Active • $expiredLicenses Expired • $revokedLicenses Revoked",
                    accentColor = Color(0xFF00E699),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Connected Device Slots",
                    value = "$totalActiveDevices / $totalMaxDevices",
                    subtitle = "$utilizationPct% capacity utilized across keys",
                    accentColor = if (utilizationPct > 85) Color(0xFFFF9900) else Color(0xFF3399FF),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Hardware Fleet",
                    value = "$uniqueDevices",
                    subtitle = "Distinct unique physical devices seen",
                    accentColor = Color(0xFFAA77FF),
                    modifier = Modifier.weight(1f),
                )
                AnalyticsMetricCard(
                    title = "Telemetry Events",
                    value = "$totalHeartbeats",
                    subtitle = "Recorded heartbeats & active pings",
                    accentColor = Color(0xFFFFCC00),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Device Slot Capacity & Platform Breakdown Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Device Capacity Utilization Bar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161E))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "DEVICE SLOT CAPACITY",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF00E699), fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "$totalActiveDevices of $totalMaxDevices slots",
                            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { utilizationFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF00E699),
                        trackColor = Color(0xFF222230),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-time active client instances currently connected to license keys.",
                        style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp),
                    )
                }

                // Platform Distribution Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161E))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "PLATFORM DISTRIBUTION",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF3399FF), fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (platforms.isEmpty()) {
                        Text(
                            text = "No platform telemetry recorded yet.",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                        )
                    } else {
                        platforms.take(3).forEach { (name, count, pct) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (name.contains("Desktop", ignoreCase = true)) Icons.Rounded.Computer else Icons.Rounded.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFF88AAFF),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = name,
                                        style = TextStyle(color = Color.White, fontSize = 12.sp),
                                    )
                                }
                                Text(
                                    text = "$count pings (${(pct * 100).toInt()}%)",
                                    style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Telemetry Logs Filter Bar
        item {
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
                    Text(
                        text = "LIVE TELEMETRY STREAM (${filteredAnalytics.size} of $totalHeartbeats Records)",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    )

                    // Event Filter Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "heartbeat", "activation", "login").forEach { ev ->
                            val isSelected = (selectedEventFilter == ev) || (ev == "ALL" && (selectedEventFilter == null || selectedEventFilter == "ALL"))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00E699) else Color(0xFF222230))
                                    .clickable { selectedEventFilter = if (ev == "ALL") null else ev }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = ev.uppercase(),
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
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF00E699)),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchFilter.isEmpty()) {
                                Text("Filter by license key, device ID, or platform...", style = TextStyle(color = Color(0xFF666677), fontSize = 13.sp))
                            }
                            innerTextField()
                        },
                    )
                }
            }
        }

        // Stream Items
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
        } else if (filteredAnalytics.isEmpty()) {
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
                        text = if (searchFilter.isNotBlank()) "No telemetry matching '$searchFilter'" else "No analytics telemetry records recorded yet. User client heartbeat logs will appear here.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888899)),
                    )
                }
            }
        } else {
            items(filteredAnalytics) { record ->
                val isRevoked = record.event.equals("revoked", ignoreCase = true) || record.event.equals("error", ignoreCase = true)
                val isLive = isTelemetryLiveActive(record)
                val isOffline = !isLive && !isRevoked

                val eventName = when {
                    isRevoked -> "REVOKED"
                    isLive -> "ACTIVE"
                    record.event.equals("activation", ignoreCase = true) -> "ACTIVATION"
                    record.event.equals("login", ignoreCase = true) -> "LOGIN"
                    else -> "OFFLINE"
                }
                val eventColor = when {
                    isRevoked -> Color(0xFFFF5252)
                    isLive -> Color(0xFF00E699)
                    eventName == "ACTIVATION" || eventName == "LOGIN" -> Color(0xFF3399FF)
                    else -> Color(0xFF888899)
                }

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
                    Column(modifier = Modifier.weight(1f)) {
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
                                text = record.license_key ?: "UNKNOWN KEY",
                                style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val currentAppVersion = com.nuvio.app.core.build.AppVersionPolicy.displayVersionName

                        Text(
                            text = formatTelemetryHardware(record, currentAppVersion),
                            style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                        )
                    }

                    val statusBadgeText = when {
                        isRevoked -> "Revoked"
                        isLive -> "Active Now"
                        record.last_seen_at != null && record.last_seen_at.contains("T") ->
                            record.last_seen_at.take(19).replace("T", " ")
                        record.created_at != null && record.created_at.contains("T") ->
                            record.created_at.take(19).replace("T", " ")
                        else -> "Offline"
                    }
                    Text(
                        text = statusBadgeText,
                        style = TextStyle(
                            color = if (isLive) Color(0xFF00E699) else Color(0xFF666677),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

private fun isTelemetryLiveActive(record: LicenseAnalyticsRecord, nowMs: Long = com.nuvio.app.features.watchprogress.WatchProgressClock.nowEpochMs()): Boolean {
    val evt = record.event.orEmpty()
    if (evt.equals("revoked", ignoreCase = true) || evt.equals("offline", ignoreCase = true) || evt.equals("error", ignoreCase = true)) {
        return false
    }
    if (!evt.equals("heartbeat", ignoreCase = true)) {
        return false
    }

    val epochMs = record.last_seen_at?.toLongOrNull()
    if (epochMs != null && epochMs > 1_000_000_000_000L) {
        val diffMs = nowMs - epochMs
        return diffMs in -15_000L..(5 * 60 * 1000L)
    }

    val timeStr = record.last_seen_at?.takeIf { it.contains("T") } ?: record.created_at?.takeIf { it.contains("T") }
    if (timeStr != null) {
        val parsed = runCatching { kotlinx.datetime.Instant.parse(timeStr) }.getOrNull()
        if (parsed != null) {
            val diffMs = nowMs - parsed.toEpochMilliseconds()
            return diffMs in -15_000L..(5 * 60 * 1000L)
        }
    }

    return false
}

private fun formatTelemetryHardware(record: LicenseAnalyticsRecord, defaultVersion: String): String {
    val rawPlatform = record.platform?.trim().orEmpty()
    val rawDevice = record.device_id?.trim().orEmpty()
    val version = record.version?.takeIf { it.isNotBlank() && it != "1.1.20" } ?: defaultVersion

    val osName = when {
        rawPlatform.contains("Mac", ignoreCase = true) || rawPlatform.contains("Darwin", ignoreCase = true) -> "macOS"
        rawPlatform.contains("Windows", ignoreCase = true) || rawPlatform.contains("Win", ignoreCase = true) -> "Windows"
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
            "Desktop / Mobile"
    }

    return "Hardware: $hardwareLabel • v$version"
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
