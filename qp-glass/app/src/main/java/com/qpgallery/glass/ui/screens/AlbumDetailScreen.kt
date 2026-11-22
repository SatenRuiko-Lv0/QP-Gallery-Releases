package com.qpgallery.glass.ui.screens

import android.graphics.drawable.Animatable
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.Album
import com.qpgallery.glass.data.DecodedPage
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.MediaItem
import com.qpgallery.glass.data.SortOrder
import com.qpgallery.glass.data.UserSettings
import com.qpgallery.glass.ui.components.AsyncThumbnail
import com.qpgallery.glass.ui.components.GlassMenuItem
import com.qpgallery.glass.ui.components.GlassMenuSliderSpec
import com.qpgallery.glass.ui.components.LiquidFastScroller
import com.qpgallery.glass.ui.components.LiquidGlassDropdownOverlay
import com.qpgallery.glass.ui.components.LiquidGlassSearchBar
import com.qpgallery.glass.ui.components.LiquidScrollToTopButton
import com.qpgallery.glass.ui.components.computeGridScrollMetrics
import com.qpgallery.glass.ui.components.rememberGlassMenuPullDownState
import com.qpgallery.glass.ui.components.scrollGridToFraction
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassIconButton
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.liquid.asymptoticRubberBand
import com.qpgallery.glass.ui.theme.LocalEnableLiquidGlass
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class PeekTarget(
    val item: MediaItem,
    val index: Int,
    val total: Int,
)

