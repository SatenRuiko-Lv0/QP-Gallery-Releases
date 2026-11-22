package com.qpgallery.glass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.capsule.ContinuousCapsule
import com.qpgallery.glass.R
import com.qpgallery.glass.data.Album
import com.qpgallery.glass.data.SortOrder
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassIconButton
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pixel-continuous scroll metrics for a LazyGrid or LazyList so the right-edge fast scrubber
 * moves smoothly 1px at a time with ZERO row-boundary jumps or flickering!
 */
data class ContinuousScrollMetrics(
    val fraction: Float,
    val canScroll: Boolean,
    val rowPitchPx: Float,
    val maxScrollPx: Float,
    val totalRows: Int,
    val headerTotalPx: Float = 0f,
)

fun computeGridScrollMetrics(
    gridState: LazyGridState,
    itemCount: Int,
    columns: Int,
    headerCount: Int,
    fallbackRowPitchPx: Float,
    fallbackHeaderPx: Float = 160f,
): ContinuousScrollMetrics {
    if (itemCount <= 0 || columns <= 0) {
        return ContinuousScrollMetrics(0f, false, fallbackRowPitchPx, 0f, 0, 0f)
    }
    val info = gridState.layoutInfo
    val spacing = info.mainAxisItemSpacing
    val anchor = info.visibleItemsInfo.firstOrNull { it.index >= headerCount }
    val sampleH = anchor?.size?.height ?: 0
    val rowPitch = if (sampleH > 0) (sampleH + spacing).toFloat() else fallbackRowPitchPx.coerceAtLeast(1f)
    val headerTotalPx = if (headerCount <= 0) {
        0f
    } else {
        val headerItem = info.visibleItemsInfo.firstOrNull { it.index < headerCount }
        if (headerItem != null && headerItem.size.height > 0) {
            (headerItem.size.height + spacing).toFloat()
        } else {
            fallbackHeaderPx
        }
    }
    val totalRows = ((itemCount + columns - 1) / columns).coerceAtLeast(1)
    val viewportH = info.viewportSize.height.toFloat().coerceAtLeast(1f)
    val totalContentH = headerTotalPx + totalRows * rowPitch + info.beforeContentPadding + info.afterContentPadding - spacing
    val maxScroll = (totalContentH - viewportH).coerceAtLeast(0f)
    if (maxScroll <= viewportH * 0.18f) {
        return ContinuousScrollMetrics(0f, false, rowPitch, maxScroll, totalRows, headerTotalPx)
    }
    val rawPx = if (anchor != null) {
        val anchorRow = ((anchor.index - headerCount).coerceAtLeast(0)) / columns
        headerTotalPx + anchorRow * rowPitch - anchor.offset.y.toFloat()
    } else {
        val firstIdx = (gridState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
        val currentRow = firstIdx / columns
        currentRow * rowPitch + gridState.firstVisibleItemScrollOffset.toFloat()
    }
    val currentPx = rawPx.coerceIn(0f, maxScroll)
    val frac = when {
        !gridState.canScrollBackward -> 0f
        !gridState.canScrollForward -> 1f
        else -> (currentPx / maxScroll).coerceIn(0f, 1f)
    }
    return ContinuousScrollMetrics(frac, true, rowPitch, maxScroll, totalRows, headerTotalPx)
}

fun computeListScrollMetrics(
    listState: LazyListState,
    itemCount: Int,
    headerCount: Int,
    fallbackRowPitchPx: Float,
    fallbackHeaderPx: Float = 160f,
): ContinuousScrollMetrics {
    if (itemCount <= 0) {
        return ContinuousScrollMetrics(0f, false, fallbackRowPitchPx, 0f, 0, 0f)
    }
    val info = listState.layoutInfo
    val spacing = info.mainAxisItemSpacing
    val anchor = info.visibleItemsInfo.firstOrNull { it.index >= headerCount }
    val sampleH = anchor?.size ?: 0
    val rowPitch = if (sampleH > 0) (sampleH + spacing).toFloat() else fallbackRowPitchPx.coerceAtLeast(1f)
    val headerTotalPx = if (headerCount <= 0) {
        0f
    } else {
        val headerItem = info.visibleItemsInfo.firstOrNull { it.index < headerCount }
        if (headerItem != null && headerItem.size > 0) {
            (headerItem.size + spacing).toFloat()
        } else {
            fallbackHeaderPx
        }
    }
    val viewportH = info.viewportSize.height.toFloat().coerceAtLeast(1f)
    val totalContentH = headerTotalPx + itemCount * rowPitch + info.beforeContentPadding + info.afterContentPadding - spacing
    val maxScroll = (totalContentH - viewportH).coerceAtLeast(0f)
    if (maxScroll <= viewportH * 0.18f) {
        return ContinuousScrollMetrics(0f, false, rowPitch, maxScroll, itemCount, headerTotalPx)
    }
    val rawPx = if (anchor != null) {
        val anchorIdx = (anchor.index - headerCount).coerceAtLeast(0)
        headerTotalPx + anchorIdx * rowPitch - anchor.offset.toFloat()
    } else {
        val firstIdx = (listState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
        firstIdx * rowPitch + listState.firstVisibleItemScrollOffset.toFloat()
    }
    val currentPx = rawPx.coerceIn(0f, maxScroll)
    val frac = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> (currentPx / maxScroll).coerceIn(0f, 1f)
    }
    return ContinuousScrollMetrics(frac, true, rowPitch, maxScroll, itemCount, headerTotalPx)
}

suspend fun scrollGridToFraction(
    gridState: LazyGridState,
    fraction: Float,
    itemCount: Int,
    columns: Int,
    headerCount: Int,
    metrics: ContinuousScrollMetrics,
) {
    if (itemCount <= 0 || columns <= 0 || metrics.rowPitchPx <= 1f) return
    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0.001f) {
        gridState.scrollToItem(0, 0)
        return
    }
    val targetPx = clamped * metrics.maxScrollPx
    if (headerCount > 0 && targetPx < metrics.headerTotalPx) {
        gridState.scrollToItem(0, targetPx.roundToInt().coerceAtLeast(0))
        return
    }
    val itemsTargetPx = (targetPx - metrics.headerTotalPx).coerceAtLeast(0f)
    val targetRow = (itemsTargetPx / metrics.rowPitchPx).toInt().coerceIn(0, (metrics.totalRows - 1).coerceAtLeast(0))
    val subOffset = (itemsTargetPx - targetRow * metrics.rowPitchPx).roundToInt().coerceAtLeast(0)
    val targetItem = (targetRow * columns + headerCount).coerceIn(0, itemCount - 1 + headerCount)
    gridState.scrollToItem(targetItem, subOffset)
}

