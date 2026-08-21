package com.nuvio.app.features.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.watched.WatchedRepository

@Composable
fun AddonCustomPageScreen(
    manifestUrl: String,
    pageId: String,
    onBack: () -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: (MetaPreview) -> Unit,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addonsState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val homeState by HomeRepository.uiState.collectAsStateWithLifecycle()
    val watchedState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()

    val addon = remember(addonsState.addons, manifestUrl) {
        addonsState.addons.firstOrNull { it.manifestUrl == manifestUrl }
    }
    val manifest = addon?.manifest
    val page = remember(manifest, pageId) {
        manifest?.pages?.firstOrNull { it.id == pageId }
    }

    val pageCatalogs = remember(manifest, page, homeState.sections) {
        val targetCatalogIds = page?.catalogIds.orEmpty()
        val allSections = homeState.sections
        allSections.filter { section ->
            val target = section.target
            if (target is CatalogTarget.Addon && target.manifestUrl == manifestUrl) {
                targetCatalogIds.isEmpty() || target.catalogId in targetCatalogIds
            } else {
                false
            }
        }
    }

    val tokens = MaterialTheme.nuvio
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = page?.name?.ifBlank { null } ?: manifest?.name ?: "Addon Page"
    val description = page?.description?.ifBlank { null } ?: manifest?.description.orEmpty()
    val logoUrl = page?.icon?.ifBlank { null } ?: manifest?.logoUrl

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarPadding + 16.dp,
                bottom = nuvioSafeBottomPadding(extra = 32.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvioBackButton(onClick = onBack)
                    Spacer(modifier = Modifier.width(12.dp))
                    if (!logoUrl.isNullOrBlank()) {
                        NuvioAsyncImage(
                            model = logoUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (addon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        NuvioInfoBadge(
                            text = addon.displayTitle,
                        )
                    }
                }
            }

            if (pageCatalogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No catalogs available for this page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = pageCatalogs,
                    key = { it.key },
                ) { section ->
                    HomeCatalogRowSection(
                        section = section,
                        watchedKeys = watchedState.watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                        onViewAllClick = { onCatalogClick(section) },
                    )
                }
            }
        }
    }
}
