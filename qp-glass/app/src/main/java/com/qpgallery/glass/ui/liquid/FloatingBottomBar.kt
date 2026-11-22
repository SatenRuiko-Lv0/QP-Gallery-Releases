// Adapted from KernelSU v3.3.1 FloatingBottomBar.kt and compose-miuix-ui (Apache 2.0),
// refined with Apple iOS 26 Clear Liquid Glass optics + juicy Q-elastic over-boundary tugging physics.

package com.qpgallery.glass.ui.liquid

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.capsule.ContinuousCapsule
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

internal fun asymptoticRubberBand(delta: Float, maxDisplacement: Float): Float {
    if (delta == 0f || maxDisplacement <= 0f) return 0f
    val a = abs(delta)
    return maxDisplacement * (1f - 1f / (1f + a / (maxDisplacement * 1.95f))) * delta.sign
}

@Composable
fun RowScope.FloatingBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                onClick {
                    onClick()
                    true
                }
            }
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (activationKey) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    isGlassEnabled: Boolean = true,
    content: @Composable RowScope.((Int) -> Unit) -> Unit,
) {
    val isInDark = isInDarkTheme()
    val pillShape = remember { ContinuousCapsule }
    val accentColor = appleAccentColor()
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val activeGlass = isGlassEnabled && backdrop != null

    val containerColor = if (activeGlass) {
        if (isInDark) Color(0xFF0A0D13).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.22f)
    } else {
        surfaceContainer
    }
    val fresnelTopSheen = remember(isInDark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (isInDark) 0.10f else 0.22f),
            0.38f to Color.Transparent,
            1f to Color.White.copy(alpha = if (isInDark) 0.04f else 0.08f),
        )
    }

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    // Spring-coupled Q-elastic over-boundary pull: responsive liquid inertia while dragging + bouncy jelly rebound on release!
    var isHolding by remember { mutableStateOf(false) }
    var targetRawPull by remember { mutableStateOf(Offset.Zero) }
    var tugHapticFired by remember { mutableStateOf(false) }
    val maxPullXPx = with(density) { 22.dp.toPx() }
    val maxPullYPx = with(density) { 16.dp.toPx() }

    val targetRubberOffset = remember(targetRawPull, maxPullXPx, maxPullYPx) {
        Offset(
            x = asymptoticRubberBand(targetRawPull.x, maxPullXPx),
            y = asymptoticRubberBand(targetRawPull.y, maxPullYPx),
        )
    }
    val panelRubberOffset by animateOffsetAsState(
        targetValue = targetRubberOffset,
        animationSpec = if (isHolding) {
            spring(dampingRatio = 0.56f, stiffness = 560f)
        } else {
            spring(dampingRatio = 0.35f, stiffness = 310f)
        },
        label = "floatingBarElasticPull",
    )
    val pullTension by remember(maxPullXPx, maxPullYPx) {
        derivedStateOf {
            val nx = if (maxPullXPx > 0f) abs(panelRubberOffset.x) / maxPullXPx else 0f
            val ny = if (maxPullYPx > 0f) abs(panelRubberOffset.y) / maxPullYPx else 0f
            hypot(nx, ny).coerceAtMost(1.35f)
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    var pendingTarget by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }
    val onSelectedUpdated by rememberUpdatedState(onSelected)
    val latestTabsCount by rememberUpdatedState(tabsCount)
    val latestIsLtr by rememberUpdatedState(isLtr)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (latestIsLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, latestTabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 82f / 56f,
            canDrag = { true },
            onDragStarted = { position ->
                isHolding = true
                tugHapticFired = false
                val idx = indexAt(position.x)
                pendingTarget = idx.toFloat()
                lastHapticTab = idx
                updateValue(idx.toFloat())
            },
            onDragStopped = {
                isHolding = false
                val targetIndex = pendingTarget.roundToInt().coerceIn(0, latestTabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectedUpdated(targetIndex)
                }
                pendingTarget = targetIndex.toFloat()
                updateValue(targetIndex.toFloat())
                targetRawPull = Offset.Zero
            },
            onDragCancelled = {
                isHolding = false
                pendingTarget = currentIndex.toFloat()
                updateValue(currentIndex.toFloat())
                targetRawPull = Offset.Zero
            },
            onDrag = { _, _, dragAmount ->
                if (tabWidthPx > 0f && (dragAmount.x != 0f || dragAmount.y != 0f)) {
                    val maxTab = (latestTabsCount - 1).toFloat()
                    val deltaTabs = dragAmount.x / tabWidthPx * if (latestIsLtr) 1f else -1f
                    val unclamped = pendingTarget + deltaTabs
                    val clamped = unclamped.coerceIn(0f, maxTab)
                    val crossedTab = clamped.roundToInt().coerceIn(0, latestTabsCount - 1)
                    if (crossedTab != lastHapticTab) {
                        lastHapticTab = crossedTab
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    pendingTarget = clamped
                    updateValue(clamped)

                    val beyondBound = unclamped < 0f || unclamped > maxTab
                    val extraPullX = if (beyondBound) dragAmount.x * 1.45f else dragAmount.x * 0.24f
                    val extraPullY = dragAmount.y * 1.15f
                    val nextPull = Offset(
                        x = (targetRawPull.x + extraPullX).coerceIn(-460f, 460f),
                        y = (targetRawPull.y + extraPullY).coerceIn(-360f, 360f),
                    )
                    targetRawPull = nextPull
                    if (!tugHapticFired && hypot(nextPull.x / maxPullXPx, nextPull.y / maxPullYPx) > 1.25f) {
                        tugHapticFired = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
        )
    }

    SideEffect {
        dampedDragAnimation.valueRange = 0f..(tabsCount - 1).coerceAtLeast(1).toFloat()
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            pendingTarget = selectedIndex.toFloat()
            lastHapticTab = selectedIndex
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (index !in 0 until tabsCount) return
        if (currentIndex != index) {
            currentIndex = index
            pendingTarget = index.toFloat()
            lastHapticTab = index
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectedUpdated(index)
        }
        dampedDragAnimation.animateToValue(index.toFloat())
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx, dampedDragAnimation) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelRubberOffset.x
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelRubberOffset.x,
                    size.height / 2f + panelRubberOffset.y * 0.6f,
                )
            },
        )
    }

    val baseHighlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = 90f)
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .selectableGroup()
                .graphicsLayer {
                    val rx = panelRubberOffset.x
                    val ry = panelRubberOffset.y
                    val nx = if (maxPullXPx > 0f) (rx / maxPullXPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                    val ny = if (maxPullYPx > 0f) (ry / maxPullYPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                    // Strictly level 0-deg horizontal pull with opposite-edge Q-elastic stretch (no tilt/rotation!)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (0.5f - nx * 0.34f).fastCoerceIn(0.12f, 0.88f),
                        pivotFractionY = (0.5f - ny * 0.34f).fastCoerceIn(0.12f, 0.88f),
                    )
                    translationX = rx
                    translationY = ry
                    scaleX = 1f + abs(nx) * 0.10f - abs(ny) * 0.045f
                    scaleY = 1f + abs(ny) * 0.12f - abs(nx) * 0.045f
                }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 16.dp,
                        color = Color.Black,
                        alpha = if (isInDark) 0.34f else 0.11f,
                    ),
                )
                .then(
                    if (activeGlass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, 48.dp.toPx())
                                vibrancy(brightness = 0f, contrast = 1.08f, saturation = 1.68f)
                                blur(2.6.dp.toPx(), 2.6.dp.toPx())
                                lens(
                                    refractionHeight = (24.dp + 5.dp * pullTension).toPx(),
                                    refractionAmount = (28.dp + 8.dp * pullTension).toPx(),
                                    depthEffect = true,
                                    chromaticAberration = 0.16f + 0.30f * pullTension,
                                )
                            },
                            highlight = { baseHighlight.value.copy(alpha = (0.88f + 0.12f * pullTension).coerceAtMost(1f)) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDragAnimation.pressProgress)
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                                drawRect(fresnelTopSheen)
                            },
                        )
                    } else {
                        Modifier.background(containerColor, pillShape)
                    }
                )
                .then(
                    if (activeGlass) {
                        interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .then(dampedDragAnimation.modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                content(::activateTab)
            }
        }

        if (activeGlass) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalContentColor provides accentColor,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelRubberOffset.x
                            translationY = panelRubberOffset.y
                        }
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { content(::activateTab) },
                )
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (activeGlass && combinedBackdrop != null) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            val baseTx = if (isLtr) progressOffset else -progressOffset
                            translationX = baseTx + panelRubberOffset.x * 1.36f
                            translationY = panelRubberOffset.y * 1.32f
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                val tension = pullTension
                                lens(
                                    refractionHeight = lerp(6.dp.toPx(), 13.dp.toPx(), progress) + 4.dp.toPx() * tension,
                                    refractionAmount = lerp(10.dp.toPx(), 20.dp.toPx(), progress) + 7.dp.toPx() * tension,
                                    depthEffect = true,
                                    chromaticAberration = lerp(0.18f, 0.60f, progress) + 0.28f * tension,
                                )
                            },
                            highlight = {
                                pillHighlight.value.copy(
                                    alpha = (lerp(0.62f, 1.0f, dampedDragAnimation.pressProgress) + 0.14f * pullTension)
                                        .coerceAtMost(1f),
                                )
                            },
                            layerBlock = {
                                val nx = if (maxPullXPx > 0f) abs(panelRubberOffset.x) / maxPullXPx else 0f
                                val ny = if (maxPullYPx > 0f) abs(panelRubberOffset.y) / maxPullYPx else 0f
                                scaleX = dampedDragAnimation.scaleX * (1f + nx * 0.16f - ny * 0.07f)
                                scaleY = dampedDragAnimation.scaleY * (1f + ny * 0.16f - nx * 0.07f)
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.24f, 0.24f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.24f, 0.24f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!isInDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                    alpha = 1f - progress * 0.85f,
                                )
                                drawRect(Color.Black.copy(alpha = 0.02f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = lerp(2.5f, 8f, progress).dp,
                                color = Color.Black.copy(alpha = if (isInDark) 0.18f else 0.10f),
                                alpha = lerp(0.32f, 1f, progress),
                            )
                        }
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            val baseTx = if (isLtr) progressOffset else -progressOffset
                            translationX = baseTx + panelRubberOffset.x * 1.25f
                            translationY = panelRubberOffset.y * 1.15f
                        }
                        .clip(pillShape)
                        .background(accentColor.copy(alpha = 0.16f), pillShape)
                        .height(56.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(LocalContentColor provides accentColor) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                .height(56.dp)
                                .graphicsLayer {
                                    val progressOffset = dampedDragAnimation.value * tabWidthPx
                                    translationX = if (isLtr) -progressOffset else progressOffset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = { content(::activateTab) },
                        )
                    }
                }
            }
        }
    }
}