suspend fun scrollListToFraction(
    listState: LazyListState,
    fraction: Float,
    itemCount: Int,
    headerCount: Int,
    metrics: ContinuousScrollMetrics,
) {
    if (itemCount <= 0 || metrics.rowPitchPx <= 1f) return
    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0.001f) {
        listState.scrollToItem(0, 0)
        return
    }
    val targetPx = clamped * metrics.maxScrollPx
    if (headerCount > 0 && targetPx < metrics.headerTotalPx) {
        listState.scrollToItem(0, targetPx.roundToInt().coerceAtLeast(0))
        return
    }
    val itemsTargetPx = (targetPx - metrics.headerTotalPx).coerceAtLeast(0f)
    val targetRow = (itemsTargetPx / metrics.rowPitchPx).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val subOffset = (itemsTargetPx - targetRow * metrics.rowPitchPx).roundToInt().coerceAtLeast(0)
    listState.scrollToItem(targetRow + headerCount, subOffset)
}

/**
 * Computes sort-aware (primarySectionLabel, secondaryDetail) for the Liquid Glass fast-scroll bubble.
 */
fun formatAlbumFastScrollSection(
    album: Album,
    index: Int,
    total: Int,
    sortOrder: SortOrder,
    locale: Locale = Locale.getDefault(),
): Pair<String, String> {
    val isZh = locale.language.startsWith("zh")
    val pos = "${index + 1} / $total"
    return when (sortOrder) {
        SortOrder.DATE_DESC, SortOrder.DATE_ASC -> {
            val monthStr = if (album.latestModifiedMs > 0L) {
                val pattern = if (isZh) "yyyy年M月" else "MMM yyyy"
                SimpleDateFormat(pattern, locale).format(Date(album.latestModifiedMs))
            } else {
                "—"
            }
            monthStr to "${album.name} · $pos"
        }
        SortOrder.NAME_ASC, SortOrder.NAME_DESC -> {
            val trimmed = album.name.trim()
            val first = trimmed.firstOrNull()
            val badge = when {
                first == null -> "#"
                first.isDigit() -> "#"
                first.code < 128 && first.isLetter() -> first.uppercaseChar().toString()
                else -> first.toString()
            }
            badge to "${album.name} · $pos"
        }
        SortOrder.COUNT_DESC -> {
            val countBadge = if (isZh) "${album.count} 张" else "${album.count}"
            countBadge to "${album.name} · $pos"
        }
    }
}

