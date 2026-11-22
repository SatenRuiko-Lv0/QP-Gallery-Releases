package com.qpgallery.glass.ui.screens

import android.app.Activity
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.DecodedPage
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.MediaItem
import com.qpgallery.glass.ui.components.AsyncThumbnail
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassBadge
import com.qpgallery.glass.ui.liquid.LiquidGlassIconButton
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.liquid.LiquidSlider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ComicReaderScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    maxBrightness: Boolean,
    backEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onInspectItem: (MediaItem) -> Unit,
    onBack: (Int) -> Unit,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack(0) }
        return
    }

    val safeInitial = initialIndex.coerceIn(0, items.lastIndex)
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Stable lifecycle-bound scopes (NEVER cancelled by visibleRange LaunchedEffect restarts!)
    val uiScope = rememberCoroutineScope()
    val decodeScope = rememberCoroutineScope()

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitial)
    val thumbListState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitial)
    val comicBackdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(safeInitial) }
    var scrubbingPage by remember { mutableStateOf<Int?>(null) }
    var hasUserScrolled by remember { mutableStateOf(false) }
    var anchorIndex by remember { mutableIntStateOf(safeInitial) }
    var scrollDirection by remember { mutableIntStateOf(1) }
    var previousFirstVisible by remember { mutableIntStateOf(safeInitial) }

    val pageAspects = remember(items) { mutableStateMapOf<Int, Float>() }
    val decodedPages = remember(items) { mutableStateMapOf<Int, DecodedPage>() }
    val failedPages = remember(items) { mutableStateMapOf<Int, Boolean>() }
    val activeDecodeJobs = remember(items) { mutableMapOf<Int, Job>() }
    val activeProbeJobs = remember(items) { mutableMapOf<Int, Job>() }

    remember(items) {
        items.forEachIndexed { idx, item ->
            ImageLoader.getCachedDimensions(item.path)?.let { d ->
                if (d.aspect > 0f) pageAspects[idx] = d.aspect
            }
        }
        true
    }

    DisposableEffect(items) {
        onDispose {
            activeDecodeJobs.values.forEach { it.cancel() }
            activeDecodeJobs.clear()
            activeProbeJobs.values.forEach { it.cancel() }
            activeProbeJobs.clear()
        }
    }

    DisposableEffect(view, maxBrightness) {
        val window = (context as? Activity)?.window
        val prevBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window != null && maxBrightness) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            if (window != null) {
                if (maxBrightness) {
                    window.attributes = window.attributes.apply { screenBrightness = prevBrightness }
                }
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(controlsVisible, view) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            hasUserScrolled = true
            if (controlsVisible) controlsVisible = false
        }
    }

    BackHandler(enabled = backEnabled) {
        onBack(currentPage)
    }

    val crispTextShadow = remember {
        TextShadow(color = Color.Black.copy(alpha = 0.48f), offset = Offset(0f, 1f), blurRadius = 6f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val viewportWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val viewportHeightPx = constraints.maxHeight.coerceAtLeast(1)

        fun enqueueProbe(idx: Int) {
            if (idx !in items.indices || pageAspects[idx] != null || activeProbeJobs.containsKey(idx)) return
            activeProbeJobs[idx] = decodeScope.launch {
                try {
                    ImageLoader.probeDimensions(context.contentResolver, items[idx])?.let { d ->
                        if (d.aspect > 0f) pageAspects[idx] = d.aspect
                    }
                } finally {
                    activeProbeJobs.remove(idx)
                }
            }
        }

        fun enqueueDecode(idx: Int) {
            if (idx !in items.indices) return
            if (decodedPages.containsKey(idx) || activeDecodeJobs.containsKey(idx)) return
            ImageLoader.getCachedComicPage(items[idx].path, viewportWidthPx)?.let { cached ->
                if (cached.aspect > 0f && pageAspects[idx] == null) {
                    pageAspects[idx] = cached.aspect
                }
                failedPages.remove(idx)
                decodedPages[idx] = cached
                return
            }
            if (failedPages[idx] == true) return

            activeDecodeJobs[idx] = decodeScope.launch {
                try {
                    val decoded = ImageLoader.decodeComicPage(
                        resolver = context.contentResolver,
                        item = items[idx],
                        displayWidth = viewportWidthPx,
                        onAspect = { asp ->
                            if (asp > 0f && pageAspects[idx] == null) {
                                pageAspects[idx] = asp
                            }
                        },
                    )
                    if (!isActive) return@launch
                    if (decoded != null) {
                        if (decoded.aspect > 0f) pageAspects[idx] = decoded.aspect
                        failedPages.remove(idx)
                        decodedPages[idx] = decoded
                    } else if (isActive) {
                        failedPages[idx] = true
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } finally {
                    activeDecodeJobs.remove(idx)
                }
            }
        }

        suspend fun positionPageCentered(targetIndex: Int) {
            val clamped = targetIndex.coerceIn(0, items.lastIndex)
            anchorIndex = clamped
            hasUserScrolled = false
            enqueueDecode(clamped)
            enqueueDecode(clamped + 1)
            enqueueDecode(clamped - 1)
            val item = items[clamped]
            val aspect = pageAspects[clamped]
                ?: ImageLoader.probeDimensions(context.contentResolver, item)?.aspect?.also {
                    if (it > 0f) pageAspects[clamped] = it
                }
                ?: 1.35f
            val pageHeightPx = (viewportWidthPx * aspect).roundToInt().coerceIn(1, 250_000)
            val scrollOffset = if (pageHeightPx < viewportHeightPx) {
                -((viewportHeightPx - pageHeightPx) / 2)
            } else {
                0
            }
            listState.scrollToItem(clamped, scrollOffset)
        }

        LaunchedEffect(items, safeInitial, viewportWidthPx, viewportHeightPx) {
            enqueueDecode(safeInitial)
            enqueueDecode(safeInitial + 1)
            enqueueDecode(safeInitial - 1)
            for (delta in listOf(0, -1, 1, 2)) {
                val idx = safeInitial + delta
                if (idx in items.indices && pageAspects[idx] == null) {
                    ImageLoader.probeDimensions(context.contentResolver, items[idx])?.let { d ->
                        if (d.aspect > 0f) pageAspects[idx] = d.aspect
                    }
                }
            }
            positionPageCentered(safeInitial)
        }

        // Pre-decode target ±1 during slider scrub (45ms debounce) so releasing jumps with zero black flash
        LaunchedEffect(scrubbingPage, viewportWidthPx) {
            val target = scrubbingPage ?: return@LaunchedEffect
            delay(45)
            enqueueProbe(target)
            enqueueDecode(target)
            enqueueDecode(target + 1)
            enqueueDecode(target - 1)
        }

        val visibleRange by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val vis = info.visibleItemsInfo
                if (vis.isEmpty()) {
                    safeInitial..safeInitial
                } else {
                    vis.first().index..vis.last().index
                }
            }
        }

        val computedPage by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val vis = info.visibleItemsInfo
                if (vis.isEmpty()) return@derivedStateOf currentPage
                val first = vis.first().index
                val last = vis.last().index
                if (!hasUserScrolled && anchorIndex in first..last) {
                    return@derivedStateOf anchorIndex
                }
                if (last == items.lastIndex && !listState.canScrollForward) return@derivedStateOf last
                if (first == 0 && !listState.canScrollBackward) return@derivedStateOf first
                val centerY = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 2
                for (item in vis) {
                    if (item.offset + item.size > centerY) {
                        return@derivedStateOf item.index
                    }
                }
                last
            }
        }

        LaunchedEffect(computedPage) {
            if (computedPage != currentPage) {
                currentPage = computedPage
                onPageChanged(computedPage)
            }
        }

        val latestScrubbingPage by rememberUpdatedState(scrubbingPage)

        LaunchedEffect(visibleRange, viewportWidthPx) {
            val first = visibleRange.first
            val last = visibleRange.last
            if (first != previousFirstVisible) {
                scrollDirection = if (first > previousFirstVisible) 1 else -1
                previousFirstVisible = first
            }

            val ahead = 5
            val behind = 3
            val keepStart = max(0, first - if (scrollDirection > 0) behind else ahead)
            val keepEnd = min(items.lastIndex, last + if (scrollDirection > 0) ahead else behind)
            val scrubProtect = latestScrubbingPage?.let { (it - 1)..(it + 1) }

            val toCancel = activeDecodeJobs.keys.filter { idx ->
                idx !in keepStart..keepEnd && (scrubProtect == null || idx !in scrubProtect)
            }
            for (idx in toCancel) {
                activeDecodeJobs.remove(idx)?.cancel()
            }

            val maxBytes = min(84L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 5L)
            var totalBytes = decodedPages.values.sumOf { it.bytes.toLong() }
            if (totalBytes > maxBytes) {
                val evictCandidates = decodedPages.keys
                    .filter { idx -> idx !in first..last && (scrubProtect == null || idx !in scrubProtect) }
                    .sortedByDescending { min(abs(it - first), abs(it - last)) }
                for (idx in evictCandidates) {
                    if (totalBytes <= maxBytes * 3 / 4) break
                    decodedPages.remove(idx)?.let { totalBytes -= it.bytes }
                }
            }

            val probeStart = max(0, first - 4)
            val probeEnd = min(items.lastIndex, last + 10)
            for (idx in probeStart..probeEnd) {
                enqueueProbe(idx)
            }

            val queue = ArrayList<Int>(16)
            for (i in first..last) queue.add(i)
            if (scrollDirection >= 0) {
                for (i in (last + 1)..min(items.lastIndex, last + ahead)) queue.add(i)
                for (i in (first - 1) downTo max(0, first - behind)) queue.add(i)
            } else {
                for (i in (first - 1) downTo max(0, first - ahead)) queue.add(i)
                for (i in (last + 1)..min(items.lastIndex, last + behind)) queue.add(i)
            }

            for (idx in queue) {
                enqueueDecode(idx)
            }
        }

        val shownPage = scrubbingPage ?: currentPage
        LaunchedEffect(controlsVisible, shownPage) {
            if (controlsVisible && shownPage in items.indices) {
                val target = (shownPage - 2).coerceAtLeast(0)
                if (scrubbingPage != null) {
                    thumbListState.scrollToItem(target)
                } else {
                    thumbListState.animateScrollToItem(target)
                }
            }
        }

        // 1. Full-bleed comic stream captured into comicBackdrop
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(comicBackdrop)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                    )
                },
        ) {
            items(
                count = items.size,
                key = { idx -> items[idx].path },
            ) { idx ->
                val item = items[idx]
                val aspect = pageAspects[idx] ?: 1.38f
                val heightDp = with(density) {
                    (viewportWidthPx * aspect).roundToInt().coerceIn(1, 250_000).toDp()
                }
                val page = decodedPages[idx]
                val failed = failedPages[idx] == true

                ComicPageSlot(
                    item = item,
                    page = page,
                    failed = failed,
                    heightDp = heightDp,
                )
            }
        }

        // 2. Immersive lower-right crystal Liquid Glass page badge (2D elastic draggable + corner avoidance)
        val navCutoutInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val bottomInsetDp = navCutoutInsets.calculateBottomPadding()
        val bottomMarginDp = 6.dp + bottomInsetDp
        val safeRightMarginDp = computeRoundedCornerRightMargin(
            bottomMarginDp = bottomMarginDp,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
        )

        AnimatedVisibility(
            visible = !controlsVisible,
            enter = fadeIn(spring(stiffness = 500f)),
            exit = fadeOut(spring(stiffness = 600f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = safeRightMarginDp, bottom = bottomMarginDp),
        ) {
            LiquidGlassBadge(
                backdrop = comicBackdrop,
                darkSurface = true,
                tintColor = Color(0xFF0A0D13).copy(alpha = 0.22f),
            ) {
                Text(
                    text = "${currentPage + 1} / ${items.size}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(fontFeatureSettings = "tnum", shadow = crispTextShadow),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }

        // Subtle status-bar-only contrast scrim when controls are open
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent),
                        ),
                    ),
            )
        }

        // 3. Crystal-Clear Floating Liquid Glass Top Bar (Back · Filename · Info — ALL 2D elastic!)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(stiffness = 520f),
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            val activeItem = items[shownPage.coerceIn(0, items.lastIndex)]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidGlassIconButton(
                    onClick = { onBack(currentPage) },
                    backdrop = comicBackdrop,
                    contentDescription = stringResource(R.string.action_back),
                    darkSurface = true,
                    tintColor = Color(0xFF0A0D13).copy(alpha = 0.18f),
                ) {
                    Icon(
                        imageVector = GlassIcons.BackChevron,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }

                LiquidGlassSurface(
                    backdrop = comicBackdrop,
                    shape = ContinuousCapsule,
                    tintColor = Color(0xFF0A0D13).copy(alpha = 0.18f),
                    blurRadius = 3.dp,
                    refractionHeight = 16.dp,
                    refractionAmount = 22.dp,
                    chromaticAberration = 0.18f,
                    highlightAlpha = 0.90f,
                    darkSurface = true,
                    elasticDrag = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = activeItem.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = crispTextShadow),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                LiquidGlassIconButton(
                    onClick = { onInspectItem(activeItem) },
                    backdrop = comicBackdrop,
                    contentDescription = stringResource(R.string.action_info),
                    darkSurface = true,
                    tintColor = Color(0xFF0A0D13).copy(alpha = 0.18f),
                ) {
                    Icon(
                        imageVector = GlassIcons.Info,
                        contentDescription = stringResource(R.string.action_info),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 4. Crystal-Clear Floating Liquid Glass Bottom Dock (Unclipped G2 Thumbnail Strip + Fluid iOS 26 Glass Scrubber!)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = 520f),
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val dockShape = remember { ContinuousRoundedRectangle(26.dp) }
            val thumbShape = remember { ContinuousRoundedRectangle(10.dp) }

            LiquidGlassSurface(
                backdrop = comicBackdrop,
                shape = dockShape,
                tintColor = Color(0xFF0A0D13).copy(alpha = 0.24f),
                blurRadius = 4.5.dp,
                refractionHeight = 18.dp,
                refractionAmount = 24.dp,
                chromaticAberration = 0.18f,
                highlightAlpha = 0.90f,
                shadowRadius = 20.dp,
                darkSurface = true,
                modifier = Modifier
                    .fillMaxWidth()
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
                        .padding(top = 8.dp, bottom = 10.dp),
                ) {
                    // Lazy Horizontal Thumbnail Strip with generous top/bottom headroom so selected border is NEVER clipped
                    // and custom G2 rounded press feedback (ZERO rectangular shadow/ripple!).
                    LazyRow(
                        state = thumbListState,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                    ) {
                        items(
                            count = items.size,
                            key = { idx -> items[idx].path },
                        ) { idx ->
                            ComicStripThumbItem(
                                item = items[idx],
                                index = idx,
                                isSelected = idx == shownPage,
                                shape = thumbShape,
                                textShadow = crispTextShadow,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scrubbingPage = null
                                    currentPage = idx
                                    onPageChanged(idx)
                                    uiScope.launch { positionPageCentered(idx) }
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = Color.White.copy(alpha = 0.14f),
                    )
                    Spacer(Modifier.height(6.dp))

                    // Authentic iOS 26 Media Liquid Glass Scrubber:
                    // Fixed counter width prevents 1->2->3->4 digit layout jitter during 646+ page scrubbing!
                    val maxIndex = (items.size - 1).coerceAtLeast(1)
                    val counterWidth = (items.size.toString().length * 9 + 14).dp.coerceAtLeast(38.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${shownPage + 1}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum", shadow = crispTextShadow),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(counterWidth),
                        )

                        LiquidSlider(
                            value = shownPage.toFloat(),
                            onValueChange = { raw ->
                                if (items.size > 1) {
                                    scrubbingPage = raw.roundToInt().coerceIn(0, items.lastIndex)
                                }
                            },
                            onValueChangeFinished = { raw ->
                                if (items.size > 1) {
                                    val target = raw.roundToInt().coerceIn(0, items.lastIndex)
                                    scrubbingPage = null
                                    currentPage = target
                                    onPageChanged(target)
                                    uiScope.launch { positionPageCentered(target) }
                                }
                            },
                            onDragCancel = {
                                scrubbingPage = null
                            },
                            valueRange = 0f..maxIndex.toFloat(),
                            backdrop = comicBackdrop,
                            enabled = items.size > 1,
                            darkSurface = true,
                            glassFill = true,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            contentDescription = stringResource(R.string.comic_page_jump),
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "${items.size}",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(fontFeatureSettings = "tnum", shadow = crispTextShadow),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(counterWidth),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thumbnail strip item with:
 * - Generous vertical clearance so the selected border never clips against LazyRow bounds.
 * - Custom G2 continuous elastic press scale + rounded crystal sheen (ZERO hard rectangular press shadow!).
 */
@Composable
private fun ComicStripThumbItem(
    item: MediaItem,
    index: Int,
    isSelected: Boolean,
    shape: ContinuousRoundedRectangle,
    textShadow: TextShadow,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.90f
            isSelected -> 1.06f
            else -> 0.95f
        },
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 440f),
        label = "stripThumbScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .padding(top = 6.dp, bottom = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape),
        ) {
            AsyncThumbnail(
                item = item,
                maxEdgePx = 220,
                shape = shape,
                placeholderColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxSize(),
            )
            // Soft G2 rounded press sheen instead of rectangular shadow
            if (pressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.18f), shape),
                )
            }
            // Crisp unclipped G2 luminous glass border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = if (isSelected) 2.2.dp else 0.7.dp,
                        color = if (isSelected) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.28f),
                        shape = shape,
                    ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${index + 1}",
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = TextStyle(fontFeatureSettings = "tnum", shadow = textShadow),
            maxLines = 1,
        )
    }
}

