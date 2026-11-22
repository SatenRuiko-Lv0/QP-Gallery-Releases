package com.qpgallery.glass.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.Album
import com.qpgallery.glass.data.AlbumViewMode
import com.qpgallery.glass.data.FolderRuleKind
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.SortOrder
import com.qpgallery.glass.data.UserSettings
import com.qpgallery.glass.ui.components.AsyncThumbnail
import com.qpgallery.glass.ui.components.GlassMenuItem
import com.qpgallery.glass.ui.components.GlassMenuPullDownState
import com.qpgallery.glass.ui.components.GlassMenuSliderSpec
import com.qpgallery.glass.ui.components.LiquidFastScroller
import com.qpgallery.glass.ui.components.LiquidGlassDropdownOverlay
import com.qpgallery.glass.ui.components.LiquidGlassSearchBar
import com.qpgallery.glass.ui.components.LiquidScrollToTopButton
import com.qpgallery.glass.ui.components.computeGridScrollMetrics
import com.qpgallery.glass.ui.components.computeListScrollMetrics
import com.qpgallery.glass.ui.components.formatAlbumFastScrollSection
import com.qpgallery.glass.ui.components.rememberGlassMenuPullDownState
import com.qpgallery.glass.ui.components.scrollGridToFraction
import com.qpgallery.glass.ui.components.scrollListToFraction
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassIconButton
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.theme.LocalEnableLiquidGlass
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    totalPhotos: Int,
    settings: UserSettings,
    isScanning: Boolean,
    hasStorageAccess: Boolean,
    hasAllFilesAccess: Boolean,
    contentBackdrop: LayerBackdrop,
    bottomBarClearance: Dp,
    onOpenAlbum: (Album) -> Unit,
    onToggleViewMode: () -> Unit,
    onSetAlbumColumns: (Int) -> Unit,
    onSelectSort: (SortOrder) -> Unit,
    onToggleShowHidden: () -> Unit,
    onOpenFolderRules: (FolderRuleKind) -> Unit,
    onExcludeAlbums: (List<Album>) -> Unit,
    onDeleteAlbums: (List<Album>) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestMediaAccess: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val liquidEnabled = LocalEnableLiquidGlass.current
    val haptic = LocalHapticFeedback.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val sortPullState = rememberGlassMenuPullDownState()
    val morePullState = rememberGlassMenuPullDownState()

    // Long-press multi-select state for whole-album delete / exclude
    var selectedAlbumPaths by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeleteDialogOpen by remember { mutableStateOf(false) }

    val filteredAlbums = remember(albums, searchQuery, settings.showHidden) {
        val visibleBase = if (settings.showHidden) albums else albums.filterNot { it.isHidden }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            visibleBase
        } else {
            visibleBase.filter { album ->
                album.name.contains(q, ignoreCase = true) ||
                    album.path.contains(q, ignoreCase = true) ||
                    album.items.any { it.name.contains(q, ignoreCase = true) }
            }
        }
    }

    // Prune stale selections after deletion/rescan
    LaunchedEffect(albums) {
        val existing = albums.mapTo(HashSet()) { it.path }
        val pruned = selectedAlbumPaths.filterTo(HashSet()) { it in existing }
        if (pruned.size != selectedAlbumPaths.size) {
            selectedAlbumPaths = pruned
        }
    }

    val isSelectionMode = selectedAlbumPaths.isNotEmpty()
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode)
        if (!isSelectionMode) confirmDeleteDialogOpen = false
    }

    val selectedAlbums = remember(albums, selectedAlbumPaths) {
        albums.filter { it.path in selectedAlbumPaths }
    }
    val selectedPhotoTotal = remember(selectedAlbums) {
        selectedAlbums.sumOf { it.count }
    }

    fun toggleSelect(path: String) {
        selectedAlbumPaths = if (path in selectedAlbumPaths) {
            selectedAlbumPaths - path
        } else {
            selectedAlbumPaths + path
        }
    }

    fun selectAllVisible() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val visiblePaths = filteredAlbums.mapTo(HashSet()) { it.path }
        selectedAlbumPaths = if (visiblePaths.isNotEmpty() && visiblePaths.all { it in selectedAlbumPaths }) {
            emptySet()
        } else {
            visiblePaths
        }
    }

    fun invertVisibleSelection() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val visiblePaths = filteredAlbums.map { it.path }
        selectedAlbumPaths = visiblePaths.filterNot { it in selectedAlbumPaths }.toSet()
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topContentPadding = statusTop + 76.dp
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var userScrolledAwayFromTop by remember { mutableStateOf(false) }

    // Track user scroll engagement via snapshotFlow (ZERO full-screen recompositions during 120Hz scrolling!)
    LaunchedEffect(gridState, listState) {
        snapshotFlow {
            val isGrid = settings.albumViewMode == AlbumViewMode.GRID
            val scrolling = gridState.isScrollInProgress || listState.isScrollInProgress
            val atTop = if (isGrid) {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 24
            } else {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
            }
            scrolling to atTop
        }.collect { (scrolling, atTop) ->
            if (atTop) {
                userScrolledAwayFromTop = false
            } else if (scrolling) {
                userScrolledAwayFromTop = true
            }
        }
    }

    // Keep list anchored at top (0, 0) when new albums are prepended by startup scan before user scrolls, or when sort changes
    var lastSortOrder by remember { mutableStateOf(settings.albumSort) }
    LaunchedEffect(filteredAlbums.size, settings.albumSort) {
        val sortChanged = settings.albumSort != lastSortOrder
        if (sortChanged) {
            lastSortOrder = settings.albumSort
            userScrolledAwayFromTop = false
        }
        val activelyScrolling = gridState.isScrollInProgress || listState.isScrollInProgress
        if ((sortChanged || (!userScrolledAwayFromTop && !activelyScrolling)) && filteredAlbums.isNotEmpty()) {
            gridState.scrollToItem(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

    // System-gallery style background cover pre-warmer + ahead-of-scroll RAM window prefetcher
    val context = LocalContext.current
    LaunchedEffect(filteredAlbums.size, settings.showHidden) {
        if (filteredAlbums.isNotEmpty()) {
            ImageLoader.prewarmAlbumCovers(context.contentResolver, filteredAlbums)
        }
    }
    val activeFirstAlbumIdx by remember(settings.albumViewMode) {
        derivedStateOf {
            if (settings.albumViewMode == AlbumViewMode.GRID) {
                gridState.firstVisibleItemIndex
            } else {
                listState.firstVisibleItemIndex
            }
        }
    }
    LaunchedEffect(activeFirstAlbumIdx, filteredAlbums) {
        if (filteredAlbums.isEmpty()) return@LaunchedEffect
        val maxIdx = filteredAlbums.lastIndex
        val first = activeFirstAlbumIdx.coerceIn(0, maxIdx)
        val aheadEnd = (first + 60).coerceAtMost(maxIdx)
        val behindStart = (first - 16).coerceAtLeast(0)
        val window = buildList(aheadEnd - behindStart + 1) {
            // 1st priority: items on screen and immediately ahead of scroll direction!
            for (i in first..aheadEnd) {
                val alb = filteredAlbums[i]
                val cover = alb.cover ?: continue
                add(cover to !alb.isHidden)
            }
            // 2nd priority: items just above the viewport for smooth upward reverse scroll
            for (i in (first - 1) downTo behindStart) {
                val alb = filteredAlbums[i]
                val cover = alb.cover ?: continue
                add(cover to !alb.isHidden)
            }
        }
        if (window.isNotEmpty()) {
            ImageLoader.submitPrefetchWindow(context.contentResolver, window)
        }
    }

    BackHandler(enabled = confirmDeleteDialogOpen || isSelectionMode || moreMenuOpen || sortMenuOpen || searchOpen) {
        when {
            confirmDeleteDialogOpen -> confirmDeleteDialogOpen = false
            isSelectionMode -> selectedAlbumPaths = emptySet()
            moreMenuOpen -> moreMenuOpen = false
            sortMenuOpen -> sortMenuOpen = false
            searchOpen -> {
                searchQuery = ""
                searchOpen = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (liquidEnabled || settings.enableBlur) Modifier.layerBackdrop(contentBackdrop) else Modifier),
        ) {
            if (!hasStorageAccess) {
                StoragePermissionPlaceholder(
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestMediaAccess = onRequestMediaAccess,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = topContentPadding, bottom = bottomBarClearance, start = 16.dp, end = 16.dp),
                )
            } else if (filteredAlbums.isEmpty() && !isScanning) {
                EmptyGalleryState(
                    title = stringResource(if (searchQuery.isNotBlank()) R.string.empty_search else R.string.empty_albums),
                    subtitle = if (searchQuery.isNotBlank()) null else stringResource(R.string.empty_albums_hint),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = topContentPadding, bottom = bottomBarClearance),
                )
            } else if (settings.albumViewMode == AlbumViewMode.GRID) {
                val albumCols = settings.albumGridColumns.coerceIn(2, 4)
                val gridGapH = when (albumCols) {
                    2 -> 12.dp
                    3 -> 10.dp
                    else -> 8.dp
                }
                val gridGapV = when (albumCols) {
                    2 -> 16.dp
                    3 -> 14.dp
                    else -> 12.dp
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(albumCols),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = topContentPadding + 8.dp,
                        bottom = bottomBarClearance + 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(gridGapH),
                    verticalArrangement = Arrangement.spacedBy(gridGapV),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!hasAllFilesAccess) {
                        item(span = { GridItemSpan(albumCols) }) {
                            AllFilesUpgradeBanner(onRequestAllFilesAccess)
                        }
                    }
                    items(
                        items = filteredAlbums,
                        key = { it.path },
                        contentType = { "album_grid_card" },
                    ) { album ->
                        val isSelected = album.path in selectedAlbumPaths
                        AlbumGridCard(
                            album = album,
                            columns = albumCols,
                            isDark = isDark,
                            accent = accent,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelect(album.path)
                                } else {
                                    onOpenAlbum(album)
                                }
                            },
                            onLongClick = {
                                sortMenuOpen = false
                                moreMenuOpen = false
                                toggleSelect(album.path)
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = topContentPadding + 8.dp,
                        bottom = bottomBarClearance + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!hasAllFilesAccess) {
                        item {
                            AllFilesUpgradeBanner(onRequestAllFilesAccess)
                        }
                    }
                    items(
                        items = filteredAlbums,
                        key = { it.path },
                        contentType = { "album_grid_card" },
                    ) { album ->
                        val isSelected = album.path in selectedAlbumPaths
                        AlbumListRow(
                            album = album,
                            accent = accent,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelect(album.path)
                                } else {
                                    onOpenAlbum(album)
                                }
                            },
                            onLongClick = {
                                sortMenuOpen = false
                                moreMenuOpen = false
                                toggleSelect(album.path)
                            },
                        )
                    }
                }
            }
        }

        // Right-edge pixel-continuous Liquid Glass Fast Scrubber with live sort-aware indicator bubble (Date / Name / Count)
        if (hasStorageAccess && filteredAlbums.size >= 4) {
            val isGrid = settings.albumViewMode == AlbumViewMode.GRID
            val albumCols = settings.albumGridColumns.coerceIn(2, 4)
            val headerOffset = if (!hasAllFilesAccess) 1 else 0
            LiquidFastScroller(
                itemCount = filteredAlbums.size,
                metricsProvider = {
                    if (isGrid) {
                        computeGridScrollMetrics(
                            gridState = gridState,
                            itemCount = filteredAlbums.size,
                            columns = albumCols,
                            headerCount = headerOffset,
                            fallbackRowPitchPx = 560f,
                        )
                    } else {
                        computeListScrollMetrics(
                            listState = listState,
                            itemCount = filteredAlbums.size,
                            headerCount = headerOffset,
                            fallbackRowPitchPx = 220f,
                        )
                    }
                },
                isScrolling = {
                    if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress
                },
                        onScrollToFraction = { frac, metrics ->
                    userScrolledAwayFromTop = frac > 0.02f
                    scrollScope.launch {
                        if (isGrid) {
                            scrollGridToFraction(gridState, frac, filteredAlbums.size, albumCols, headerOffset, metrics)
                        } else {
                            scrollListToFraction(listState, frac, filteredAlbums.size, headerOffset, metrics)
                        }
                    }
                },
                sectionLabelForIndex = { idx ->
                    val album = filteredAlbums.getOrNull(idx)
                    if (album != null) {
                        formatAlbumFastScrollSection(album, idx, filteredAlbums.size, settings.albumSort)
                    } else {
                        "" to ""
                    }
                },
                backdrop = contentBackdrop,
                topPadding = topContentPadding + 6.dp,
                bottomPadding = bottomBarClearance + 14.dp,
            )
        }

        // Small circular transparent Liquid Glass "Scroll to Top" button slightly above the bottom bar
        // (Only triggers after scrolling down a meaningful distance; automatically disappears upon returning to top)
        val isGridForTopBtn = settings.albumViewMode == AlbumViewMode.GRID
        val colsForTopBtn = settings.albumGridColumns.coerceIn(2, 4)
        val showScrollToTop by remember(isGridForTopBtn, colsForTopBtn, isSelectionMode) {
            derivedStateOf {
                if (isSelectionMode || !userScrolledAwayFromTop) {
                    false
                } else if (isGridForTopBtn) {
                    gridState.firstVisibleItemIndex >= colsForTopBtn &&
                        (gridState.firstVisibleItemIndex >= colsForTopBtn * 2 || gridState.firstVisibleItemScrollOffset > 140)
                } else {
                    listState.firstVisibleItemIndex >= 4 ||
                        (listState.firstVisibleItemIndex >= 3 && listState.firstVisibleItemScrollOffset > 100)
                }
            }
        }
        LiquidScrollToTopButton(
            visible = showScrollToTop,
            backdrop = contentBackdrop,
            onClick = {
                userScrolledAwayFromTop = false
                scrollScope.launch {
                    if (isGridForTopBtn) {
                        if (gridState.firstVisibleItemIndex > colsForTopBtn * 10) {
                            gridState.scrollToItem(colsForTopBtn * 5)
                        }
                        gridState.animateScrollToItem(0)
                    } else {
                        if (listState.firstVisibleItemIndex > 20) {
                            listState.scrollToItem(10)
                        }
                        listState.animateScrollToItem(0)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarClearance + 4.dp),
        )

        // Smooth progressive top fade so floating title stays razor-sharp over bright scrolling covers
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        0f to surfaceColor.copy(alpha = 0.98f),
                        0.62f to surfaceColor.copy(alpha = 0.88f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(surfaceColor.copy(alpha = 0.96f)),
        )

        // Floating Liquid Glass Top Header (seamlessly switches to Multi-Select Bar on album long-press!)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSelectionMode) {
                LiquidGlassIconButton(
                    onClick = { selectedAlbumPaths = emptySet() },
                    backdrop = contentBackdrop,
                    contentDescription = stringResource(R.string.action_close),
                    darkSurface = isDark,
                ) {
                    Icon(
                        imageVector = GlassIcons.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp),
                    )
                }

                LiquidGlassSurface(
                    backdrop = contentBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.selected_albums_summary, selectedAlbums.size, selectedPhotoTotal),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                LiquidGlassSurface(
                    backdrop = contentBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        val allSelected = filteredAlbums.isNotEmpty() && filteredAlbums.all { it.path in selectedAlbumPaths }
                        TopPillIcon(
                            icon = GlassIcons.SelectAll,
                            description = stringResource(R.string.action_select_all),
                            tint = if (allSelected) accent else MiuixTheme.colorScheme.onSurface,
                            onClick = ::selectAllVisible,
                        )
                        TopPillIcon(
                            icon = GlassIcons.InvertSelect,
                            description = stringResource(R.string.action_invert_selection),
                            tint = MiuixTheme.colorScheme.onSurface,
                            onClick = ::invertVisibleSelection,
                        )
                    }
                }
            } else if (searchOpen) {
                LiquidGlassSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        searchOpen = false
                    },
                    backdrop = contentBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tab_albums),
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.albums_summary, albums.size, totalPhotos),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }

                LiquidGlassSurface(
                    backdrop = contentBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    elasticYieldDownward = true,
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        TopPillIcon(
                            icon = GlassIcons.Search,
                            description = stringResource(R.string.action_search),
                            onClick = { searchOpen = true },
                        )
                        TopPillIcon(
                            icon = GlassIcons.Sort,
                            description = stringResource(R.string.action_sort),
                            pullDownState = sortPullState,
                            onPullDownOpen = {
                                moreMenuOpen = false
                                sortMenuOpen = true
                            },
                            onPullDownDismiss = { sortMenuOpen = false },
                            onClick = {
                                moreMenuOpen = false
                                sortMenuOpen = !sortMenuOpen
                            },
                        )
                        TopPillIcon(
                            icon = GlassIcons.More,
                            description = stringResource(R.string.action_more),
                            pullDownState = morePullState,
                            onPullDownOpen = {
                                sortMenuOpen = false
                                moreMenuOpen = true
                            },
                            onPullDownDismiss = { moreMenuOpen = false },
                            onClick = {
                                sortMenuOpen = false
                                moreMenuOpen = !moreMenuOpen
                            },
                        )
                    }
                }
            }
        }

        // Bottom Floating Liquid Glass Multi-Select Action Dock (Exclude Selected · Delete Albums)
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = 480f),
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            val dockTint = if (isDark) Color(0xFF0A0D14).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.44f)
            LiquidGlassSurface(
                backdrop = contentBackdrop,
                shape = ContinuousCapsule,
                tintColor = dockTint,
                blurRadius = 4.dp,
                refractionHeight = 18.dp,
                refractionAmount = 24.dp,
                chromaticAberration = 0.20f,
                highlightAlpha = 0.92f,
                darkSurface = isDark,
                elasticDrag = true,
                modifier = Modifier.height(54.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectionDockAction(
                        icon = GlassIcons.FolderMinus,
                        label = stringResource(R.string.action_exclude_selected),
                        fg = MiuixTheme.colorScheme.onSurface,
                        bg = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val batch = selectedAlbums
                            selectedAlbumPaths = emptySet()
                            onExcludeAlbums(batch)
                        },
                    )
                    SelectionDockAction(
                        icon = GlassIcons.Trash,
                        label = stringResource(R.string.action_delete_albums),
                        fg = Color.White,
                        bg = Color(0xFFFF453A).copy(alpha = 0.90f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            confirmDeleteDialogOpen = true
                        },
                    )
                }
            }
        }

        // Liquid Glass Sort Dropdown (supports both Tap and Pull-Down-Slide-Release!)
        LiquidGlassDropdownOverlay(
            visible = sortMenuOpen,
            onDismiss = { sortMenuOpen = false },
            backdrop = contentBackdrop,
            pullDownState = sortPullState,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 60.dp, end = 16.dp),
            items = listOf(
                GlassMenuItem(
                    title = stringResource(R.string.sort_date_desc),
                    icon = GlassIcons.Sort,
                    selected = settings.albumSort == SortOrder.DATE_DESC,
                    onClick = { onSelectSort(SortOrder.DATE_DESC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_date_asc),
                    icon = GlassIcons.Sort,
                    selected = settings.albumSort == SortOrder.DATE_ASC,
                    onClick = { onSelectSort(SortOrder.DATE_ASC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_name_asc),
                    icon = GlassIcons.Sort,
                    selected = settings.albumSort == SortOrder.NAME_ASC,
                    onClick = { onSelectSort(SortOrder.NAME_ASC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_name_desc),
                    icon = GlassIcons.Sort,
                    selected = settings.albumSort == SortOrder.NAME_DESC,
                    onClick = { onSelectSort(SortOrder.NAME_DESC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_count_desc),
                    icon = GlassIcons.Sort,
                    selected = settings.albumSort == SortOrder.COUNT_DESC,
                    onClick = { onSelectSort(SortOrder.COUNT_DESC) },
                ),
            ),
        )

        // Liquid Glass Overflow Menu (4-section layout: 1. Show Hidden | 2. View + Grid Slider | 3. Folders | 4. Refresh)
        val albumColsForMenu = settings.albumGridColumns.coerceIn(2, 4)
        val albumColTick2 = stringResource(R.string.settings_grid_columns_tick, 2)
        val albumColTick3 = stringResource(R.string.settings_grid_columns_tick, 3)
        val albumColTick4 = stringResource(R.string.settings_grid_columns_tick, 4)
        LiquidGlassDropdownOverlay(
            visible = moreMenuOpen,
            onDismiss = { moreMenuOpen = false },
            backdrop = contentBackdrop,
            pullDownState = morePullState,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 60.dp, end = 16.dp),
            items = listOf(
                // Section 1: Show/Hide Hidden Items (top solo slot for instant 1-gesture access!)
                GlassMenuItem(
                    title = stringResource(
                        if (settings.showHidden) R.string.menu_hide_hidden else R.string.menu_show_hidden,
                    ),
                    icon = if (settings.showHidden) GlassIcons.EyeSlash else GlassIcons.Eye,
                    selected = settings.showHidden,
                    onClick = onToggleShowHidden,
                ),
                // Section 2: View mode + compact inline embedded-dot Album Grid Columns slider (2 · 3 · 4)
                GlassMenuItem(
                    title = stringResource(
                        if (settings.albumViewMode == AlbumViewMode.GRID) R.string.menu_view_list
                        else R.string.menu_view_grid,
                    ),
                    icon = if (settings.albumViewMode == AlbumViewMode.GRID) {
                        GlassIcons.ListView
                    } else {
                        GlassIcons.Grid
                    },
                    dividerBefore = true,
                    onClick = onToggleViewMode,
                ),
                GlassMenuItem(
                    title = stringResource(R.string.menu_album_columns),
                    icon = GlassIcons.Grid,
                    sliderSpec = GlassMenuSliderSpec(
                        steps = listOf(2, 3, 4),
                        selectedValue = albumColsForMenu,
                        valueSummary = when (albumColsForMenu) {
                            2 -> albumColTick2
                            3 -> albumColTick3
                            else -> albumColTick4
                        },
                        stepLabel = { n ->
                            when (n) {
                                2 -> albumColTick2
                                3 -> albumColTick3
                                else -> albumColTick4
                            }
                        },
                        onValueSelected = onSetAlbumColumns,
                    ),
                ),
                // Section 3: Included & Excluded Folders
                GlassMenuItem(
                    title = stringResource(R.string.menu_included_folders),
                    icon = GlassIcons.FolderPlus,
                    dividerBefore = true,
                    onClick = { onOpenFolderRules(FolderRuleKind.INCLUDE) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.menu_excluded_folders),
                    icon = GlassIcons.FolderMinus,
                    onClick = { onOpenFolderRules(FolderRuleKind.EXCLUDE) },
                ),
                // Section 4: Refresh
                GlassMenuItem(
                    title = stringResource(R.string.action_refresh),
                    icon = GlassIcons.Refresh,
                    dividerBefore = true,
                    onClick = onRefresh,
                ),
            ),
        )

        // Deterministic Two-Step Whole-Album Delete Confirmation Liquid Glass Sheet
        AnimatedVisibility(
            visible = confirmDeleteDialogOpen && selectedAlbums.isNotEmpty(),
            enter = fadeIn(spring(stiffness = 460f)),
            exit = fadeOut(spring(stiffness = 520f)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { confirmDeleteDialogOpen = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val fg = if (isDark) Color.White else Color(0xFF14171F)
                val sheetShape = remember { ContinuousRoundedRectangle(28.dp) }
                val sheetTint = if (isDark) Color(0xFF10141D).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f)
                val firstAlbum = selectedAlbums.firstOrNull()
                val confirmMessage = if (selectedAlbums.size == 1 && firstAlbum != null) {
                    stringResource(R.string.delete_album_single_message, firstAlbum.name, firstAlbum.count)
                } else {
                    stringResource(
                        R.string.delete_albums_multi_message,
                        selectedAlbums.size,
                        selectedPhotoTotal,
                        firstAlbum?.name.orEmpty(),
                    )
                }

                LiquidGlassSurface(
                    backdrop = contentBackdrop,
                    shape = sheetShape,
                    tintColor = sheetTint,
                    blurRadius = 9.dp,
                    refractionHeight = 18.dp,
                    refractionAmount = 24.dp,
                    chromaticAberration = 0.18f,
                    highlightAlpha = 0.90f,
                    shadowRadius = 24.dp,
                    darkSurface = isDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = GlassIcons.Trash,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = stringResource(R.string.delete_albums_confirm_title),
                                color = Color(0xFFFF453A),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = confirmMessage,
                            color = fg.copy(alpha = 0.90f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SelectionDockAction(
                                icon = GlassIcons.Close,
                                label = stringResource(R.string.action_cancel),
                                fg = fg,
                                bg = fg.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    confirmDeleteDialogOpen = false
                                },
                            )
                            SelectionDockAction(
                                icon = GlassIcons.Trash,
                                label = stringResource(R.string.action_confirm_delete_count, selectedPhotoTotal),
                                fg = Color.White,
                                bg = Color(0xFFFF453A),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val batch = selectedAlbums
                                    confirmDeleteDialogOpen = false
                                    selectedAlbumPaths = emptySet()
                                    onDeleteAlbums(batch)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionDockAction(
    icon: ImageVector,
    label: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 460f),
        label = "selectionActionScale",
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ContinuousCapsule)
            .background(bg)
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TopPillIcon(
    icon: ImageVector,
    description: String,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    pullDownState: GlassMenuPullDownState? = null,
    onPullDownOpen: (() -> Unit)? = null,
    onPullDownDismiss: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val clickPressed by interactionSource.collectIsPressedAsState()
    var gesturePressed by remember { mutableStateOf(false) }
    val pressed = clickPressed || gesturePressed
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 480f),
        label = "topPillIconScale",
    )

    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnOpen by rememberUpdatedState(onPullDownOpen)
    val latestOnDismiss by rememberUpdatedState(onPullDownDismiss)

    val touchModifier = if (pullDownState != null && onPullDownOpen != null && onPullDownDismiss != null) {
        val pullSlopPx = with(density) { 11.dp.toPx() }
        Modifier
            .onGloballyPositioned { layoutCoords = it }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnClick()
                    true
                }
            }
            .pointerInput(pullDownState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    gesturePressed = true
                    var totalDrag = Offset.Zero
                    var pullOpened = false
                    var stolenByParent = false
                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                if (pullOpened) {
                                    change.consume()
                                    val coords = layoutCoords
                                    val finalRoot = if (coords != null && coords.isAttached) {
                                        coords.localToRoot(change.position)
                                    } else {
                                        null
                                    }
                                    pullDownState.finishPull(
                                        finalRootPos = finalRoot,
                                        onDismiss = { latestOnDismiss?.invoke() },
                                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                    )
                                } else if (!stolenByParent && !change.isConsumed) {
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnClick()
                                }
                                break
                            }
                            if (!pullOpened && change.isConsumed) {
                                // Parent LiquidGlassSurface owns horizontal/upward elastic drag
                                stolenByParent = true
                                gesturePressed = false
                                continue
                            }
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                totalDrag += delta
                                if (!pullOpened && !stolenByParent &&
                                    hypot(totalDrag.x, totalDrag.y) > pullSlopPx &&
                                    totalDrag.y > 0f &&
                                    totalDrag.y >= abs(totalDrag.x) * 0.35f
                                ) {
                                    pullOpened = true
                                    gesturePressed = false
                                    pullDownState.beginPull()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnOpen?.invoke()
                                }
                            }
                            if (pullOpened) {
                                change.consume()
                                val coords = layoutCoords
                                if (coords != null && coords.isAttached) {
                                    val rootPos = coords.localToRoot(change.position)
                                    pullDownState.updatePointer(rootPos) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        }
                    } finally {
                        gesturePressed = false
                        if (pullOpened && pullDownState.isPulling) {
                            pullDownState.cancelPull()
                        }
                    }
                }
            }
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ContinuousCapsule)
            .background(if (pressed) tint.copy(alpha = 0.12f) else Color.Transparent)
            .then(touchModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    album: Album,
    columns: Int,
    isDark: Boolean,
    accent: Color,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.945f
            isSelected -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 420f),
        label = "albumCardScale",
    )

    val cornerDp = when (columns) {
        2 -> 20.dp
        3 -> 16.dp
        else -> 13.dp
    }
    val coverEdgePx = when (columns) {
        2 -> 560
        3 -> 440
        else -> 340
    }
    val titleSize = when (columns) {
        2 -> 15.sp
        3 -> 13.sp
        else -> 12.sp
    }
    val countSize = when (columns) {
        2 -> 13.sp
        3 -> 11.sp
        else -> 10.sp
    }
    val coverShape = remember(cornerDp) { ContinuousRoundedRectangle(cornerDp) }
    val rimBrush = remember(isDark) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
            } else {
                listOf(Color.White.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.08f))
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncThumbnail(
                item = album.cover,
                maxEdgePx = coverEdgePx,
                shape = coverShape,
                allowDiskCache = !album.isHidden,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isSelected) {
                            Modifier.border(2.6.dp, accent, coverShape)
                        } else {
                            Modifier.border(0.75.dp, rimBrush, coverShape)
                        }
                    ),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.14f), coverShape),
                )
            }
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (columns <= 2) 10.dp else 7.dp)
                        .size(if (columns <= 2) 26.dp else 22.dp)
                        .clip(ContinuousCapsule)
                        .background(if (isSelected) accent else Color.Black.copy(alpha = 0.34f))
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.85f),
                            shape = ContinuousCapsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = GlassIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (columns <= 2) 15.dp else 13.dp),
                        )
                    }
                }
            }
            if (album.isHidden) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (columns <= 2) 8.dp else 5.dp)
                        .clip(ContinuousCapsule)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .padding(horizontal = if (columns <= 2) 8.dp else 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.hidden_badge),
                        color = Color.White,
                        fontSize = if (columns <= 2) 10.sp else 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (columns <= 2) 8.dp else 6.dp))
        Text(
            text = album.name,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = titleSize,
            lineHeight = if (columns <= 2) 20.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(if (columns <= 2) 2.dp else 1.dp))
        Text(
            text = "${album.count}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = countSize,
            lineHeight = if (columns <= 2) 18.sp else 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumListRow(
    album: Album,
    accent: Color,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 440f),
        label = "albumRowScale",
    )
    val coverShape = remember { ContinuousRoundedRectangle(14.dp) }
    val cardShape = remember { ContinuousRoundedRectangle(18.dp) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSelected) Modifier.border(1.8.dp, accent, cardShape) else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncThumbnail(
                item = album.cover,
                maxEdgePx = 280,
                shape = coverShape,
                allowDiskCache = !album.isHidden,
                modifier = Modifier.size(60.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = album.name,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (album.isHidden) {
                        Box(
                            modifier = Modifier
                                .clip(ContinuousCapsule)
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.hidden_badge),
                                color = accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = album.path.removePrefix("/storage/emulated/0/"),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(ContinuousCapsule)
                        .background(if (isSelected) accent else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) Color.Transparent else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
                            shape = ContinuousCapsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = GlassIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            } else {
                Text(
                    text = "${album.count}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AllFilesUpgradeBanner(onRequestAllFilesAccess: () -> Unit) {
    val accent = appleAccentColor()
    val cardShape = remember { ContinuousRoundedRectangle(18.dp) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRequestAllFilesAccess,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = GlassIcons.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.perm_grant),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.perm_desc),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun StoragePermissionPlaceholder(
    onRequestAllFilesAccess: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = appleAccentColor()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = GlassIcons.Albums,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = stringResource(R.string.perm_title),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.perm_desc),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    text = stringResource(R.string.perm_grant),
                    onClick = onRequestAllFilesAccess,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.perm_grant_media),
                    onClick = onRequestMediaAccess,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun EmptyGalleryState(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = GlassIcons.Albums,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f),
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    }
}