/**
 * Pure pixel-continuous Apple iOS 26 Liquid Glass Fast Scrubber with live sort-aware indicator bubble.
 * - Moves pixel-by-pixel with ZERO row-boundary jumps or flickering even on 30-photo albums!
 * - Clean dash-free Liquid Glass vertical capsule handle with right-edge system gesture exclusion.
 */
@Composable
fun LiquidFastScroller(
    itemCount: Int,
    metricsProvider: () -> ContinuousScrollMetrics,
    isScrolling: () -> Boolean,
    onScrollToFraction: (fraction: Float, metrics: ContinuousScrollMetrics) -> Unit,
    sectionLabelForIndex: (Int) -> Pair<String, String>,
    backdrop: Backdrop?,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val latestMetricsProvider by rememberUpdatedState(metricsProvider)
    val latestIsScrolling by rememberUpdatedState(isScrolling)
    val metrics by remember { derivedStateOf { latestMetricsProvider() } }
    if (itemCount < 2 || !metrics.canScroll) return

    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val latestItemCount by rememberUpdatedState(itemCount)
    val latestMetrics by rememberUpdatedState(metrics)
    val latestOnScrollTo by rememberUpdatedState(onScrollToFraction)
    val latestSectionFor by rememberUpdatedState(sectionLabelForIndex)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragTargetIndex by remember { mutableIntStateOf(0) }
    var lastHapticSection by remember { mutableStateOf("") }
    var lastPrimarySection by remember { mutableStateOf("") }
    var lastSecondaryDetail by remember { mutableStateOf("") }
    var showRecentlyScrolled by remember { mutableStateOf(false) }

    val scrollingNow by remember { derivedStateOf { latestIsScrolling() } }
    LaunchedEffect(scrollingNow, isDragging) {
        if (scrollingNow || isDragging) {
            showRecentlyScrolled = true
        } else {
            delay(1400L)
            showRecentlyScrolled = false
        }
    }

    val activeFraction = if (isDragging) dragFraction else metrics.fraction
    val active = isDragging || showRecentlyScrolled

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 16.dp else if (active) 11.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
        label = "fastScrollThumbW",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 64.dp else 52.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
        label = "fastScrollThumbH",
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else if (active) 0.96f else 0.68f,
        animationSpec = spring(stiffness = 380f),
        label = "fastScrollThumbAlpha",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val usableSpanPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val latestUsableSpanPx by rememberUpdatedState(usableSpanPx)
        val latestThumbHeightPx by rememberUpdatedState(thumbHeightPx)
        val thumbTopPx = (activeFraction * usableSpanPx).coerceIn(0f, usableSpanPx)

        // Subtle glass rail groove visible while actively scrolling or dragging
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 9.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(ContinuousCapsule)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    ),
            )
        }

        // Live Sort-Aware Floating Liquid Glass Section Bubble beside the thumb!
        val bubbleHeightPx = with(density) { 46.dp.toPx() }
        val bubbleTopPx = (thumbTopPx + (thumbHeightPx - bubbleHeightPx) * 0.5f)
            .coerceIn(0f, (trackHeightPx - bubbleHeightPx).coerceAtLeast(0f))
        if (isDragging) {
            val (sec, det) = latestSectionFor(dragTargetIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)))
            if (sec.isNotEmpty()) {
                lastPrimarySection = sec
                lastSecondaryDetail = det
            }
        }

        AnimatedVisibility(
            visible = isDragging && lastPrimarySection.isNotEmpty(),
            enter = fadeIn(spring(stiffness = 520f)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
            ),
            exit = fadeOut(spring(stiffness = 500f)) + scaleOut(targetScale = 0.90f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 42.dp)
                .graphicsLayer { translationY = bubbleTopPx },
        ) {
            val bubbleTint = if (isDark) Color(0xFF0A0D14).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.76f)
            val fg = if (isDark) Color.White else Color(0xFF0F131C)
            val textShadow = remember(isDark) {
                TextShadow(
                    color = if (isDark) Color.Black.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.78f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f,
                )
            }
            LiquidGlassSurface(
                backdrop = backdrop,
                shape = ContinuousCapsule,
                tintColor = bubbleTint,
                blurRadius = 5.dp,
                refractionHeight = 14.dp,
                refractionAmount = 20.dp,
                chromaticAberration = 0.22f,
                highlightAlpha = 0.95f,
                shadowRadius = 16.dp,
                darkSurface = isDark,
                modifier = Modifier
                    .height(46.dp)
                    .widthIn(min = 104.dp, max = 252.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = lastPrimarySection,
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = TextStyle(fontFeatureSettings = "tnum", shadow = textShadow),
                    )
                    if (lastSecondaryDetail.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(width = 1.dp, height = 16.dp)
                                .background(fg.copy(alpha = 0.24f)),
                        )
                        Text(
                            text = lastSecondaryDetail,
                            color = fg.copy(alpha = 0.94f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFeatureSettings = "tnum", shadow = textShadow),
                        )
                    }
                }
            }
        }

        // Right-edge touch strip (44dp wide, keyed on Unit so thumb expansion NEVER interrupts dragging!)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(44.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun updateAtY(y: Float) {
                            val total = latestItemCount
                            if (total <= 1) return
                            val span = latestUsableSpanPx.coerceAtLeast(1f)
                            val th = latestThumbHeightPx
                            val frac = ((y - th * 0.5f) / span).coerceIn(0f, 1f)
                            val target = (frac * (total - 1)).roundToInt().coerceIn(0, total - 1)
                            dragFraction = frac
                            dragTargetIndex = target
                            val (sec, det) = latestSectionFor(target)
                            if (sec.isNotEmpty()) {
                                lastPrimarySection = sec
                                lastSecondaryDetail = det
                                if (sec != lastHapticSection) {
                                    lastHapticSection = sec
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            latestOnScrollTo(frac, latestMetrics)
                        }

                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        down.consume()
                        updateAtY(down.position.y)

                        try {
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                updateAtY(change.position.y)
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            val handleTint = if (isDragging) {
                accent.copy(alpha = if (isDark) 0.40f else 0.30f)
            } else if (isDark) {
                Color(0xFF141822).copy(alpha = 0.36f)
            } else {
                Color.White.copy(alpha = 0.44f)
            }

            // Pure minimalist Apple Liquid Glass vertical capsule handle
            LiquidGlassSurface(
                backdrop = backdrop,
                shape = ContinuousCapsule,
                tintColor = handleTint,
                blurRadius = 2.5.dp,
                refractionHeight = if (isDragging) 14.dp else 9.dp,
                refractionAmount = if (isDragging) 18.dp else 12.dp,
                chromaticAberration = if (isDragging) 0.36f else 0.16f,
                highlightAlpha = if (isDragging) 1f else 0.90f,
                shadowRadius = 10.dp,
                darkSurface = isDark,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .graphicsLayer {
                        translationY = thumbTopPx
                        alpha = thumbAlpha
                    }
                    .systemGestureExclusion()
                    .size(width = thumbWidth, height = thumbHeight),
            ) {}
        }
    }
}

/**
 * Small circular crystal-clear Liquid Glass "Scroll to Top" button positioned slightly above the bottom bar.
 * Only appears after scrolling past the threshold distance and disappears automatically upon returning to top.
 */
@Composable
fun LiquidScrollToTopButton(
    visible: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isInDarkTheme()
    val desc = stringResource(R.string.action_scroll_to_top)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 460f)) + scaleIn(
            initialScale = 0.72f,
            animationSpec = spring(dampingRatio = 0.66f, stiffness = 440f),
        ),
        exit = fadeOut(spring(stiffness = 520f)) + scaleOut(
            targetScale = 0.72f,
            animationSpec = spring(stiffness = 520f),
        ),
        modifier = modifier,
    ) {
        LiquidGlassIconButton(
            onClick = onClick,
            backdrop = backdrop,
            contentDescription = desc,
            size = 42.dp,
            darkSurface = isDark,
        ) {
            Icon(
                imageVector = GlassIcons.ArrowUp,
                contentDescription = desc,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