/**
 * Zero-black-screen page slot:
 * 1) Renders any RAM-cached thumbnail synchronously on Frame 0 (or fast fallback thumbnail while full-res decodes).
 * 2) Smoothly crossfades full-resolution bitmap (160ms) over the placeholder for a seamless transition.
 */
@Composable
private fun ComicPageSlot(
    item: MediaItem,
    page: DecodedPage?,
    failed: Boolean,
    heightDp: Dp,
) {
    val context = LocalContext.current
    var placeholder by remember(item.path) {
        mutableStateOf<ImageBitmap?>(ImageLoader.getAnyCachedThumbnail(item.path)?.asImageBitmap())
    }

    LaunchedEffect(item.path, page == null) {
        if (page == null && placeholder == null) {
            val fastThumb = ImageLoader.loadThumbnail(context.contentResolver, item, 260)
            if (fastThumb != null) {
                placeholder = fastThumb.asImageBitmap()
            }
        }
    }

    val fullAlpha by animateFloatAsState(
        targetValue = if (page?.bitmap != null) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "comicPageFade",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = placeholder
        if (thumb != null && fullAlpha < 0.99f && page?.animatedDrawable == null) {
            Image(
                bitmap = thumb,
                contentDescription = item.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val fullBmp = page?.bitmap
        if (fullBmp != null) {
            Image(
                bitmap = fullBmp,
                contentDescription = item.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullAlpha },
            )
        } else if (page?.animatedDrawable != null) {
            AnimatedDrawableCanvas(
                drawable = page.animatedDrawable,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed && thumb == null) {
            Text(
                text = stringResource(R.string.could_not_load_image),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun computeRoundedCornerRightMargin(
    bottomMarginDp: Dp,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val baseRightPx = with(density) { (if (Build.VERSION.SDK_INT >= 31) 8.dp else 16.dp).roundToPx() }
    val bottomPx = with(density) { bottomMarginDp.roundToPx() }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return with(density) { baseRightPx.toDp() }
    }
    val rootInsets = view.rootWindowInsets ?: return with(density) { baseRightPx.toDp() }
    val corner = rootInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
        ?: return with(density) { baseRightPx.toDp() }

    val center = corner.center
    val radius = corner.radius
    val origin = IntArray(2).also { view.getLocationInWindow(it) }
    val badgeBottom = origin[1] + viewportHeightPx - bottomPx
    if (badgeBottom <= center.y) {
        return with(density) { baseRightPx.toDp() }
    }
    val dy = min(radius, badgeBottom - center.y)
    val reach = sqrt((radius - dy).toDouble() * (radius + dy).toDouble()).toInt()
    val safeRight = center.x + reach - with(density) { 1.dp.roundToPx() }
    val neededPx = max(baseRightPx, origin[0] + viewportWidthPx - safeRight)
    return with(density) { neededPx.toDp() }
}

@Composable
internal fun AnimatedDrawableCanvas(
    drawable: Drawable,
    modifier: Modifier = Modifier,
) {
    var frameTick by remember(drawable) { mutableLongStateOf(0L) }

    DisposableEffect(drawable) {
        if (drawable is Animatable) drawable.start()
        onDispose {
            if (drawable is Animatable) drawable.stop()
        }
    }

    LaunchedEffect(drawable) {
        if (drawable is Animatable) {
            while (true) {
                withFrameNanos { frameTick = it }
            }
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