@Composable
fun AlbumDetailScreen(
    album: Album,
    settings: UserSettings,
    lastViewedIndex: Int?,
    backEnabled: Boolean,
    loadAlbumItems: suspend (Album, SortOrder) -> List<MediaItem>,
    onOpenPhoto: (List<MediaItem>, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPreviewPhoto: (MediaItem) -> Unit,
    onSelectPhotoSort: (SortOrder) -> Unit,
    onSetColumns: (Int) -> Unit,
    onCycleColumns: () -> Unit,
    onExcludeAlbum: () -> Unit,
    onBack: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val liquidEnabled = LocalEnableLiquidGlass.current
    val haptic = LocalHapticFeedback.current
    val scrollScope = rememberCoroutineScope()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val detailBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val sortPullState = rememberGlassMenuPullDownState()
    val morePullState = rememberGlassMenuPullDownState()

    // iOS Press-and-Hold "Peek & Scrub" temporary preview state:
    // Hold any thumbnail to pop open preview; slide finger across other thumbnails to live-switch; lift to dismiss!
    var peekTarget by remember { mutableStateOf<PeekTarget?>(null) }
    var lastPeekTarget by remember { mutableStateOf<PeekTarget?>(null) }
    var peekRawOffset by remember { mutableStateOf(Offset.Zero) }
    val cellCoordsByIndex = remember { HashMap<Int, LayoutCoordinates>() }
    if (peekTarget != null) lastPeekTarget = peekTarget

    // Lazy per-album photo loading & sorting (shows populated items immediately with zero blank flash!)
    val sortedItems by produceState<List<MediaItem>?>(
        initialValue = null,
        key1 = album.path,
        key2 = album.items,
        key3 = settings.photoSort,
    ) {
        value = loadAlbumItems(album, settings.photoSort)
    }

    val resolvedItems = sortedItems ?: album.items
    val items = remember(resolvedItems, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) resolvedItems
        else resolvedItems.filter { it.name.contains(q, ignoreCase = true) }
    }
    val displayedCount = if (searchQuery.isBlank() && sortedItems == null && resolvedItems.isEmpty()) album.count else items.size

    val gridState = rememberLazyGridState()
    val columns = settings.gridColumns.coerceIn(3, 5)
    LaunchedEffect(album.path, items.size, columns) {
        cellCoordsByIndex.clear()
    }

    // Background pre-warmer for all photos in the opened album + ahead-of-scroll RAM window prefetcher
    val context = LocalContext.current
    LaunchedEffect(items, album.isHidden) {
        if (items.isNotEmpty()) {
            ImageLoader.prewarmAlbumItems(context.contentResolver, items, allowDiskCache = !album.isHidden)
        }
    }
    val detailFirstVisibleIdx by remember {
        derivedStateOf { gridState.firstVisibleItemIndex }
    }
    LaunchedEffect(detailFirstVisibleIdx, items, album.isHidden) {
        if (items.isEmpty()) return@LaunchedEffect
        val maxIdx = items.lastIndex
        val first = detailFirstVisibleIdx.coerceIn(0, maxIdx)
        val aheadEnd = (first + 72).coerceAtMost(maxIdx)
        val behindStart = (first - 20).coerceAtLeast(0)
        val allowDisk = !album.isHidden
        val window = buildList(aheadEnd - behindStart + 1) {
            // 1st priority: visible items & upcoming rows below the viewport
            for (i in first..aheadEnd) {
                add(items[i] to allowDisk)
            }
            // 2nd priority: rows immediately above the viewport
            for (i in (first - 1) downTo behindStart) {
                add(items[i] to allowDisk)
            }
        }
        ImageLoader.submitPrefetchWindow(context.contentResolver, window)
    }
    val thumbEdgePx = when (columns) {
        3 -> 480
        4 -> 380
        else -> 320
    }
    val cellShape = remember(columns) {
        ContinuousRoundedRectangle(if (columns <= 3) 14.dp else 10.dp)
    }
    val cellRimBrush = remember(isDark) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
            } else {
                listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.20f))
            },
        )
    }

    LaunchedEffect(lastViewedIndex, items.size) {
        if (lastViewedIndex != null && lastViewedIndex in items.indices) {
            val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == lastViewedIndex }
            if (!visible) {
                gridState.scrollToItem((lastViewedIndex - columns).coerceAtLeast(0))
            }
        }
    }

    BackHandler(enabled = backEnabled) {
        when {
            peekTarget != null -> {
                peekTarget = null
                peekRawOffset = Offset.Zero
            }
            moreMenuOpen -> moreMenuOpen = false
            sortMenuOpen -> sortMenuOpen = false
            searchOpen -> {
                searchQuery = ""
                searchOpen = false
            }
            else -> onBack()
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        // Translucent Liquid Glass Canvas: ambient blurred album cover wallpaper + HD G2 glass-bezel thumbnail grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (liquidEnabled) Modifier.layerBackdrop(detailBackdrop) else Modifier),
        ) {
            AsyncThumbnail(
                item = album.cover,
                maxEdgePx = 128,
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderColor = surfaceColor,
                allowDiskCache = !album.isHidden,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(54.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0xFF0A0D14).copy(alpha = 0.62f),
                                    Color(0xFF0C1018).copy(alpha = 0.54f),
                                    Color(0xFF090C12).copy(alpha = 0.70f),
                                )
                            } else {
                                listOf(
                                    Color(0xFFF5F7FC).copy(alpha = 0.64f),
                                    Color(0xFFF2F5FA).copy(alpha = 0.54f),
                                    Color(0xFFEDF1F8).copy(alpha = 0.68f),
                                )
                            },
                        ),
                    ),
            )

            if (items.isEmpty() && sortedItems != null) {
                EmptyGalleryState(
                    title = stringResource(if (searchQuery.isNotBlank()) R.string.empty_search else R.string.empty_photos),
                    subtitle = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (items.isNotEmpty()) {
                val cellGap = if (columns <= 3) 7.dp else 5.dp
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = statusTop + 68.dp,
                        bottom = navBottom + 84.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(cellGap),
                    verticalArrangement = Arrangement.spacedBy(cellGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.path },
                        contentType = { _, _ -> "photo_cell" },
                    ) { index, item ->
                        val isLastViewed = searchQuery.isEmpty() && lastViewedIndex == index
                        val isBeingPeeked = peekTarget?.index == index
                        DisposableEffect(index) {
                            onDispose { cellCoordsByIndex.remove(index) }
                        }
                        GlassThumbnailCell(
                            item = item,
                            index = index,
                            maxEdgePx = thumbEdgePx,
                            shape = cellShape,
                            rimBrush = cellRimBrush,
                            isLastViewed = isLastViewed,
                            isBeingPeeked = isBeingPeeked,
                            allowDiskCache = !album.isHidden,
                            accent = accent,
                            isDark = isDark,
                            onCoordsUpdated = { coords -> cellCoordsByIndex[index] = coords },
                            onClick = { onOpenPhoto(items, index) },
                            onPeekStart = {
                                sortMenuOpen = false
                                moreMenuOpen = false
                                peekRawOffset = Offset.Zero
                                peekTarget = PeekTarget(item = item, index = index, total = items.size)
                            },
                            onPeekRootMove = { rootPos ->
                                var hitIdx: Int? = null
                                var hitRect: Rect? = null
                                for ((idx, coords) in cellCoordsByIndex) {
                                    if (!coords.isAttached) continue
                                    val rect = coords.boundsInRoot()
                                    if (rect.contains(rootPos)) {
                                        hitIdx = idx
                                        hitRect = rect
                                        break
                                    }
                                }
                                if (hitIdx != null && hitIdx in items.indices) {
                                    val cur = peekTarget
                                    if (cur == null || cur.index != hitIdx) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        peekTarget = PeekTarget(item = items[hitIdx], index = hitIdx, total = items.size)
                                    }
                                    if (hitRect != null) {
                                        peekRawOffset = Offset(
                                            x = (rootPos.x - hitRect.center.x) * 0.40f,
                                            y = (rootPos.y - hitRect.center.y) * 0.40f,
                                        )
                                    }
                                }
                            },
                            onPeekEnd = {
                                peekTarget = null
                                peekRawOffset = Offset.Zero
                            },
                        )
                    }
                }
            }
        }

        // Pixel-continuous right-edge Liquid Glass Fast Scrubber (zero row-boundary jumping even on 30-40 photo albums!)
        if (items.size >= 12 && peekTarget == null) {
            LiquidFastScroller(
                itemCount = items.size,
                metricsProvider = {
                    computeGridScrollMetrics(
                        gridState = gridState,
                        itemCount = items.size,
                        columns = columns,
                        headerCount = 0,
                        fallbackRowPitchPx = 320f,
                    )
                },
                isScrolling = { gridState.isScrollInProgress },
                onScrollToFraction = { frac, metrics ->
                    scrollScope.launch {
                        scrollGridToFraction(gridState, frac, items.size, columns, 0, metrics)
                    }
                },
                sectionLabelForIndex = { idx ->
                    val target = items.getOrNull(idx)
                    "#${idx + 1}" to (target?.name?.substringBeforeLast('.')?.take(18).orEmpty() + " · ${idx + 1}/${items.size}")
                },
                backdrop = detailBackdrop,
                topPadding = statusTop + 72.dp,
                bottomPadding = navBottom + 84.dp,
            )
        }

        // Small circular transparent Liquid Glass "Scroll to Top" button slightly above the bottom pill
        val showScrollToTop by remember(columns) {
            derivedStateOf {
                gridState.firstVisibleItemIndex >= columns * 2 ||
                    (gridState.firstVisibleItemIndex >= columns && gridState.firstVisibleItemScrollOffset > 200)
            }
        }
        LiquidScrollToTopButton(
            visible = showScrollToTop && peekTarget == null,
            backdrop = detailBackdrop,
            onClick = {
                scrollScope.launch {
                    if (gridState.firstVisibleItemIndex > columns * 12) {
                        gridState.scrollToItem(columns * 6)
                    }
                    gridState.animateScrollToItem(0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 70.dp),
        )

        // Crystal-Clear Floating Liquid Glass Top Header (strictly 0-deg horizontal elastic drag!)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searchOpen) {
                LiquidGlassSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        searchOpen = false
                    },
                    backdrop = detailBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LiquidGlassIconButton(
                    onClick = onBack,
                    backdrop = detailBackdrop,
                    contentDescription = stringResource(R.string.action_back),
                    darkSurface = isDark,
                ) {
                    Icon(
                        imageVector = GlassIcons.BackChevron,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp),
                    )
                }

                LiquidGlassSurface(
                    backdrop = detailBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = album.name,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(
                                shadow = TextShadow(
                                    color = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.65f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f,
                                ),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "$displayedCount",
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }

                LiquidGlassSurface(
                    backdrop = detailBackdrop,
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

        // High-legibility Crystal Liquid Glass Bottom Action Pill (strictly 0-deg horizontal elastic pull!)
        if (items.isNotEmpty()) {
            val resumeIndex = lastViewedIndex?.takeIf { searchQuery.isEmpty() && it in items.indices }
            val barFg = if (isDark) Color.White else Color(0xFF0E121A)
            val barTextShadow = remember(isDark) {
                TextShadow(
                    color = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f),
                    offset = Offset(0f, 1f),
                    blurRadius = 5f,
                )
            }
            val barTint = if (isDark) Color(0xFF090C12).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.36f)
            LiquidGlassSurface(
                backdrop = detailBackdrop,
                shape = ContinuousCapsule,
                tintColor = barTint,
                blurRadius = 3.5.dp,
                refractionHeight = 18.dp,
                refractionAmount = 24.dp,
                chromaticAberration = 0.20f,
                highlightAlpha = 0.92f,
                shadowRadius = 16.dp,
                darkSurface = isDark,
                elasticDrag = true,
                maxPullX = 22.dp,
                maxPullY = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .height(46.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val colSource = remember { MutableInteractionSource() }
                    val colPressed by colSource.collectIsPressedAsState()
                    val colScale by animateFloatAsState(
                        targetValue = if (colPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.56f, stiffness = 460f),
                        label = "colBtnScale",
                    )
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = colScale
                                scaleY = colScale
                            }
                            .clip(ContinuousCapsule)
                            .background(if (colPressed) barFg.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(
                                interactionSource = colSource,
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCycleColumns()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = GlassIcons.Grid,
                            contentDescription = stringResource(R.string.menu_columns),
                            tint = barFg,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${columns}×",
                            color = barFg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(shadow = barTextShadow),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 16.dp)
                            .background(barFg.copy(alpha = 0.24f)),
                    )

                    Text(
                        text = stringResource(R.string.folder_items_count, items.size),
                        color = barFg.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum", shadow = barTextShadow),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )

                    if (resumeIndex != null) {
                        val resumeSource = remember { MutableInteractionSource() }
                        val resumePressed by resumeSource.collectIsPressedAsState()
                        val resumeScale by animateFloatAsState(
                            targetValue = if (resumePressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = 0.56f, stiffness = 460f),
                            label = "resumeChipScale",
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = resumeScale
                                    scaleY = resumeScale
                                }
                                .clip(ContinuousCapsule)
                                .background(accent.copy(alpha = 0.84f))
                                .clickable(
                                    interactionSource = resumeSource,
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenPhoto(items, resumeIndex)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_resume_page, resumeIndex + 1),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Photo Sort Liquid Glass Menu (supports Tap & Pull-Down-Slide-Release!)
        LiquidGlassDropdownOverlay(
            visible = sortMenuOpen,
            onDismiss = { sortMenuOpen = false },
            backdrop = detailBackdrop,
            pullDownState = sortPullState,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 60.dp, end = 14.dp),
            items = listOf(
                GlassMenuItem(
                    title = stringResource(R.string.sort_name_asc),
                    icon = GlassIcons.Sort,
                    selected = settings.photoSort == SortOrder.NAME_ASC,
                    onClick = { onSelectPhotoSort(SortOrder.NAME_ASC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_name_desc),
                    icon = GlassIcons.Sort,
                    selected = settings.photoSort == SortOrder.NAME_DESC,
                    onClick = { onSelectPhotoSort(SortOrder.NAME_DESC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_date_desc),
                    icon = GlassIcons.Sort,
                    selected = settings.photoSort == SortOrder.DATE_DESC,
                    onClick = { onSelectPhotoSort(SortOrder.DATE_DESC) },
                ),
                GlassMenuItem(
                    title = stringResource(R.string.sort_date_asc),
                    icon = GlassIcons.Sort,
                    selected = settings.photoSort == SortOrder.DATE_ASC,
                    onClick = { onSelectPhotoSort(SortOrder.DATE_ASC) },
                ),
            ),
        )

        // Album Actions Liquid Glass Menu (compact inline 3 · 4 · 5 stepped slider + Exclude folder)
        val colTick3 = stringResource(R.string.settings_grid_columns_tick, 3)
        val colTick4 = stringResource(R.string.settings_grid_columns_tick, 4)
        val colTick5 = stringResource(R.string.settings_grid_columns_tick, 5)
        LiquidGlassDropdownOverlay(
            visible = moreMenuOpen,
            onDismiss = { moreMenuOpen = false },
            backdrop = detailBackdrop,
            pullDownState = morePullState,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 60.dp, end = 14.dp),
            items = listOf(
                GlassMenuItem(
                    title = stringResource(R.string.menu_columns),
                    icon = GlassIcons.Grid,
                    sliderSpec = GlassMenuSliderSpec(
                        steps = listOf(3, 4, 5),
                        selectedValue = columns,
                        valueSummary = when (columns) {
                            3 -> colTick3
                            4 -> colTick4
                            else -> colTick5
                        },
                        stepLabel = { n ->
                            when (n) {
                                3 -> colTick3
                                4 -> colTick4
                                else -> colTick5
                            }
                        },
                        onValueSelected = onSetColumns,
                    ),
                ),
                GlassMenuItem(
                    title = stringResource(R.string.menu_excluded_folders),
                    icon = GlassIcons.FolderMinus,
                    destructive = true,
                    dividerBefore = true,
                    onClick = onExcludeAlbum,
                ),
            ),
        )

        // iOS-style Press-and-Hold "Peek" Instant Fullscreen Preview Overlay (releases straight back on finger lift!)
        HoldToPeekOverlay(
            visible = peekTarget != null,
            target = peekTarget ?: lastPeekTarget,
            rawDragOffset = peekRawOffset,
            backdrop = detailBackdrop,
            isDark = isDark,
        )
    }
}

@Composable
private fun HoldToPeekOverlay(
    visible: Boolean,
    target: PeekTarget?,
    rawDragOffset: Offset,
    backdrop: Backdrop?,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxFloatPx = with(density) { 26.dp.toPx() }
    val targetFloat = remember(rawDragOffset, maxFloatPx) {
        Offset(
            x = asymptoticRubberBand(rawDragOffset.x * 0.45f, maxFloatPx),
            y = asymptoticRubberBand(rawDragOffset.y * 0.45f, maxFloatPx),
        )
    }
    val smoothFloat by animateOffsetAsState(
        targetValue = if (visible) targetFloat else Offset.Zero,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "peekCardFloat",
    )

    AnimatedVisibility(
        visible = visible && target != null,
        enter = fadeIn(spring(stiffness = 560f)),
        exit = fadeOut(spring(stiffness = 580f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val shown = target ?: return@AnimatedVisibility
        val cardShape = remember { ContinuousRoundedRectangle(26.dp) }
        val previewRim = remember {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.14f)),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.72f else 0.56f)),
            contentAlignment = Alignment.Center,
        ) {
            val screenWidthPx = constraints.maxWidth.coerceAtLeast(720)
            val maxCardW = (maxWidth - 28.dp).coerceAtLeast(220.dp)
            val maxCardH = (maxHeight - 156.dp).coerceAtLeast(260.dp)

            // Instant Frame-0 cached thumbnail placeholder -> seamlessly upgrades to full sharp decode in ~25ms!
            val placeholderBitmap: ImageBitmap? = remember(shown.item.path) {
                ImageLoader.getAnyCachedThumbnail(shown.item.path)?.asImageBitmap()
            }
            var decodedPage by remember(shown.item.path) { mutableStateOf<DecodedPage?>(null) }
            var aspect by remember(shown.item.path) {
                val cachedDims = ImageLoader.getCachedDimensions(shown.item.path)
                val initial = when {
                    cachedDims != null && cachedDims.aspect > 0f -> cachedDims.aspect
                    placeholderBitmap != null && placeholderBitmap.width > 0 ->
                        placeholderBitmap.height.toFloat() / placeholderBitmap.width.toFloat()
                    else -> 1f
                }
                mutableStateOf(initial.coerceIn(0.35f, 2.8f))
            }

            LaunchedEffect(shown.item.path, screenWidthPx) {
                val full = ImageLoader.decodeFullImage(context.contentResolver, shown.item, screenWidthPx)
                if (full != null) {
                    decodedPage = full
                    if (full.aspect > 0f) {
                        aspect = full.aspect.coerceIn(0.35f, 2.8f)
                    }
                }
            }

            // Compute exact natural fit dimensions inside (maxCardW x maxCardH) with silky liquid morph between photos!
            val widthRatio = maxCardW.value / (maxCardH.value / aspect).coerceAtLeast(1f)
            val targetCardW = if (widthRatio <= 1f) maxCardW else (maxCardH / aspect).coerceAtMost(maxCardW)
            val targetCardH = (targetCardW * aspect).coerceAtMost(maxCardH)
            val cardW by animateDpAsState(
                targetValue = targetCardW,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 540f),
                label = "peekCardW",
            )
            val cardH by animateDpAsState(
                targetValue = targetCardH,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 540f),
                label = "peekCardH",
            )

            Column(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(
                            initialScale = 0.74f,
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 460f),
                        ),
                        exit = scaleOut(
                            targetScale = 0.80f,
                            animationSpec = spring(stiffness = 560f),
                        ),
                    )
                    .graphicsLayer {
                        translationX = smoothFloat.x
                        translationY = smoothFloat.y
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = cardW, height = cardH)
                        .dropShadow(
                            shape = cardShape,
                            shadow = Shadow(
                                radius = 28.dp,
                                color = Color.Black,
                                alpha = 0.44f,
                            ),
                        )
                        .clip(cardShape)
                        .background(Color(0xFF0E1118))
                        .border(1.dp, previewRim, cardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val animDrawable = decodedPage?.animatedDrawable
                    val sharpBmp = decodedPage?.bitmap ?: placeholderBitmap
                    if (animDrawable != null) {
                        var frameTick by remember(animDrawable) { mutableIntStateOf(0) }
                        DisposableEffect(animDrawable) {
                            (animDrawable as? Animatable)?.start()
                            onDispose { (animDrawable as? Animatable)?.stop() }
                        }
                        LaunchedEffect(animDrawable) {
                            while (true) {
                                withFrameNanos { frameTick++ }
                            }
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            @Suppress("UNUSED_EXPRESSION")
                            frameTick
                            animDrawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                            drawIntoCanvas { animDrawable.draw(it.nativeCanvas) }
                        }
                    } else if (sharpBmp != null) {
                        Image(
                            bitmap = sharpBmp,
                            contentDescription = shown.item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Minimalist crystal Liquid Glass caption pill cleanly below the popped photo
                LiquidGlassSurface(
                    backdrop = backdrop,
                    shape = ContinuousCapsule,
                    tintColor = Color(0xFF0A0D14).copy(alpha = 0.46f),
                    blurRadius = 4.dp,
                    refractionHeight = 12.dp,
                    refractionAmount = 16.dp,
                    chromaticAberration = 0.16f,
                    highlightAlpha = 0.88f,
                    darkSurface = true,
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(max = maxCardW),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${shown.index + 1} / ${shown.total}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 1.dp, height = 12.dp)
                                .background(Color.White.copy(alpha = 0.28f)),
                        )
                        Text(
                            text = shown.item.name,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassThumbnailCell(
    item: MediaItem,
    index: Int,
    maxEdgePx: Int,
    shape: ContinuousRoundedRectangle,
    rimBrush: Brush,
    isLastViewed: Boolean,
    isBeingPeeked: Boolean,
    allowDiskCache: Boolean,
    accent: Color,
    isDark: Boolean,
    onCoordsUpdated: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
    onPeekStart: () -> Unit,
    onPeekRootMove: (Offset) -> Unit,
    onPeekEnd: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnCoordsUpdated by rememberUpdatedState(onCoordsUpdated)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnPeekStart by rememberUpdatedState(onPeekStart)
    val latestOnPeekRootMove by rememberUpdatedState(onPeekRootMove)
    val latestOnPeekEnd by rememberUpdatedState(onPeekEnd)

    var cellCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed || isBeingPeeked) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 460f),
        label = "thumbCellScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned { coords ->
                cellCoords = coords
                if (coords.isAttached) {
                    latestOnCoordsUpdated(coords)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .pointerInput(item.path) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var longTriggered = false
                    var movedTooMuch = false
                    val downTime = SystemClock.uptimeMillis()
                    var totalDrag = Offset.Zero

                    // Phase 1: Distinguish quick tap vs grid scroll vs 240ms press-and-hold Peek preview!
                    while (!longTriggered) {
                        val elapsed = SystemClock.uptimeMillis() - downTime
                        val remaining = (240L - elapsed).coerceAtLeast(1L)
                        val event = withTimeoutOrNull(remaining) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            longTriggered = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestOnPeekStart()
                            break
                        }
                        val change = event.changes.fastFirstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            movedTooMuch = true
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            pressed = false
                            if (!movedTooMuch) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnClick()
                            }
                            return@awaitEachGesture
                        }
                        val delta = change.positionChange()
                        totalDrag += delta
                        if (hypot(totalDrag.x, totalDrag.y) > slop) {
                            movedTooMuch = true
                            pressed = false
                            return@awaitEachGesture
                        }
                    }

                    if (!longTriggered) {
                        pressed = false
                        return@awaitEachGesture
                    }

                    // Phase 2: Peek & Scrub active! Lock grid scroll; map finger position into root coords
                    // so sweeping across neighboring thumbnails live-switches the preview immediately; release to close!
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            val coords = cellCoords
                            if (coords != null && coords.isAttached) {
                                latestOnPeekRootMove(coords.localToRoot(change.position))
                            }
                            change.consume()
                        }
                    } finally {
                        pressed = false
                        latestOnPeekEnd()
                    }
                }
            },
    ) {
        AsyncThumbnail(
            item = item,
            maxEdgePx = maxEdgePx,
            shape = shape,
            allowDiskCache = allowDiskCache,
            placeholderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.36f),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isBeingPeeked || isLastViewed) {
                        Modifier.border(2.2.dp, accent, shape)
                    } else {
                        Modifier.border(0.75.dp, rimBrush, shape)
                    }
                ),
        )

        if (pressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.14f), shape),
            )
        }

        if (isLastViewed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(ContinuousCapsule)
                    .background(accent.copy(alpha = 0.90f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
