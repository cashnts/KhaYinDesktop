package com.nuvio.app.features.license

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.normalizeManifestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdminAddonItem(
    val url: String,
    val manifest: AddonManifest? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val customName: String? = null,
    val customDescription: String? = null,
)

private data class QuickPreset(
    val name: String,
    val url: String,
    val description: String,
    val category: String = "Catalog",
)

private val POPULAR_PRESETS = listOf(
    QuickPreset("Nuvio Catalog Addon", "https://catalog.nuvio.tv/manifest.json", "Curated fast catalog of Movies, Series & Trending", "Catalog"),
    QuickPreset("Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "Official metadata catalog for Movies & Series", "Catalog"),
    QuickPreset("KhaYin Streams", "https://stream.khayin.net/manifest.json", "Official KhaYin fast streams provider", "Streams"),
    QuickPreset("KhaYin Subtitle", "https://opensubtitles-v3.strem.io/manifest.json", "Multi-language subtitles from OpenSubtitles", "Subtitles"),
    QuickPreset("CyberFlix", "https://cyberflix.elfhosted.com/manifest.json", "Curated catalog playlists from top streaming services", "Catalog"),
    QuickPreset("Anime Kitsu", "https://anime-kitsu.strem.fun/manifest.json", "Anime catalog and episode stream resolver", "Anime"),
    QuickPreset("Archive.org", "https://dev.nebulawp.org/stremio/archive.org-addon/manifest.json", "Public domain movies, documentaries & classics", "Public Domain"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAddonManagementTabContent(
    initialUrls: List<String>,
    initialDisabledAddons: List<String>,
    initialAddonMetadata: Map<String, AddonMetadataOverride> = emptyMap(),
    onSaveAndBroadcast: (presetAddons: List<String>, disabledAddons: List<String>, addonMetadata: Map<String, AddonMetadataOverride>) -> Unit,
    isBroadcasting: Boolean,
    broadcastStatus: String?,
    onCopyToast: (String) -> Unit = {},
    onNavigateToCatalogs: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val addonItems = remember { mutableStateListOf<AdminAddonItem>() }
    val disabledItems = remember { mutableStateListOf<String>() }

    var inputUrl by remember { mutableStateOf("") }
    var isInspecting by remember { mutableStateOf(false) }
    var inspectError by remember { mutableStateOf<String?>(null) }
    var inspectSuccessInfo by remember { mutableStateOf<String?>(null) }

    var newBlacklistUrl by remember { mutableStateOf("") }
    var showRawEditor by remember { mutableStateOf(false) }
    var rawTextContent by remember { mutableStateOf("") }

    // Initialize list when initialUrls change
    LaunchedEffect(initialUrls) {
        val currentUrls = addonItems.map { it.url }
        if (currentUrls != initialUrls) {
            addonItems.clear()
            initialUrls.filter { it.isNotBlank() }.forEach { url ->
                val meta = initialAddonMetadata[url]
                addonItems.add(AdminAddonItem(
                    url = url,
                    isLoading = true,
                    customName = meta?.name,
                    customDescription = meta?.description,
                ))
            }
            rawTextContent = initialUrls.joinToString("\n")

            // Load manifests in background
            addonItems.forEachIndexed { index, item ->
                scope.launch {
                    val result = AddonRepository.inspectRemoteManifest(item.url)
                    val updated = result.fold(
                        onSuccess = { manifest ->
                            item.copy(manifest = manifest, isLoading = false, errorMessage = null)
                        },
                        onFailure = { err ->
                            item.copy(manifest = null, isLoading = false, errorMessage = err.message ?: "Failed to fetch manifest")
                        },
                    )
                    if (index in addonItems.indices) {
                        addonItems[index] = updated
                    }
                }
            }
        }
    }

    LaunchedEffect(initialDisabledAddons) {
        if (disabledItems != initialDisabledAddons) {
            disabledItems.clear()
            disabledItems.addAll(initialDisabledAddons.filter { it.isNotBlank() })
        }
    }

    fun syncRawText() {
        rawTextContent = addonItems.joinToString("\n") { it.url }
    }

    fun addUrl(urlToAdd: String) {
        val normalized = runCatching { normalizeManifestUrl(urlToAdd) }.getOrNull() ?: urlToAdd.trim()
        if (normalized.isBlank()) return
        if (addonItems.any { it.url.equals(normalized, ignoreCase = true) }) {
            inspectError = "Addon is already present in the list."
            return
        }

        inspectError = null
        isInspecting = true
        scope.launch {
            val result = AddonRepository.inspectRemoteManifest(normalized)
            isInspecting = false
            result.fold(
                onSuccess = { manifest ->
                    addonItems.add(
                        AdminAddonItem(
                            url = normalized,
                            manifest = manifest,
                            isLoading = false,
                            errorMessage = null,
                        ),
                    )
                    inputUrl = ""
                    inspectSuccessInfo = "Added ${manifest.name} (v${manifest.version})"
                    syncRawText()
                },
                onFailure = { err ->
                    // Still add it with error flag so admin can see/correct it
                    addonItems.add(
                        AdminAddonItem(
                            url = normalized,
                            manifest = null,
                            isLoading = false,
                            errorMessage = err.message ?: "Unreachable manifest",
                        ),
                    )
                    inputUrl = ""
                    inspectError = "Added with error: ${err.message}"
                    syncRawText()
                },
            )
        }
    }

    fun refreshSingleAddon(index: Int) {
        if (index !in addonItems.indices) return
        val item = addonItems[index]
        addonItems[index] = item.copy(isLoading = true, errorMessage = null)
        scope.launch {
            val result = AddonRepository.inspectRemoteManifest(item.url)
            val updated = result.fold(
                onSuccess = { manifest ->
                    item.copy(manifest = manifest, isLoading = false, errorMessage = null)
                },
                onFailure = { err ->
                    item.copy(manifest = null, isLoading = false, errorMessage = err.message ?: "Unreachable")
                },
            )
            if (index in addonItems.indices) {
                addonItems[index] = updated
            }
        }
    }

    fun moveAddon(fromIndex: Int, toIndex: Int) {
        if (fromIndex in addonItems.indices && toIndex in addonItems.indices && fromIndex != toIndex) {
            val item = addonItems.removeAt(fromIndex)
            addonItems.add(toIndex, item)
            syncRawText()
        }
    }

    fun removeAddon(index: Int) {
        if (index in addonItems.indices) {
            val removed = addonItems.removeAt(index)
            inspectSuccessInfo = "Removed ${removed.manifest?.name ?: removed.url}"
            syncRawText()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. OVERVIEW & BROADCAST ACTION HEADER ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF00E699).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = Color(0xFF00E699),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "ADDON MANAGEMENT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp,
                                ),
                            )
                            Text(
                                text = "Preset addons configured here are automatically synchronized and installed across all user clients.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                            )
                        }
                    }

                    // Save & Broadcast Button
                    Button(
                        onClick = {
                            onSaveAndBroadcast(
                                addonItems.map { it.url },
                                disabledItems.toList(),
                                addonItems
                                    .filter { !it.customName.isNullOrBlank() || !it.customDescription.isNullOrBlank() }
                                    .associate { item ->
                                        item.url to AddonMetadataOverride(
                                            name = item.customName?.takeIf { it.isNotBlank() },
                                            description = item.customDescription?.takeIf { it.isNotBlank() },
                                        )
                                    },
                            )
                        },
                        enabled = !isBroadcasting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E699),
                            disabledContainerColor = Color(0xFF00E699).copy(alpha = 0.4f),
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp),
                    ) {
                        if (isBroadcasting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Broadcasting...",
                                style = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            )
                        } else {
                            Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Broadcast to All Clients",
                                style = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val verifiedCount = addonItems.count { it.manifest != null }
                    val errorCount = addonItems.count { it.errorMessage != null }
                    val totalCatalogs = addonItems.sumOf { it.manifest?.catalogs?.size ?: 0 }

                    StatBadge(label = "Configured Addons", value = "${addonItems.size}", color = Color(0xFF00E699))
                    StatBadge(label = "Online", value = "$verifiedCount", color = Color(0xFF71BDE8))
                    StatBadge(label = "Total Catalogs", value = "$totalCatalogs", color = Color(0xFFFFB800))
                    if (errorCount > 0) {
                        StatBadge(label = "Issues Detected", value = "$errorCount", color = Color(0xFFFF4D4D))
                    }
                    if (disabledItems.isNotEmpty()) {
                        StatBadge(label = "Blacklisted Addons", value = "${disabledItems.size}", color = Color(0xFFE056FD))
                    }
                }

                // Broadcast status banner
                broadcastStatus?.let { status ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D2818))
                            .border(1.dp, Color(0xFF00E699).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = status, style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // --- 2. ADD ADDON WORKFLOW ---
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
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSTALL NEW MANIFEST TO BUNDLE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input + Inspect Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, Color(0xFF323244), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = inputUrl,
                            onValueChange = {
                                inputUrl = it
                                inspectError = null
                                inspectSuccessInfo = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF00E699)),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (inputUrl.isEmpty()) {
                                    Text(
                                        text = "Paste manifest URL (e.g. https://.../manifest.json or stremio://...)",
                                        color = Color(0xFF555566),
                                        fontSize = 13.sp,
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }

                    // Paste Button
                    Button(
                        onClick = {
                            val pasted = clipboardManager.getText()?.text?.trim()
                            if (!pasted.isNullOrBlank()) {
                                inputUrl = pasted
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Paste", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste", color = Color.White, fontSize = 12.sp)
                    }

                    // Add / Inspect Button
                    Button(
                        onClick = { addUrl(inputUrl) },
                        enabled = inputUrl.isNotBlank() && !isInspecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E699),
                            disabledContainerColor = Color(0xFF00E699).copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp),
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspecting...", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspect & Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Error / Success feedback
                inspectError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = err,
                        style = TextStyle(color = Color(0xFFFF4D4D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                inspectSuccessInfo?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = msg,
                        style = TextStyle(color = Color(0xFF00E699), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF22222E))
                Spacer(modifier = Modifier.height(14.dp))

                // Quick Presets library
                Text(
                    text = "CATALOGS",
                    style = TextStyle(color = Color(0xFF777788), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    POPULAR_PRESETS.forEach { preset ->
                        val isInstalled = addonItems.any { it.url.equals(preset.url, ignoreCase = true) }
                        val categoryColor = when (preset.category.lowercase()) {
                            "catalog" -> Color(0xFFFFB800)
                            "streams" -> Color(0xFF00E699)
                            "subtitles" -> Color(0xFF71BDE8)
                            "anime" -> Color(0xFFFF9F43)
                            else -> Color(0xFFB57EDC)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isInstalled) Color(0xFF13221A) else Color(0xFF1A1A24))
                                .border(
                                    1.dp,
                                    if (isInstalled) Color(0xFF00E699).copy(alpha = 0.5f) else Color(0xFF2E2E3E),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = !isInstalled) { addUrl(preset.url) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isInstalled) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF71BDE8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = preset.name,
                                            style = TextStyle(
                                                color = if (isInstalled) Color(0xFF00E699) else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(categoryColor.copy(alpha = 0.15f))
                                                .border(1.dp, categoryColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = preset.category,
                                                style = TextStyle(color = categoryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = preset.description,
                                        style = TextStyle(color = Color(0xFF888899), fontSize = 11.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. CURRENT ADDONS LIST HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CURRENT BROADCAST MANIFESTS (${addonItems.size})",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• Order determines catalog placement",
                        style = TextStyle(color = Color(0xFF777788), fontSize = 12.sp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Manage Catalogs shortcut button
                    val totalDiscoveredCatalogs = addonItems.sumOf { it.manifest?.catalogs?.size ?: 0 }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF281E10))
                            .border(1.dp, Color(0xFFFFB800).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onNavigateToCatalogs() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manage Catalogs ($totalDiscoveredCatalogs)",
                                style = TextStyle(color = Color(0xFFFFB800), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    // Raw editor toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showRawEditor) Color(0xFF00E699).copy(alpha = 0.2f) else Color(0xFF1E1E28))
                            .border(1.dp, if (showRawEditor) Color(0xFF00E699) else Color(0xFF333344), RoundedCornerShape(6.dp))
                            .clickable { showRawEditor = !showRawEditor }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Code,
                                contentDescription = null,
                                tint = if (showRawEditor) Color(0xFF00E699) else Color(0xFFAAAAAA),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showRawEditor) "Hide Raw Editor" else "Raw URLs Editor",
                                style = TextStyle(
                                    color = if (showRawEditor) Color(0xFF00E699) else Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }

                    // Refresh all button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1E28))
                            .border(1.dp, Color(0xFF333344), RoundedCornerShape(6.dp))
                            .clickable {
                                addonItems.indices.forEach { refreshSingleAddon(it) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color(0xFF71BDE8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-verify All", style = TextStyle(color = Color(0xFF71BDE8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // Raw Editor Section (collapsible)
        if (showRawEditor) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111118))
                        .border(1.dp, Color(0xFF00E699).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "BULK MANIFEST URL EDITOR (ONE PER LINE):",
                        style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF08080C))
                            .border(1.dp, Color(0xFF222230), RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    ) {
                        BasicTextField(
                            value = rawTextContent,
                            onValueChange = { rawTextContent = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFF00E699)),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                val lines = rawTextContent.lines().map { it.trim() }.filter { it.isNotBlank() }
                                addonItems.clear()
                                lines.forEach { url ->
                                    addonItems.add(AdminAddonItem(url = url, isLoading = true))
                                }
                                addonItems.forEachIndexed { index, _ -> refreshSingleAddon(index) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E699)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text("Apply to Visual List", style = TextStyle(color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Empty state
        if (addonItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF16161E))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(14.dp))
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFF444455), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No broadcast addons configured yet.",
                            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Use the input field above or click any quick-add preset to start assembling your manifest bundle.",
                            style = TextStyle(color = Color(0xFF777788), fontSize = 13.sp),
                        )
                    }
                }
            }
        } else {
            // --- 4. ADDON CARDS ---
            itemsIndexed(addonItems) { index, item ->
                AdminAddonCard(
                    index = index,
                    totalCount = addonItems.size,
                    item = item,
                    onMoveUp = if (index > 0) { { moveAddon(index, index - 1) } } else null,
                    onMoveDown = if (index < addonItems.lastIndex) { { moveAddon(index, index + 1) } } else null,
                    onRefresh = { refreshSingleAddon(index) },
                    onRemove = { removeAddon(index) },
                    onCopyUrl = {
                        clipboardManager.setText(AnnotatedString(item.url))
                        onCopyToast("Copied manifest URL to clipboard")
                    },
                    onUpdateMetadata = { name, description ->
                        if (index in addonItems.indices) {
                            addonItems[index] = addonItems[index].copy(
                                customName = name.takeIf { it.isNotBlank() },
                                customDescription = description.takeIf { it.isNotBlank() },
                            )
                        }
                    },
                )
            }
        }

        // --- 5. GLOBAL BLACKLIST SECTION ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF331E24), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BLACKLIST ADDON",
                        style = TextStyle(color = Color(0xFFFF4D4D), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Addons containing any of these keywords or manifest URLs will be immediately uninstalled from all client applications upon startup or sync.",
                    style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Add to blacklist input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, Color(0xFF382226), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = newBlacklistUrl,
                            onValueChange = { newBlacklistUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFFFF4D4D)),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (newBlacklistUrl.isEmpty()) {
                                    Text("Addon domain or manifest URL to blacklist (e.g. malicious-addon.com)", color = Color(0xFF554448), fontSize = 12.sp)
                                }
                                inner()
                            },
                        )
                    }

                    Button(
                        onClick = {
                            val trimmed = newBlacklistUrl.trim()
                            if (trimmed.isNotBlank() && trimmed !in disabledItems) {
                                disabledItems.add(trimmed)
                                newBlacklistUrl = ""
                            }
                        },
                        enabled = newBlacklistUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text("Add to Blacklist", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                }

                if (disabledItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        disabledItems.forEachIndexed { idx, item ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2E151A))
                                    .border(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item,
                                        style = TextStyle(color = Color(0xFFFF8888), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFFF4D4D),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { disabledItems.removeAt(idx) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminAddonCard(
    index: Int,
    totalCount: Int,
    item: AdminAddonItem,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onCopyUrl: () -> Unit,
    onUpdateMetadata: (name: String, description: String) -> Unit,
) {
    val manifest = item.manifest
    var showEditPanel by remember(item.url) { mutableStateOf(false) }
    var editName by remember(item.url) {
        mutableStateOf(item.customName ?: manifest?.name ?: "")
    }
    var editDescription by remember(item.url) {
        mutableStateOf(item.customDescription ?: manifest?.description ?: "")
    }
    // Keep edit fields in sync when manifest first loads
    LaunchedEffect(manifest?.name, manifest?.description) {
        if (editName.isBlank()) editName = manifest?.name ?: ""
        if (editDescription.isBlank()) editDescription = manifest?.description ?: ""
    }
    val hasOverride = !item.customName.isNullOrBlank() || !item.customDescription.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161E))
            .border(
                1.dp,
                when {
                    showEditPanel -> Color(0xFF7B5EA7)
                    hasOverride -> Color(0xFF5B3C8A).copy(alpha = 0.6f)
                    item.errorMessage != null -> Color(0xFFFF4D4D).copy(alpha = 0.4f)
                    else -> Color(0xFF262633)
                },
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Logo Container (fitted, padded, no clipping)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E2A))
                    .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(12.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!manifest?.logoUrl.isNullOrBlank()) {
                    NuvioAsyncImage(
                        model = manifest.logoUrl,
                        contentDescription = manifest.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = if (manifest != null) Color(0xFF00E699) else Color(0xFF666677),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Priority Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E699).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00E699).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                            )
                        }

                        Text(
                            text = manifest?.name ?: item.url.substringBefore("/manifest.json").substringAfterLast("/").ifEmpty { "Addon" },
                            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        manifest?.version?.let { ver ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF252535))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "v$ver",
                                    style = TextStyle(color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }

                        // Status pill
                        when {
                            item.isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF332B10))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("Verifying...", color = Color(0xFFFFB800), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            item.errorMessage != null -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF331418))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("Unreachable", color = Color(0xFFFF4D4D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            manifest != null -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF102E20))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("Active", color = Color(0xFF00E699), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Move Up
                        IconButton(
                            onClick = { onMoveUp?.invoke() },
                            enabled = onMoveUp != null,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "Move Up",
                                tint = if (onMoveUp != null) Color(0xFFAAAAAA) else Color(0xFF444455),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Move Down
                        IconButton(
                            onClick = { onMoveDown?.invoke() },
                            enabled = onMoveDown != null,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ArrowDownward,
                                contentDescription = "Move Down",
                                tint = if (onMoveDown != null) Color(0xFFAAAAAA) else Color(0xFF444455),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Refresh / Check
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color(0xFF71BDE8), modifier = Modifier.size(16.dp))
                        }

                        // Copy URL
                        IconButton(
                            onClick = onCopyUrl,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy URL", tint = Color(0xFF888899), modifier = Modifier.size(16.dp))
                        }

                        // Edit Metadata
                        IconButton(
                            onClick = { showEditPanel = !showEditPanel },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit Metadata",
                                tint = if (showEditPanel || hasOverride) Color(0xFF7B5EA7) else Color(0xFF888899),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Remove
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove Addon", tint = Color(0xFFFF4D4D), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Description
                manifest?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = TextStyle(color = Color(0xFF9E9EA7), fontSize = 12.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Error message if present
                item.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: $err",
                        style = TextStyle(color = Color(0xFFFF6666), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tags & Resources row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    manifest?.resources?.forEach { res ->
                        ResourcePill(name = res.name, color = Color(0xFF00E699))
                    }
                    manifest?.types?.forEach { type ->
                        ResourcePill(name = type.replaceFirstChar(Char::uppercase), color = Color(0xFF71BDE8))
                    }
                    if (manifest?.catalogs?.isNotEmpty() == true) {
                        ResourcePill(name = "${manifest.catalogs.size} Catalogs", color = Color(0xFFFFB800))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // URL Line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.url,
                        style = TextStyle(
                            color = Color(0xFF666677),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Override badge
                if (hasOverride) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF7B5EA7).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF7B5EA7).copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Metadata Override Active",
                                style = TextStyle(color = Color(0xFFB197FC), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        item.customName?.let {
                            Text(text = "Name: \"$it\"", style = TextStyle(color = Color(0xFF9D80CC), fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // ── Inline Metadata Edit Panel ──────────────────────────────────────
        AnimatedVisibility(
            visible = showEditPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF7B5EA7).copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF7B5EA7), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "METADATA OVERRIDE",
                        style = TextStyle(color = Color(0xFF7B5EA7), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Overrides what clients see — does not modify the remote manifest",
                        style = TextStyle(color = Color(0xFF666677), fontSize = 10.sp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Name field
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DISPLAY NAME",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .border(1.dp, Color(0xFF7B5EA7).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFF7B5EA7)),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (editName.isEmpty()) {
                                        Text(
                                            text = manifest?.name ?: "e.g. My Custom Addon",
                                            style = TextStyle(color = Color(0xFF444455), fontSize = 13.sp),
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }

                    // Description field
                    Column(modifier = Modifier.weight(2f)) {
                        Text(
                            text = "DESCRIPTION",
                            style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .border(1.dp, Color(0xFF7B5EA7).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFF7B5EA7)),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (editDescription.isEmpty()) {
                                        Text(
                                            text = manifest?.description ?: "Override description shown to all users…",
                                            style = TextStyle(color = Color(0xFF444455), fontSize = 13.sp),
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            onUpdateMetadata(editName.trim(), editDescription.trim())
                            showEditPanel = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5EA7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Override", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }

                    if (hasOverride) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E28))
                                .border(1.dp, Color(0xFF444455), RoundedCornerShape(8.dp))
                                .clickable {
                                    editName = manifest?.name ?: ""
                                    editDescription = manifest?.description ?: ""
                                    onUpdateMetadata("", "")
                                    showEditPanel = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = "Reset to Manifest Default",
                                style = TextStyle(color = Color(0xFF888899), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E28))
                            .clickable { showEditPanel = false }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = "Cancel",
                            style = TextStyle(color = Color(0xFF666677), fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F1F2C))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            Text(text = value, style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
            Text(text = label, style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun ResourcePill(name: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = name,
            style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
    }
}
