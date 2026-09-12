package com.nuvio.app.features.license

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.addons.AddonRepository
import kotlinx.coroutines.launch

data class AdminCatalogItem(
    val key: String,
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val type: String,
    val defaultTitle: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = false,
    val order: Int = 0,
)

private const val HERO_SELECTION_LIMIT = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCatalogManagementTabContent(
    configuredAddonUrls: List<String>,
    initialPresetCatalogs: List<PresetCatalogConfig>,
    initialHeroEnabled: Boolean,
    initialShowCatalogType: Boolean,
    initialHideUnreleased: Boolean,
    onSaveAndBroadcastCatalogs: (
        catalogs: List<PresetCatalogConfig>,
        heroEnabled: Boolean,
        showCatalogType: Boolean,
        hideUnreleased: Boolean,
    ) -> Unit,
    isBroadcasting: Boolean,
    broadcastStatus: String?,
    onCopyToast: (String) -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val catalogItems = remember { mutableStateListOf<AdminCatalogItem>() }
    var heroCarouselEnabled by remember { mutableStateOf(initialHeroEnabled) }
    var showCatalogType by remember { mutableStateOf(initialShowCatalogType) }
    var hideUnreleasedContent by remember { mutableStateOf(initialHideUnreleased) }

    var isLoadingManifests by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("all") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // Sync state when initial props update
    LaunchedEffect(initialHeroEnabled, initialShowCatalogType, initialHideUnreleased) {
        heroCarouselEnabled = initialHeroEnabled
        showCatalogType = initialShowCatalogType
        hideUnreleasedContent = initialHideUnreleased
    }

    // Inspect active addons and aggregate all catalogs
    LaunchedEffect(configuredAddonUrls, initialPresetCatalogs) {
        isLoadingManifests = true
        scope.launch {
            val presetByKey = initialPresetCatalogs.associateBy { it.key }
            val extracted = mutableListOf<AdminCatalogItem>()
            val processedKeys = mutableSetOf<String>()

            // 1. Fetch remote manifests for all configured URLs
            configuredAddonUrls.filter { it.isNotBlank() }.forEach { url ->
                val result = AddonRepository.inspectRemoteManifest(url)
                result.getOrNull()?.let { manifest ->
                    manifest.catalogs.forEach { catalog ->
                        val key = "${manifest.id}:${catalog.type}:${catalog.id}"
                        if (processedKeys.add(key)) {
                            val preset = presetByKey[key]
                            val defaultTitle = "${catalog.name} - ${catalog.type.replaceFirstChar(Char::uppercase)}"
                            extracted.add(
                                AdminCatalogItem(
                                    key = key,
                                    addonId = manifest.id,
                                    addonName = manifest.name,
                                    catalogId = catalog.id,
                                    type = catalog.type,
                                    defaultTitle = defaultTitle,
                                    customTitle = preset?.customTitle ?: "",
                                    enabled = preset?.enabled ?: true,
                                    heroSourceEnabled = preset?.heroSourceEnabled ?: false,
                                    order = preset?.order ?: (1000 + extracted.size),
                                )
                            )
                        }
                    }
                }
            }

            // 2. Include any existing preset catalogs that might belong to built-in or cached sources
            initialPresetCatalogs.forEach { preset ->
                if (processedKeys.add(preset.key)) {
                    extracted.add(
                        AdminCatalogItem(
                            key = preset.key,
                            addonId = preset.addonId,
                            addonName = preset.addonName.ifBlank { "Addon" },
                            catalogId = preset.catalogId,
                            type = preset.type,
                            defaultTitle = preset.defaultTitle,
                            customTitle = preset.customTitle,
                            enabled = preset.enabled,
                            heroSourceEnabled = preset.heroSourceEnabled,
                            order = preset.order,
                        )
                    )
                }
            }

            // 3. Sort by order
            extracted.sortBy { it.order }
            val reindexed = extracted.mapIndexed { idx, item -> item.copy(order = idx) }

            catalogItems.clear()
            catalogItems.addAll(reindexed)
            isLoadingManifests = false
        }
    }

    fun moveCatalog(fromIndex: Int, toIndex: Int) {
        if (fromIndex in catalogItems.indices && toIndex in catalogItems.indices && fromIndex != toIndex) {
            val item = catalogItems.removeAt(fromIndex)
            catalogItems.add(toIndex, item)
            catalogItems.forEachIndexed { idx, itm ->
                catalogItems[idx] = itm.copy(order = idx)
            }
            hasUnsavedChanges = true
        }
    }

    fun toggleEnabled(key: String) {
        val idx = catalogItems.indexOfFirst { it.key == key }
        if (idx != -1) {
            val curr = catalogItems[idx]
            catalogItems[idx] = curr.copy(enabled = !curr.enabled)
            hasUnsavedChanges = true
        }
    }

    fun toggleHeroSource(key: String) {
        val idx = catalogItems.indexOfFirst { it.key == key }
        if (idx != -1) {
            val curr = catalogItems[idx]
            val currentlySelectedCount = catalogItems.count { it.heroSourceEnabled }
            if (!curr.heroSourceEnabled && currentlySelectedCount >= HERO_SELECTION_LIMIT) {
                onCopyToast("Maximum $HERO_SELECTION_LIMIT hero carousel sources allowed")
                return
            }
            catalogItems[idx] = curr.copy(heroSourceEnabled = !curr.heroSourceEnabled)
            hasUnsavedChanges = true
        }
    }

    fun updateCustomTitle(key: String, newTitle: String) {
        val idx = catalogItems.indexOfFirst { it.key == key }
        if (idx != -1) {
            catalogItems[idx] = catalogItems[idx].copy(customTitle = newTitle)
            hasUnsavedChanges = true
        }
    }

    fun resetToDefaults() {
        catalogItems.forEachIndexed { idx, itm ->
            catalogItems[idx] = itm.copy(
                customTitle = "",
                enabled = true,
                heroSourceEnabled = idx < HERO_SELECTION_LIMIT,
                order = idx,
            )
        }
        heroCarouselEnabled = true
        showCatalogType = true
        hideUnreleasedContent = false
        hasUnsavedChanges = true
    }

    val selectedHeroCount = catalogItems.count { it.heroSourceEnabled }
    val enabledCatalogCount = catalogItems.count { it.enabled }
    val distinctAddonsCount = catalogItems.map { it.addonName }.distinct().size

    // Available types for filter chips
    val allTypes = listOf("all") + catalogItems.map { it.type.lowercase() }.distinct().sorted()

    // Filtered items
    val filteredItems = catalogItems.filter { item ->
        val matchesSearch = searchQuery.isBlank() ||
            item.defaultTitle.contains(searchQuery, ignoreCase = true) ||
            item.customTitle.contains(searchQuery, ignoreCase = true) ||
            item.addonName.contains(searchQuery, ignoreCase = true) ||
            item.type.contains(searchQuery, ignoreCase = true)
        val matchesType = selectedTypeFilter == "all" || item.type.equals(selectedTypeFilter, ignoreCase = true)
        matchesSearch && matchesType
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. OVERVIEW & BROADCAST HEADER ---
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
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFB800).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "CATALOG MANAGEMENT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp,
                                ),
                            )
                            Text(
                                text = "Order, custom titles, and hero source placement configured here apply to every user's home screen.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888899)),
                            )
                        }
                    }

                    // Broadcast Catalogs Action Button
                    Button(
                        onClick = {
                            val configs = catalogItems.mapIndexed { idx, itm ->
                                PresetCatalogConfig(
                                    key = itm.key,
                                    addonId = itm.addonId,
                                    addonName = itm.addonName,
                                    catalogId = itm.catalogId,
                                    type = itm.type,
                                    defaultTitle = itm.defaultTitle,
                                    customTitle = itm.customTitle,
                                    enabled = itm.enabled,
                                    heroSourceEnabled = itm.heroSourceEnabled,
                                    order = idx,
                                )
                            }
                            onSaveAndBroadcastCatalogs(
                                configs,
                                heroCarouselEnabled,
                                showCatalogType,
                                hideUnreleasedContent,
                            )
                            hasUnsavedChanges = false
                        },
                        enabled = !isBroadcasting && catalogItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E699),
                            disabledContainerColor = Color(0xFF00E699).copy(alpha = 0.35f),
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
                            Text("Broadcasting...", style = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                        } else {
                            Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasUnsavedChanges) "Broadcast Changes (${catalogItems.size} Catalogs)" else "Broadcast to All Clients",
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
                    CatalogStatBadge(label = "Total Catalogs", value = "${catalogItems.size}", color = Color(0xFFFFB800))
                    CatalogStatBadge(label = "Active / Enabled", value = "$enabledCatalogCount", color = Color(0xFF00E699))
                    CatalogStatBadge(label = "Hero Sources", value = "$selectedHeroCount/$HERO_SELECTION_LIMIT", color = Color(0xFF71BDE8))
                    CatalogStatBadge(label = "Addon Sources", value = "$distinctAddonsCount", color = Color(0xFFB57EDC))

                    if (hasUnsavedChanges) {
                        CatalogStatBadge(label = "Status", value = "Unsaved Changes", color = Color(0xFFFF9F43))
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

        // --- 2. GLOBAL HOME DISPLAY SETTINGS ---
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
                    text = "GLOBAL HOME SCREEN DISPLAY SETTINGS",
                    style = TextStyle(color = Color(0xFF777788), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Hero Carousel Toggle
                    GlobalToggleOption(
                        title = "Hero Carousel",
                        subtitle = "Feature selected catalogs prominently at top",
                        checked = heroCarouselEnabled,
                        onCheckedChange = {
                            heroCarouselEnabled = it
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier.weight(1f),
                    )

                    // Show Catalog Type Toggle
                    GlobalToggleOption(
                        title = "Show Media Type",
                        subtitle = "Display type badge (Movie, Series) on headers",
                        checked = showCatalogType,
                        onCheckedChange = {
                            showCatalogType = it
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier.weight(1f),
                    )

                    // Hide Unreleased Content Toggle
                    GlobalToggleOption(
                        title = "Hide Unreleased",
                        subtitle = "Filter out future unreleased media by default",
                        checked = hideUnreleasedContent,
                        onCheckedChange = {
                            hideUnreleasedContent = it
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // --- 3. FILTER & SEARCH TOOLBAR ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "CATALOG PLACEMENT & ORDER (${catalogItems.size})",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    )
                    Text(
                        text = "• Drag or use arrows to adjust placement for all users",
                        style = TextStyle(color = Color(0xFF777788), fontSize = 12.sp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Reset to defaults
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1E28))
                            .border(1.dp, Color(0xFF333344), RoundedCornerShape(6.dp))
                            .clickable { resetToDefaults() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Reset to Defaults", style = TextStyle(color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                    }

                    // Shortcut to Addon Management
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1B2E24))
                            .border(1.dp, Color(0xFF00E699).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable { onNavigateToAddons() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Addon Sources", style = TextStyle(color = Color(0xFF00E699), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Search & Type Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Search field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF262633), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF777788), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFF00E699)),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Filter catalogs by name, addon, or type...", color = Color(0xFF555566), fontSize = 12.sp)
                                }
                                inner()
                            },
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF777788),
                                modifier = Modifier.size(14.dp).clickable { searchQuery = "" },
                            )
                        }
                    }
                }

                // Type filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTypes.forEach { type ->
                        val selected = selectedTypeFilter == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF00E699).copy(alpha = 0.2f) else Color(0xFF1E1E28))
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF00E699) else Color(0xFF333344),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable { selectedTypeFilter = type }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = type.replaceFirstChar(Char::uppercase),
                                style = TextStyle(
                                    color = if (selected) Color(0xFF00E699) else Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // --- 4. CATALOG CARDS LIST ---
        if (isLoadingManifests) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E699), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Inspecting remote manifests & assembling catalogs...", color = Color(0xFF888899), fontSize = 13.sp)
                    }
                }
            }
        } else if (catalogItems.isEmpty()) {
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
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = Color(0xFF444455), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No catalogs discovered yet.",
                            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configure addon manifests in the Addon Management tab to automatically populate browsable catalogs.",
                            style = TextStyle(color = Color(0xFF777788), fontSize = 13.sp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToAddons,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E699)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Go to Addon Management", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No catalogs match your search filter.", color = Color(0xFF777788), fontSize = 13.sp)
                }
            }
        } else {
            itemsIndexed(filteredItems, key = { _, itm -> itm.key }) { index, item ->
                val realIndex = catalogItems.indexOfFirst { it.key == item.key }
                AdminCatalogRowCard(
                    displayIndex = realIndex + 1,
                    item = item,
                    canMoveUp = realIndex > 0,
                    canMoveDown = realIndex < catalogItems.lastIndex,
                    onMoveUp = { moveCatalog(realIndex, realIndex - 1) },
                    onMoveDown = { moveCatalog(realIndex, realIndex + 1) },
                    onToggleEnabled = { toggleEnabled(item.key) },
                    onToggleHero = { toggleHeroSource(item.key) },
                    onCustomTitleChange = { updateCustomTitle(item.key, it) },
                )
            }
        }
    }
}

