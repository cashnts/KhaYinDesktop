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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AdminLicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adminPassword by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var licenses by remember { mutableStateOf<List<LicenseInfo>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Generator Form State
    var customerName by remember { mutableStateOf("") }
    var durationDays by remember { mutableStateOf(30) }
    var maxDevices by remember { mutableStateOf(1) }
    var tier by remember { mutableStateOf("standard") }
    var notes by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var newlyCreatedLicense by remember { mutableStateOf<LicenseInfo?>(null) }
    var actionToast by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun refreshList() {
        if (!isUnlocked || adminPassword.isBlank()) return
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

    if (!isUnlocked) {
        // Admin Unlock Gate
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D11)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color(0xFF00E699),
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Administrator Portal",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter server admin password to generate and manage client license keys.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF888899),
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Password input
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161E))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "ADMIN PASSWORD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF00E699),
                            fontWeight = FontWeight.Bold,
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = adminPassword,
                            onValueChange = {
                                adminPassword = it
                                authError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF00E699)),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (adminPassword.isNotBlank() && !isAuthenticating) {
                                        isAuthenticating = true
                                        scope.launch {
                                            LicenseRepository.adminListLicenses(adminPassword).fold(
                                                onSuccess = { list ->
                                                    isAuthenticating = false
                                                    licenses = list
                                                    isUnlocked = true
                                                },
                                                onFailure = { err ->
                                                    isAuthenticating = false
                                                    authError = err.message ?: "Invalid admin password"
                                                },
                                            )
                                        }
                                    }
                                },
                            ),
                        )
                    }

                    if (authError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = authError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF5252)),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (adminPassword.isBlank()) return@Button
                            isAuthenticating = true
                            scope.launch {
                                LicenseRepository.adminListLicenses(adminPassword).fold(
                                    onSuccess = { list ->
                                        isAuthenticating = false
                                        licenses = list
                                        isUnlocked = true
                                    },
                                    onFailure = { err ->
                                        isAuthenticating = false
                                        authError = err.message ?: "Invalid admin password"
                                    },
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E699),
                            contentColor = Color.Black,
                        ),
                        enabled = !isAuthenticating,
                    ) {
                        if (isAuthenticating) {
                            NuvioLoadingIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                        } else {
                            Text("Unlock Admin Portal", style = TextStyle(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Back",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                )
            }
        }
        return
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
                            text = "Admin License Manager",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                        Text(
                            text = "Generate and configure license keys",
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

            // Main Content: Scrollable
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Customer name input
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
                                onValueChange = { customerName = it },
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

                        // Duration Selector Chips
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
                                        .clickable { durationDays = days }
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

                        // Max Devices Selector
                        Text("Max Allowed Devices", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA)))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(1, 2, 3, 5, 10).forEach { devs ->
                                val selected = maxDevices == devs
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00E699) else Color(0xFF0F0F16))
                                        .border(1.dp, if (selected) Color(0xFF00E699) else Color(0xFF323244), RoundedCornerShape(8.dp))
                                        .clickable { maxDevices = devs }
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
                            onClick = {
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

                        // Display Newly Generated Key
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
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(lic.key))
                                            actionToast = "Copied to clipboard!"
                                        },
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

                        // Search box
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
                                onValueChange = { searchQuery = it },
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

                // 3. License List Items
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
                                text = if (isLoadingList) "Loading licenses..." else "No licenses found",
                                style = TextStyle(color = Color(0xFF666677), fontSize = 14.sp),
                            )
                        }
                    }
                } else {
                    items(filteredList) { lic ->
                        LicenseCardItem(
                            license = lic,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(lic.key))
                                actionToast = "Copied ${lic.key}"
                            },
                            onExtend = {
                                scope.launch {
                                    LicenseRepository.adminExtendLicense(adminPassword, lic.key, 30).fold(
                                        onSuccess = {
                                            actionToast = "Extended ${lic.key} by 30 days"
                                            refreshList()
                                        },
                                        onFailure = { err -> actionToast = "Error: ${err.message}" },
                                    )
                                }
                            },
                            onRevoke = {
                                scope.launch {
                                    LicenseRepository.adminRevokeLicense(adminPassword, lic.key).fold(
                                        onSuccess = {
                                            actionToast = "Revoked ${lic.key}"
                                            refreshList()
                                        },
                                        onFailure = { err -> actionToast = "Error: ${err.message}" },
                                    )
                                }
                            },
                        )
                    }
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
) {
    val isRevoked = license.status == "revoked"
    val isExpired = license.status == "expired"

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
            Column {
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
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${license.customerName ?: "Member"} • ${license.tier?.uppercase() ?: "STANDARD"} • ${if (license.expiresAt != null) "Expires " + license.expiresAt.take(10) else "Lifetime"} • ${license.maxDevices} max device(s)",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899), fontSize = 12.sp),
                )
            }

            // Quick Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onCopy,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230), contentColor = Color.White),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
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
                }
            }
        }
    }
}