@Composable
private fun AdminCatalogRowCard(
    displayIndex: Int,
    item: AdminCatalogItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleHero: () -> Unit,
    onCustomTitleChange: (String) -> Unit,
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleInput by remember(item.customTitle) { mutableStateOf(item.customTitle) }

    val typeColor = when (item.type.lowercase()) {
        "movie" -> Color(0xFF71BDE8)
        "series" -> Color(0xFFB57EDC)
        "anime" -> Color(0xFFFF9F43)
        else -> Color(0xFF00E699)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.enabled) Color(0xFF16161E) else Color(0xFF111116))
            .border(
                1.dp,
                if (item.heroSourceEnabled) Color(0xFFFFB800).copy(alpha = 0.4f)
                else if (item.enabled) Color(0xFF262633) else Color(0xFF1D1D26),
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Index + Name + Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Priority Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.enabled) Color(0xFF00E699).copy(alpha = 0.15f) else Color(0xFF222230))
                        .border(
                            1.dp,
                            if (item.enabled) Color(0xFF00E699).copy(alpha = 0.3f) else Color(0xFF333344),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "#$displayIndex",
                        style = TextStyle(
                            color = if (item.enabled) Color(0xFF00E699) else Color(0xFF666677),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Effective Display Title
                        Text(
                            text = item.customTitle.ifBlank { item.defaultTitle },
                            style = TextStyle(
                                color = if (item.enabled) Color.White else Color(0xFF666677),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (item.customTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${item.defaultTitle})",
                                style = TextStyle(color = Color(0xFF666677), fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Provider Addon Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF20202E))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = item.addonName,
                                style = TextStyle(color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }

                        // Media Type Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(typeColor.copy(alpha = 0.15f))
                                .border(1.dp, typeColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = item.type.replaceFirstChar(Char::uppercase),
                                style = TextStyle(color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            )
                        }

                        // Hero Source Pill if enabled
                        if (item.heroSourceEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFFB800).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFFFB800).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "★ HERO CAROUSEL",
                                    style = TextStyle(color = Color(0xFFFFB800), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                )
                            }
                        }

                        // Identifier
                        Text(
                            text = item.key,
                            style = TextStyle(color = Color(0xFF555566), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Right: Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Edit custom title button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isEditingTitle || item.customTitle.isNotBlank()) Color(0xFF222234) else Color(0xFF1A1A24))
                        .border(1.dp, if (item.customTitle.isNotBlank()) Color(0xFF00E699).copy(alpha = 0.3f) else Color(0xFF2E2E3E), RoundedCornerShape(6.dp))
                        .clickable { isEditingTitle = !isEditingTitle }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (item.customTitle.isNotBlank()) "Title: ${item.customTitle}" else "Rename",
                        style = TextStyle(
                            color = if (item.customTitle.isNotBlank()) Color(0xFF00E699) else Color(0xFF888899),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                // Hero Source Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.heroSourceEnabled) Color(0xFFFFB800).copy(alpha = 0.2f) else Color(0xFF1E1E28))
                        .border(
                            1.dp,
                            if (item.heroSourceEnabled) Color(0xFFFFB800) else Color(0xFF333344),
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onToggleHero() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (item.heroSourceEnabled) "★ Hero" else "☆ Hero",
                        style = TextStyle(
                            color = if (item.heroSourceEnabled) Color(0xFFFFB800) else Color(0xFF777788),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                // Enabled Toggle Switch
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00E699),
                        uncheckedThumbColor = Color(0xFF666677),
                        uncheckedTrackColor = Color(0xFF20202A),
                    ),
                    modifier = Modifier.size(width = 38.dp, height = 24.dp),
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (canMoveUp) Color(0xFFAAAAAA) else Color(0xFF333344),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (canMoveDown) Color(0xFFAAAAAA) else Color(0xFF333344),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Inline Title Editor Box
        AnimatedVisibility(visible = isEditingTitle) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF222230))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Custom Display Title for all users:", color = Color(0xFF888899), fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0A10))
                            .border(1.dp, Color(0xFF333348), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFF00E699)),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (titleInput.isEmpty()) {
                                    Text(item.defaultTitle, color = Color(0xFF555566), fontSize = 12.sp)
                                }
                                inner()
                            },
                        )
                    }

                    // Save custom title button
                    Button(
                        onClick = {
                            onCustomTitleChange(titleInput.trim())
                            isEditingTitle = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E699)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("Apply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (titleInput.isNotBlank()) {
                        Button(
                            onClick = {
                                titleInput = ""
                                onCustomTitleChange("")
                                isEditingTitle = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222230)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text("Reset", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalToggleOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F0F16))
            .border(1.dp, if (checked) Color(0xFF00E699).copy(alpha = 0.3f) else Color(0xFF222230), RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    style = TextStyle(color = Color(0xFF777788), fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color(0xFF00E699),
                    uncheckedThumbColor = Color(0xFF666677),
                    uncheckedTrackColor = Color(0xFF20202A),
                ),
                modifier = Modifier.size(width = 38.dp, height = 24.dp),
            )
        }
    }
}

@Composable
private fun CatalogStatBadge(label: String, value: String, color: Color) {
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
