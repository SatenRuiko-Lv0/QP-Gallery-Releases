package com.qpgallery.glass.ui.liquid

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.ui.theme.LocalEnableLiquidGlass
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Crystal-clear Apple iOS 26 Liquid Glass surface with optional juicy Q-elastic 2D boundary tugging:
 * - Spring-coupled liquid follow while dragging (organic jelly inertia, never stiff!)
 * - Opposite-edge TransformOrigin anchoring so pulling past bounds creates real tugging elongation & necking
 * - Low-damping (0.34f) Q-elastic jelly rebound on release + progressive tension haptic tick
 */
@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = ContinuousCapsule,
    tintColor: Color? = null,
    blurRadius: Dp = 3.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 22.dp,
    chromaticAberration: Float = 0.18f,
    highlightAlpha: Float = 0.90f,
    shadowRadius: Dp = 14.dp,
    darkSurface: Boolean = isInDarkTheme(),
    interactive: Boolean = false,
    elasticDrag: Boolean = false,
    elasticYieldDownward: Boolean = false,
    maxPullX: Dp = 22.dp,
    maxPullY: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val liquidEnabled = LocalEnableLiquidGlass.current && backdrop != null
    val resolvedTint = tintColor ?: if (darkSurface) {
        if (liquidEnabled) Color(0xFF0A0D13).copy(alpha = 0.15f) else Color(0xFF181C24).copy(alpha = 0.86f)
    } else {
        if (liquidEnabled) Color.White.copy(alpha = 0.18f) else Color(0xFFF5F7FB).copy(alpha = 0.90f)
    }
    val fresnelSheen = remember(darkSurface) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (darkSurface) 0.12f else 0.28f),
            0.36f to Color.Transparent,
            1f to Color.White.copy(alpha = if (darkSurface) 0.04f else 0.10f),
        )
    }
    val highlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = -45f)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }

    // Spring-coupled Q-elastic 2D boundary tugging state
    var dragPressed by remember { mutableStateOf(false) }
    var targetRawPull by remember { mutableStateOf(Offset.Zero) }
    val maxPullXPx = with(density) { maxPullX.toPx() }
    val maxPullYPx = with(density) { maxPullY.toPx() }
    val slopPx = with(density) { 10.dp.toPx() }

    val targetRubberOffset = remember(targetRawPull, maxPullXPx, maxPullYPx) {
        Offset(
            x = asymptoticRubberBand(targetRawPull.x, maxPullXPx),
            y = asymptoticRubberBand(targetRawPull.y, maxPullYPx),
        )
    }
    val rubberOffset by animateOffsetAsState(
        targetValue = if (elasticDrag) targetRubberOffset else Offset.Zero,
        animationSpec = if (dragPressed) {
            spring(dampingRatio = 0.54f, stiffness = 540f)
        } else {
            spring(dampingRatio = 0.34f, stiffness = 300f)
        },
        label = "elasticSurfacePull",
    )
    val pullTension by remember(maxPullXPx, maxPullYPx) {
        derivedStateOf {
            val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
            val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
            hypot(nx, ny).coerceAtMost(1.35f)
        }
    }
    val elasticPressScale by animateFloatAsState(
        targetValue = if (elasticDrag && dragPressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 360f),
        label = "elasticSurfaceScale",
    )

    val elasticTransformModifier = if (elasticDrag) {
        Modifier
            .graphicsLayer {
                val rx = rubberOffset.x
                val ry = rubberOffset.y
                val nx = if (maxPullXPx > 0f) (rx / maxPullXPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                val ny = if (maxPullYPx > 0f) (ry / maxPullYPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                // Strictly level 0-deg horizontal elastic drag (NO tilt/rotation on wide top/bottom bars!)
                transformOrigin = TransformOrigin(
                    pivotFractionX = (0.5f - nx * 0.34f).fastCoerceIn(0.14f, 0.86f),
                    pivotFractionY = (0.5f - ny * 0.34f).fastCoerceIn(0.14f, 0.86f),
                )
                translationX = rx
                translationY = ry
                scaleX = elasticPressScale * (1f + abs(nx) * 0.095f - abs(ny) * 0.045f)
                scaleY = elasticPressScale * (1f + abs(ny) * 0.115f - abs(nx) * 0.045f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    dragPressed = true
                    var totalDrag = Offset.Zero
                    var stolen = false
                    var tugTicked = false
                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            val other = event.changes.fastFirstOrNull { it.pressed }
                            if (other == null) {
                                if (stolen) change.consume()
                                break
                            } else {
                                pointerId = other.id
                                continue
                            }
                        }
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            totalDrag += delta
                            if (!stolen && hypot(totalDrag.x, totalDrag.y) > slopPx) {
                                if (elasticYieldDownward && totalDrag.y > 0f && totalDrag.y >= abs(totalDrag.x) * 0.35f) {
                                    // Yield downward / down-left pull cleanly to child pull-down menu buttons (Sort / More)
                                    break
                                }
                                stolen = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (stolen) {
                                change.consume()
                                val nextPull = Offset(
                                    x = (targetRawPull.x + delta.x * 1.25f).coerceIn(-440f, 440f),
                                    y = (targetRawPull.y + delta.y * 1.25f).coerceIn(-360f, 360f),
                                )
                                targetRawPull = nextPull
                                if (!tugTicked && hypot(nextPull.x / maxPullXPx, nextPull.y / maxPullYPx) > 1.3f) {
                                    tugTicked = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                    dragPressed = false
                    targetRawPull = Offset.Zero
                }
            }
    } else {
        Modifier
    }

    val effectiveBlur = if (elasticDrag && dragPressed) (blurRadius * 0.60f) else blurRadius
    val glassModifier = if (liquidEnabled) {
        Modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = shadowRadius,
                    color = Color.Black,
                    alpha = if (darkSurface) 0.32f else 0.10f,
                ),
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    val pad = maxOf((refractionAmount + 8.dp).toPx(), effectiveBlur.toPx() * 2f, 30.dp.toPx())
                    padding = maxOf(padding, pad)
                    vibrancy(brightness = 0f, contrast = 1.07f, saturation = 1.62f)
                    if (effectiveBlur > 0.2.dp) {
                        blur(effectiveBlur.toPx(), effectiveBlur.toPx())
                    }
                    lens(
                        refractionHeight = (refractionHeight + 5.dp * pullTension).toPx(),
                        refractionAmount = (refractionAmount + 8.dp * pullTension).toPx(),
                        depthEffect = true,
                        chromaticAberration = chromaticAberration + 0.32f * pullTension,
                    )
                },
                highlight = { highlight.value.copy(alpha = (highlightAlpha + 0.10f * pullTension).coerceAtMost(1f)) },
                onDrawSurface = {
                    drawRect(resolvedTint)
                    drawRect(fresnelSheen)
                },
            )
            .innerShadow(shape = shape) {
                InnerShadow(
                    radius = 3.dp + 3.5.dp * pullTension,
                    color = Color.Black.copy(alpha = if (darkSurface) 0.20f else 0.08f),
                    alpha = 0.45f + 0.48f * pullTension,
                )
            }
            .then(
                if (interactive || elasticDrag) {
                    interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
    } else {
        val borderColor = if (darkSurface) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
        Modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = shadowRadius,
                    color = Color.Black,
                    alpha = if (darkSurface) 0.28f else 0.10f,
                ),
            )
            .clip(shape)
            .background(resolvedTint, shape)
            .border(0.75.dp, borderColor, shape)
    }

    Box(
        modifier = modifier
            .then(elasticTransformModifier)
            .then(glassModifier),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Crystal-clear circular Liquid Glass action button with juicy Q-elastic teardrop boundary tugging:
 * - Pulling in any direction anchors the opposite rim & elongates the glass droplet (+20% stretch, -9% pinch)
 * - Releasing springs back with a silky jelly wobble (dampingRatio = 0.34f)
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    darkSurface: Boolean = isInDarkTheme(),
    tintColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val latestOnClick by rememberUpdatedState(onClick)

    var pressed by remember { mutableStateOf(false) }
    var targetRawPull by remember { mutableStateOf(Offset.Zero) }
    val maxPullPx = with(density) { 18.dp.toPx() }
    val cancelRadiusPx = with(density) { 54.dp.toPx() }

    val targetRubber = remember(targetRawPull, maxPullPx) {
        Offset(
            x = asymptoticRubberBand(targetRawPull.x, maxPullPx),
            y = asymptoticRubberBand(targetRawPull.y, maxPullPx),
        )
    }
    val rubberOffset by animateOffsetAsState(
        targetValue = targetRubber,
        animationSpec = if (pressed) {
            spring(dampingRatio = 0.54f, stiffness = 560f)
        } else {
            spring(dampingRatio = 0.34f, stiffness = 310f)
        },
        label = "glassButtonPull",
    )
    val pullTension by remember(maxPullPx) {
        derivedStateOf {
            if (maxPullPx <= 0f) 0f
            else hypot(rubberOffset.x / maxPullPx, rubberOffset.y / maxPullPx).coerceAtMost(1.35f)
        }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 380f),
        label = "glassButtonScale",
    )

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = ContinuousCapsule,
        tintColor = tintColor,
        blurRadius = if (pressed) 1.4.dp else 2.8.dp,
        refractionHeight = 14.dp + 4.dp * pullTension,
        refractionAmount = 20.dp + 8.dp * pullTension,
        chromaticAberration = (if (pressed) 0.42f else 0.18f) + 0.30f * pullTension,
        highlightAlpha = if (pressed) 1.0f else 0.90f,
        shadowRadius = 12.dp,
        darkSurface = darkSurface,
        interactive = true,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val nx = if (maxPullPx > 0f) (rubberOffset.x / maxPullPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                val ny = if (maxPullPx > 0f) (rubberOffset.y / maxPullPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                transformOrigin = TransformOrigin(
                    pivotFractionX = (0.5f - nx * 0.34f).fastCoerceIn(0.14f, 0.86f),
                    pivotFractionY = (0.5f - ny * 0.34f).fastCoerceIn(0.14f, 0.86f),
                )
                translationX = rubberOffset.x
                translationY = rubberOffset.y
                scaleX = pressScale * (1f + abs(nx) * 0.18f - abs(ny) * 0.075f)
                scaleY = pressScale * (1f + abs(ny) * 0.18f - abs(nx) * 0.075f)
            }
            .semantics {
                role = Role.Button
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                onClick {
                    latestOnClick()
                    true
                }
            }
            .pointerInput(Unit) {
                var totalDelta = Offset.Zero
                var tugTicked = false
                inspectDragGestures(
                    consumeDrag = false,
                    onDragStart = {
                        pressed = true
                        totalDelta = Offset.Zero
                        tugTicked = false
                    },
                    onDragEnd = {
                        val shouldClick = hypot(totalDelta.x, totalDelta.y) <= cancelRadiusPx
                        pressed = false
                        targetRawPull = Offset.Zero
                        if (shouldClick) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnClick()
                        }
                    },
                    onDragCancel = {
                        pressed = false
                        targetRawPull = Offset.Zero
                    },
                ) { _, dragAmount ->
                    totalDelta += dragAmount
                    if (dragAmount != Offset.Zero) {
                        val nextPull = Offset(
                            x = (targetRawPull.x + dragAmount.x * 1.25f).coerceIn(-360f, 360f),
                            y = (targetRawPull.y + dragAmount.y * 1.25f).coerceIn(-360f, 360f),
                        )
                        targetRawPull = nextPull
                        if (!tugTicked && hypot(nextPull.x / maxPullPx, nextPull.y / maxPullPx) > 1.3f) {
                            tugTicked = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            },
        content = content,
    )
}

/**
 * Compact numeric/text crystal Liquid Glass capsule badge (e.g. immersive comic page counter),
 * with subtle Q-elastic 2D pull feedback.
 */
@Composable
fun LiquidGlassBadge(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    darkSurface: Boolean = true,
    tintColor: Color = Color(0xFF0A0D13).copy(alpha = 0.22f),
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        backdrop = backdrop,
        shape = ContinuousRoundedRectangle(12.dp),
        tintColor = tintColor,
        blurRadius = 3.dp,
        refractionHeight = 10.dp,
        refractionAmount = 14.dp,
        chromaticAberration = 0.18f,
        highlightAlpha = 0.88f,
        shadowRadius = 10.dp,
        darkSurface = darkSurface,
        elasticDrag = true,
        maxPullX = 14.dp,
        maxPullY = 10.dp,
        modifier = modifier,
        content = content,
    )
}

/**
 * Authentic iOS 26 Fluid Liquid Glass Slider:
 * - Rock-solid 1:1 finger tracking across 10 to 10,000+ pages (pointerInput NEVER recreated on layout jitter!).
 * - Optical glass rail swells fluidly on touch (6.5dp -> 11dp) and stretches from the opposite anchor
 *   when tugged past 0% or 100%, rebounding with juicy Q-elastic spring physics!
 * - Pure crystal water-drop thumb AT REST and crystal chromatic magnifying lens on drag.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    enabled: Boolean = true,
    darkSurface: Boolean = isInDarkTheme(),
    glassFill: Boolean = false,
    accentColor: Color = appleAccentColor(),
    trackColor: Color = if (darkSurface) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.08f),
    contentDescription: String? = null,
) {
    val liquidEnabled = LocalEnableLiquidGlass.current && backdrop != null
    val trackBackdrop = rememberLayerBackdrop()
    val thumbShape = remember { ContinuousCapsule }
    val thumbHighlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = 90f)
    val railHighlight = rememberGravityRotatedHighlight(IosLiquidSpecular, extraDegrees = -45f)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val latestValue by rememberUpdatedState(value)
    val latestValueRange by rememberUpdatedState(valueRange)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnCancel by rememberUpdatedState(onDragCancel)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)
        val trackHeightPx = with(density) { 46.dp.toPx() }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val latestTrackWidth by rememberUpdatedState(trackWidth)
        val latestIsLtr by rememberUpdatedState(isLtr)

        val animationScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(value) }
        var wasAtBound by remember { mutableStateOf(false) }

        // Spring-coupled Q-elastic over-boundary tugging state
        var targetRawPull by remember { mutableStateOf(Offset.Zero) }
        val maxPullXPx = with(density) { 20.dp.toPx() }
        val maxPullYPx = with(density) { 14.dp.toPx() }

        val targetRubber = remember(targetRawPull, maxPullXPx, maxPullYPx) {
            Offset(
                x = asymptoticRubberBand(targetRawPull.x, maxPullXPx),
                y = asymptoticRubberBand(targetRawPull.y, maxPullYPx),
            )
        }
        val rubberOffset by animateOffsetAsState(
            targetValue = targetRubber,
            animationSpec = if (isDragging) {
                spring(dampingRatio = 0.56f, stiffness = 560f)
            } else {
                spring(dampingRatio = 0.34f, stiffness = 310f)
            },
            label = "sliderElasticPull",
        )
        val pullTension by remember(maxPullXPx, maxPullYPx) {
            derivedStateOf {
                val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
                val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
                hypot(nx, ny).coerceAtMost(1.35f)
            }
        }

        val railHeight by animateDpAsState(
            targetValue = if (isDragging) 11.dp else 6.5.dp,
            animationSpec = spring(dampingRatio = 0.46f, stiffness = 380f),
            label = "liquidSliderRailHeight",
        )

        fun valueAtX(x: Float): Float {
            val range = latestValueRange
            val span = (range.endInclusive - range.start).coerceAtLeast(1e-6f)
            val width = latestTrackWidth.toFloat().coerceAtLeast(1f)
            val ratio = (x / width).coerceIn(0f, 1f)
            return (if (latestIsLtr) range.start + span * ratio else range.endInclusive - span * ratio)
                .coerceIn(range)
        }

        // CRITICAL: Remember ONLY on animationScope so layout/digit-width changes NEVER cancel an active drag!
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = latestValue.coerceIn(latestValueRange),
                valueRange = latestValueRange,
                visibilityThreshold = 0.0005f,
                initialScale = 1f,
                pressedScale = 1.54f,
                consumeDrag = true,
                onDragStarted = { position ->
                    if (!latestEnabled) return@DampedDragAnimation
                    isDragging = true
                    wasAtBound = false
                    val startVal = valueAtX(position.x)
                    dragValue = startVal
                    updateValue(startVal)
                    latestOnChange(startVal)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onDragStopped = {
                    if (!latestEnabled) return@DampedDragAnimation
                    isDragging = false
                    val committed = dragValue.coerceIn(latestValueRange)
                    latestOnChange(committed)
                    latestOnFinished?.invoke(committed)
                    targetRawPull = Offset.Zero
                },
                onDragCancelled = {
                    isDragging = false
                    latestOnCancel?.invoke()
                    targetRawPull = Offset.Zero
                },
                onDrag = { _, position, _ ->
                    if (!latestEnabled) return@DampedDragAnimation
                    val width = latestTrackWidth.toFloat().coerceAtLeast(1f)
                    val next = valueAtX(position.x)
                    dragValue = next
                    // Smooth direct tracking locked 1:1 to finger X position across 10 to 10,000+ pages!
                    updateValue(next)
                    latestOnChange(next)

                    // Direct physical overshoot measurement from finger position relative to track bounds
                    val overX = when {
                        position.x < 0f -> position.x * 1.35f
                        position.x > width -> (position.x - width) * 1.35f
                        else -> 0f
                    }
                    val overY = (position.y - trackHeightPx * 0.5f) * 0.95f
                    val hitBound = overX != 0f
                    if (hitBound && !wasAtBound) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    wasAtBound = hitBound
                    targetRawPull = Offset(
                        x = overX.coerceIn(-420f, 420f),
                        y = overY.coerceIn(-300f, 300f),
                    )
                },
            )
        }

        SideEffect {
            dampedDragAnimation.valueRange = valueRange
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestValue to latestValueRange }
                .collectLatest { (external, range) ->
                    val clamped = external.coerceIn(range)
                    if (!isDragging && clamped != dampedDragAnimation.targetValue) {
                        dragValue = clamped
                        dampedDragAnimation.updateValue(clamped)
                    }
                }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .then(if (enabled) dampedDragAnimation.modifier else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fillBrush = remember(glassFill, accentColor, darkSurface) {
                if (glassFill) {
                    Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.42f),
                        0.65f to Color.White.copy(alpha = 0.64f),
                        1f to Color.White.copy(alpha = 0.84f),
                    )
                } else {
                    Brush.horizontalGradient(
                        0f to accentColor.copy(alpha = if (darkSurface) 0.48f else 0.44f),
                        0.7f to accentColor.copy(alpha = if (darkSurface) 0.72f else 0.68f),
                        1f to lerpColor(accentColor, Color.White, 0.20f).copy(alpha = 0.84f),
                    )
                }
            }
            val railTopSpecular = remember(glassFill, darkSurface) {
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (glassFill || darkSurface) 0.36f else 0.42f),
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.08f),
                )
            }

            // Optical Liquid Glass Rail with opposite-end TransformOrigin anchoring on boundary tug!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val nx = if (maxPullXPx > 0f) (rubberOffset.x / maxPullXPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                        val ny = if (maxPullYPx > 0f) (rubberOffset.y / maxPullYPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                        // Pull right past 100% -> anchor left end (0f) & stretch right; pull left past 0% -> anchor right end (1f)!
                        transformOrigin = TransformOrigin(
                            pivotFractionX = when {
                                nx > 0.02f -> 0f
                                nx < -0.02f -> 1f
                                else -> 0.5f
                            },
                            pivotFractionY = 0.5f,
                        )
                        translationX = rubberOffset.x * 0.28f
                        translationY = rubberOffset.y * 0.32f
                        scaleX = 1f + abs(nx) * 0.095f
                        scaleY = 1f - abs(nx) * 0.18f + abs(ny) * 0.06f
                    }
                    .layerBackdrop(trackBackdrop),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(railHeight)
                        .then(
                            if (liquidEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        vibrancy(brightness = 0f, contrast = 1.06f, saturation = 1.50f)
                                        blur(1.8.dp.toPx(), 1.8.dp.toPx())
                                        lens(
                                            refractionHeight = 4.dp.toPx(),
                                            refractionAmount = 7.dp.toPx(),
                                            depthEffect = true,
                                            chromaticAberration = 0.08f + 0.16f * pullTension,
                                        )
                                    },
                                    highlight = { railHighlight.value.copy(alpha = if (isDragging) 0.78f else 0.48f) },
                                    onDrawSurface = { drawRect(trackColor) },
                                )
                            } else {
                                Modifier
                                    .clip(ContinuousCapsule)
                                    .background(trackColor)
                            }
                        )
                        .innerShadow(shape = ContinuousCapsule) {
                            InnerShadow(
                                radius = 3.dp,
                                color = Color.Black.copy(alpha = if (darkSurface) 0.30f else 0.14f),
                                alpha = 0.85f,
                            )
                        },
                )

                Box(
                    Modifier
                        .clip(ContinuousCapsule)
                        .background(fillBrush)
                        .background(railTopSpecular)
                        .height(railHeight)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val w = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                            layout(w.coerceAtLeast(0), placeable.height) {
                                placeable.place(0, 0)
                            }
                        },
                )
            }

            val scaledTrackBackdrop = rememberTransformedBackdrop(trackBackdrop) { drawWrapped ->
                val progress = dampedDragAnimation.pressProgress
                val sx = lerp(0.90f, 1.0f, progress)
                val sy = lerp(0.90f, 1.0f, progress)
                scale(sx, sy) {
                    drawWrapped()
                }
            }
            val combinedThumbBackdrop = backdrop?.let {
                rememberCombinedBackdrop(it, scaledTrackBackdrop)
            }

            val thumbModifier = Modifier.graphicsLayer {
                val baseTx = (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                    .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f)
                val tx = (if (isLtr) baseTx else -baseTx) + rubberOffset.x
                translationX = tx
                translationY = rubberOffset.y * 0.85f
            }

            if (liquidEnabled && combinedThumbBackdrop != null) {
                Box(
                    thumbModifier
                        .dropShadow(
                            shape = thumbShape,
                            shadow = Shadow(
                                radius = 9.dp,
                                color = Color.Black,
                                alpha = if (darkSurface) 0.34f else 0.14f,
                            ),
                        )
                        .drawBackdrop(
                            backdrop = combinedThumbBackdrop,
                            shape = { thumbShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                val tension = pullTension
                                vibrancy(brightness = 0f, contrast = 1.08f, saturation = 1.65f)
                                val r = lerp(0.7.dp.toPx(), 0f, progress)
                                if (r > 0.25f) blur(r, r)
                                lens(
                                    refractionHeight = lerp(9.dp.toPx(), 14.dp.toPx(), progress) + 4.dp.toPx() * tension,
                                    refractionAmount = lerp(15.dp.toPx(), 23.dp.toPx(), progress) + 7.dp.toPx() * tension,
                                    depthEffect = true,
                                    chromaticAberration = lerp(0.28f, 0.62f, progress) + 0.28f * tension,
                                )
                            },
                            highlight = {
                                thumbHighlight.value.copy(
                                    alpha = (lerp(0.92f, 1.0f, dampedDragAnimation.pressProgress) + 0.08f * pullTension)
                                        .coerceAtMost(1f),
                                )
                            },
                            layerBlock = {
                                val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
                                val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
                                scaleX = dampedDragAnimation.scaleX * (1f + nx * 0.22f - ny * 0.09f)
                                scaleY = dampedDragAnimation.scaleY * (1f + ny * 0.20f - nx * 0.09f)
                                val v = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.24f, 0.24f)
                                scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.24f, 0.24f)
                            },
                            onDrawSurface = {
                                val p = dampedDragAnimation.pressProgress
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = lerp(if (darkSurface) 0.22f else 0.34f, 0.08f, p)),
                                        0.42f to Color.White.copy(alpha = lerp(0.05f, 0.01f, p)),
                                        1f to Color.White.copy(alpha = lerp(if (darkSurface) 0.14f else 0.22f, 0.05f, p)),
                                    ),
                                )
                            },
                        )
                        .innerShadow(shape = thumbShape) {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = lerp(3.5f, 7f, progress).dp,
                                color = Color.Black.copy(alpha = if (darkSurface) 0.22f else 0.12f),
                                alpha = lerp(0.55f, 1f, progress),
                            )
                        }
                        .size(40.dp, 24.dp),
                )
            } else {
                val fallbackBorder = Color.White.copy(alpha = if (darkSurface) 0.65f else 0.88f)
                Box(
                    thumbModifier
                        .graphicsLayer {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                        }
                        .dropShadow(
                            shape = thumbShape,
                            shadow = Shadow(radius = 5.dp, color = Color.Black, alpha = 0.20f),
                        )
                        .clip(thumbShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.56f), Color.White.copy(alpha = 0.28f)),
                            ),
                        )
                        .border(1.dp, fallbackBorder, thumbShape)
                        .size(38.dp, 24.dp),
                )
            }
        }
    }
}

/**
 * Crystal-clear iOS 26 Liquid Glass Toggle with Q-elastic boundary tugging & jelly bounce.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = appleAccentColor(),
) {
    val isDark = isInDarkTheme()
    val liquidEnabled = LocalEnableLiquidGlass.current && backdrop != null
    val trackColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val trackBorderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 22.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val latestChecked by rememberUpdatedState(checked)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnChange by rememberUpdatedState(onCheckedChange)
    val latestIsLtr by rememberUpdatedState(isLtr)

    var didDrag by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    var targetRawPull by remember { mutableStateOf(Offset.Zero) }
    val maxPullXPx = with(density) { 12.dp.toPx() }
    val maxPullYPx = with(density) { 9.dp.toPx() }

    val targetRubber = remember(targetRawPull, maxPullXPx, maxPullYPx) {
        Offset(
            x = asymptoticRubberBand(targetRawPull.x, maxPullXPx),
            y = asymptoticRubberBand(targetRawPull.y, maxPullYPx),
        )
    }
    val rubberOffset by animateOffsetAsState(
        targetValue = targetRubber,
        animationSpec = if (isHolding) {
            spring(dampingRatio = 0.54f, stiffness = 560f)
        } else {
            spring(dampingRatio = 0.34f, stiffness = 310f)
        },
        label = "toggleElasticPull",
    )
    val pullTension by remember(maxPullXPx, maxPullYPx) {
        derivedStateOf {
            val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
            val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
            hypot(nx, ny).coerceAtMost(1.35f)
        }
    }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = if (latestChecked) 1f else 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.46f,
            consumeDrag = true,
            onDragStarted = {
                isHolding = true
                didDrag = false
                fraction = if (latestChecked) 1f else 0f
            },
            onDragStopped = {
                isHolding = false
                if (!latestEnabled) return@DampedDragAnimation
                val next = if (didDrag) fraction >= 0.5f else !latestChecked
                if (next != latestChecked) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                fraction = if (next) 1f else 0f
                didDrag = false
                updateValue(fraction)
                latestOnChange(next)
                targetRawPull = Offset.Zero
            },
            onDragCancelled = {
                isHolding = false
                fraction = if (latestChecked) 1f else 0f
                didDrag = false
                updateValue(fraction)
                targetRawPull = Offset.Zero
            },
            onDrag = { _, _, dragAmount ->
                if (!latestEnabled) return@DampedDragAnimation
                if (!didDrag && abs(dragAmount.x) > 1.5f) {
                    didDrag = true
                }
                val delta = dragAmount.x / dragWidth
                val unclamped = if (latestIsLtr) fraction + delta else fraction - delta
                val clamped = unclamped.fastCoerceIn(0f, 1f)
                fraction = clamped
                updateValue(clamped)

                val pullX = if (unclamped < 0f || unclamped > 1f) dragAmount.x * 1.3f else -targetRawPull.x * 0.28f
                val pullY = dragAmount.y * 1.0f
                targetRawPull = Offset(
                    x = (targetRawPull.x + pullX).coerceIn(-240f, 240f),
                    y = (targetRawPull.y + pullY).coerceIn(-200f, 200f),
                )
            },
        )
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction && !didDrag) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val thumbHighlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = 90f)
    val scaledTrackBackdrop = rememberTransformedBackdrop(trackBackdrop) { drawWrapped ->
        val progress = dampedDragAnimation.pressProgress
        val sx = lerp(0.86f, 0.92f, progress)
        val sy = lerp(0.86f, 0.92f, progress)
        scale(sx, sy) { drawWrapped() }
    }
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, scaledTrackBackdrop) }
    val pillShape = remember { ContinuousCapsule }

    Box(
        modifier = modifier
            .semantics { role = Role.Switch }
            .then(if (enabled) dampedDragAnimation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val nx = if (maxPullXPx > 0f) (rubberOffset.x / maxPullXPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                    val ny = if (maxPullYPx > 0f) (rubberOffset.y / maxPullYPx).fastCoerceIn(-1.2f, 1.2f) else 0f
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (0.5f - nx * 0.35f).fastCoerceIn(0.15f, 0.85f),
                        pivotFractionY = 0.5f,
                    )
                    translationX = rubberOffset.x * 0.38f
                    translationY = rubberOffset.y * 0.34f
                    scaleX = 1f + abs(nx) * 0.12f - abs(ny) * 0.05f
                    scaleY = 1f + abs(ny) * 0.10f - abs(nx) * 0.06f
                }
                .layerBackdrop(trackBackdrop)
                .clip(pillShape)
                .drawBehind {
                    drawRect(
                        lerpColor(
                            trackColor,
                            accentColor.copy(alpha = 0.80f),
                            dampedDragAnimation.value.coerceIn(0f, 1f),
                        ),
                    )
                }
                .innerShadow(shape = pillShape) {
                    InnerShadow(
                        radius = 3.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.32f else 0.14f),
                        alpha = 0.80f,
                    )
                }
                .border(0.65.dp, trackBorderColor, pillShape)
                .size(58.dp, 28.dp),
        )

        val thumbPositionModifier = Modifier.graphicsLayer {
            val f = dampedDragAnimation.value.coerceIn(0f, 1f)
            val pad = 2.dp.toPx()
            val baseTx = if (isLtr) lerp(pad, pad + dragWidth, f) else lerp(-pad, -(pad + dragWidth), f)
            translationX = baseTx + rubberOffset.x * 0.90f
            translationY = rubberOffset.y * 0.80f
        }

        if (liquidEnabled && combinedBackdrop != null) {
            Box(
                thumbPositionModifier
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(radius = 6.dp, color = Color.Black, alpha = if (isDark) 0.28f else 0.14f),
                    )
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            val tension = pullTension
                            vibrancy(brightness = 0f, contrast = 1.08f, saturation = 1.62f)
                            val r = lerp(0.6.dp.toPx(), 0f, progress)
                            if (r > 0.2f) blur(r, r)
                            lens(
                                refractionHeight = lerp(7.dp.toPx(), 11.dp.toPx(), progress) + 3.dp.toPx() * tension,
                                refractionAmount = lerp(12.dp.toPx(), 18.dp.toPx(), progress) + 4.dp.toPx() * tension,
                                depthEffect = true,
                                chromaticAberration = lerp(0.24f, 0.56f, progress) + 0.24f * tension,
                            )
                        },
                        highlight = {
                            thumbHighlight.value.copy(
                                alpha = (lerp(0.92f, 1.0f, dampedDragAnimation.pressProgress) + 0.08f * pullTension)
                                    .coerceAtMost(1f),
                            )
                        },
                        layerBlock = {
                            val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
                            val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
                            scaleX = dampedDragAnimation.scaleX * (1f + nx * 0.20f - ny * 0.08f)
                            scaleY = dampedDragAnimation.scaleY * (1f + ny * 0.18f - nx * 0.08f)
                            val v = dampedDragAnimation.velocity / 40f
                            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.24f, 0.24f)
                            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.24f, 0.24f)
                        },
                        onDrawSurface = {
                            val p = dampedDragAnimation.pressProgress
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = lerp(if (isDark) 0.30f else 0.44f, 0.10f, p)),
                                    0.44f to Color.White.copy(alpha = lerp(if (isDark) 0.07f else 0.12f, 0.02f, p)),
                                    1f to Color.White.copy(alpha = lerp(if (isDark) 0.18f else 0.28f, 0.06f, p)),
                                ),
                            )
                        },
                    )
                    .innerShadow(shape = pillShape) {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = lerp(2.5f, 5.5f, progress).dp,
                            color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f),
                            alpha = lerp(0.50f, 1f, progress),
                        )
                    }
                    .size(32.dp, 24.dp),
            )
        } else {
            Box(
                thumbPositionModifier
                    .graphicsLayer {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                    }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(radius = 4.dp, color = Color.Black, alpha = 0.18f),
                    )
                    .clip(pillShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.32f)),
                        ),
                    )
                    .border(0.9.dp, Color.White.copy(alpha = 0.85f), pillShape)
                    .size(32.dp, 24.dp),
            )
        }
    }
}

/**
 * Authentic Apple iOS 26 Discrete Notched Liquid Glass Slider (modeled after iOS 26 Text Size / Zoom detents):
 * - Neutral translucent glass rail with crisp etched vertical tick marks at every discrete stop (NO misleading progress bar!)
 * - Crystal Liquid Glass water-drop thumb glides fluidly with magnetic detent snapping & haptic ticks on every stop
 * - Tap any tick or stop label directly, or drag smoothly across detents (strictly level 0-deg horizontal pull at ends!)
 */
@Composable
fun LiquidSteppedSlider(
    steps: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    stepLabel: (Int) -> String = { "$it" },
    enabled: Boolean = true,
    darkSurface: Boolean = isInDarkTheme(),
    accentColor: Color = appleAccentColor(),
    compact: Boolean = false,
) {
    if (steps.size < 2) return
    val liquidEnabled = LocalEnableLiquidGlass.current && backdrop != null
    val trackBackdrop = rememberLayerBackdrop()
    val thumbShape = remember { ContinuousCapsule }
    val thumbHighlight = rememberGravityRotatedHighlight(IosClearSpecular, extraDegrees = 90f)
    val railHighlight = rememberGravityRotatedHighlight(IosLiquidSpecular, extraDegrees = -45f)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val lastIndex = steps.lastIndex
    val selectedIndex = steps.indexOf(selectedValue).coerceIn(0, lastIndex)
    val latestSteps by rememberUpdatedState(steps)
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnSelected by rememberUpdatedState(onValueSelected)
    val latestIsLtr by rememberUpdatedState(isLtr)

    val thumbWidthDp = if (compact) 32.dp else 42.dp
    val thumbHeightDp = if (compact) 20.dp else 26.dp
    val sliderBoxHeightDp = if (compact) 32.dp else 44.dp
    val trackPadH = if (compact) 10.dp else 13.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
    val edgeInsetPx = thumbWidthPx * 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderBoxHeightDp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(thumbWidthPx + 1f)
            val spanPx = (totalWidthPx - thumbWidthPx).coerceAtLeast(1f)
            val latestTotalWidthPx by rememberUpdatedState(totalWidthPx)
            val latestSpanPx by rememberUpdatedState(spanPx)

            val animationScope = rememberCoroutineScope()
            var isDragging by remember { mutableStateOf(false) }
            var currentStepIndex by remember { mutableIntStateOf(selectedIndex) }
            var wasAtBound by remember { mutableStateOf(false) }

            var targetRawPull by remember { mutableStateOf(Offset.Zero) }
            val maxPullXPx = with(density) { 14.dp.toPx() }
            val maxPullYPx = with(density) { 8.dp.toPx() }

            val targetRubber = remember(targetRawPull, maxPullXPx, maxPullYPx) {
                Offset(
                    x = asymptoticRubberBand(targetRawPull.x, maxPullXPx),
                    y = asymptoticRubberBand(targetRawPull.y, maxPullYPx),
                )
            }
            val rubberOffset by animateOffsetAsState(
                targetValue = targetRubber,
                animationSpec = if (isDragging) {
                    spring(dampingRatio = 0.56f, stiffness = 560f)
                } else {
                    spring(dampingRatio = 0.36f, stiffness = 320f)
                },
                label = "steppedElasticPull",
            )
            val pullTension by remember(maxPullXPx, maxPullYPx) {
                derivedStateOf {
                    val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
                    val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
                    hypot(nx, ny).coerceAtMost(1.25f)
                }
            }

            val railHeight by animateDpAsState(
                targetValue = if (compact) {
                    if (isDragging) 9.dp else 7.dp
                } else {
                    if (isDragging) 11.dp else 8.5.dp
                },
                animationSpec = spring(dampingRatio = 0.50f, stiffness = 400f),
                label = "steppedRailHeight",
            )

            fun rawStepAtX(x: Float): Float {
                val maxIdx = latestSteps.lastIndex.coerceAtLeast(1).toFloat()
                val rawRatio = ((x - edgeInsetPx) / latestSpanPx).coerceIn(0f, 1f)
                val logicalRatio = if (latestIsLtr) rawRatio else 1f - rawRatio
                return logicalRatio * maxIdx
            }

            fun applyDetentMagnet(rawStep: Float): Float {
                val maxIdx = latestSteps.lastIndex.coerceAtLeast(1)
                val nearest = rawStep.roundToInt().coerceIn(0, maxIdx)
                val delta = rawStep - nearest
                // Gentle magnetic attraction around each integer tick so detents feel crisp & tactile
                val eased = delta * (0.48f + 0.52f * (abs(delta) * 2f).coerceIn(0f, 1f))
                return (nearest + eased).coerceIn(0f, maxIdx.toFloat())
            }

            val dampedDragAnimation = remember(animationScope) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = latestSelectedIndex.toFloat(),
                    valueRange = 0f..lastIndex.toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 1.44f,
                    consumeDrag = true,
                    onDragStarted = { position ->
                        if (!latestEnabled) return@DampedDragAnimation
                        isDragging = true
                        wasAtBound = false
                        val raw = rawStepAtX(position.x)
                        val nearest = raw.roundToInt().coerceIn(0, latestSteps.lastIndex)
                        if (nearest != currentStepIndex) {
                            currentStepIndex = nearest
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnSelected(latestSteps[nearest])
                        }
                        updateValue(nearest.toFloat())
                    },
                    onDragStopped = {
                        if (!latestEnabled) return@DampedDragAnimation
                        isDragging = false
                        val committed = currentStepIndex.coerceIn(0, latestSteps.lastIndex)
                        animateToValue(committed.toFloat())
                        latestOnSelected(latestSteps[committed])
                        targetRawPull = Offset.Zero
                    },
                    onDragCancelled = {
                        isDragging = false
                        val restored = latestSelectedIndex.coerceIn(0, latestSteps.lastIndex)
                        currentStepIndex = restored
                        animateToValue(restored.toFloat())
                        targetRawPull = Offset.Zero
                    },
                    onDrag = { _, position, _ ->
                        if (!latestEnabled) return@DampedDragAnimation
                        val raw = rawStepAtX(position.x)
                        val nearest = raw.roundToInt().coerceIn(0, latestSteps.lastIndex)
                        if (nearest != currentStepIndex) {
                            currentStepIndex = nearest
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnSelected(latestSteps[nearest])
                        }
                        updateValue(applyDetentMagnet(raw))

                        val overX = when {
                            position.x < edgeInsetPx -> (position.x - edgeInsetPx) * 1.15f
                            position.x > latestTotalWidthPx - edgeInsetPx ->
                                (position.x - (latestTotalWidthPx - edgeInsetPx)) * 1.15f
                            else -> 0f
                        }
                        val overY = (position.y - with(density) { 22.dp.toPx() }) * 0.65f
                        val hitBound = overX != 0f
                        if (hitBound && !wasAtBound) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        wasAtBound = hitBound
                        targetRawPull = Offset(
                            x = overX.coerceIn(-280f, 280f),
                            y = overY.coerceIn(-180f, 180f),
                        )
                    },
                )
            }

            SideEffect {
                dampedDragAnimation.valueRange = 0f..lastIndex.toFloat()
            }

            LaunchedEffect(selectedIndex) {
                if (!isDragging) {
                    currentStepIndex = selectedIndex
                    dampedDragAnimation.animateToValue(selectedIndex.toFloat())
                }
            }

            val trackColor = if (darkSurface) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.085f)
            val tickColor = if (darkSurface) Color.White.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.22f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sliderBoxHeightDp)
                    .then(if (enabled) dampedDragAnimation.modifier else Modifier),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Neutral optical Liquid Glass notched track (ZERO misleading progress bar!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 18.dp else 22.dp)
                        .graphicsLayer {
                            val nx = if (maxPullXPx > 0f) (rubberOffset.x / maxPullXPx).fastCoerceIn(-1.1f, 1.1f) else 0f
                            val ny = if (maxPullYPx > 0f) (rubberOffset.y / maxPullYPx).fastCoerceIn(-1.1f, 1.1f) else 0f
                            // Strictly level 0-deg horizontal rubber stretch!
                            transformOrigin = TransformOrigin(
                                pivotFractionX = when {
                                    nx > 0.02f -> 0f
                                    nx < -0.02f -> 1f
                                    else -> 0.5f
                                },
                                pivotFractionY = 0.5f,
                            )
                            translationX = rubberOffset.x * 0.26f
                            translationY = rubberOffset.y * 0.24f
                            scaleX = 1f + abs(nx) * 0.06f
                            scaleY = 1f - abs(nx) * 0.10f + abs(ny) * 0.04f
                        }
                        .layerBackdrop(trackBackdrop),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Main glass track bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = trackPadH)
                            .height(railHeight)
                            .then(
                                if (liquidEnabled) {
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { ContinuousCapsule },
                                        effects = {
                                            vibrancy(brightness = 0f, contrast = 1.06f, saturation = 1.48f)
                                            blur(1.6.dp.toPx(), 1.6.dp.toPx())
                                            lens(
                                                refractionHeight = 3.5.dp.toPx(),
                                                refractionAmount = 6.dp.toPx(),
                                                depthEffect = true,
                                                chromaticAberration = 0.08f + 0.14f * pullTension,
                                            )
                                        },
                                        highlight = { railHighlight.value.copy(alpha = if (isDragging) 0.80f else 0.50f) },
                                        onDrawSurface = { drawRect(trackColor) },
                                    )
                                } else {
                                    Modifier
                                        .clip(ContinuousCapsule)
                                        .background(trackColor)
                                }
                            )
                            .innerShadow(shape = ContinuousCapsule) {
                                InnerShadow(
                                    radius = 2.5.dp,
                                    color = Color.Black.copy(alpha = if (darkSurface) 0.30f else 0.14f),
                                    alpha = 0.82f,
                                )
                            },
                    )

                    // Authentic Apple iOS 26 embedded circular step dots nested cleanly INSIDE the glass capsule rail!
                    steps.forEachIndexed { idx, _ ->
                        val fraction = idx.toFloat() / lastIndex.toFloat()
                        val isActiveDot = idx == currentStepIndex
                        val dotSizeDp = if (compact) {
                            if (isActiveDot) 4.2.dp else 3.4.dp
                        } else {
                            if (isActiveDot) 5.dp else 4.2.dp
                        }
                        val dotSizePx = with(density) { dotSizeDp.toPx() }
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val logicalFraction = if (isLtr) fraction else 1f - fraction
                                    val centerX = edgeInsetPx + logicalFraction * spanPx
                                    translationX = centerX - dotSizePx * 0.5f
                                }
                                .size(dotSizeDp)
                                .clip(CircleShape)
                                .background(
                                    if (isActiveDot) accentColor.copy(alpha = 0.92f)
                                    else tickColor,
                                ),
                        )
                    }
                }

                // Pure Crystal Water-Drop Liquid Glass Thumb snapping cleanly into each detent!
                val scaledTrackBackdrop = rememberTransformedBackdrop(trackBackdrop) { drawWrapped ->
                    val progress = dampedDragAnimation.pressProgress
                    val sx = lerp(0.90f, 1.0f, progress)
                    val sy = lerp(0.90f, 1.0f, progress)
                    scale(sx, sy) { drawWrapped() }
                }
                val combinedThumbBackdrop = backdrop?.let {
                    rememberCombinedBackdrop(it, scaledTrackBackdrop)
                }

                val thumbModifier = Modifier.graphicsLayer {
                    val p = dampedDragAnimation.progress.coerceIn(0f, 1f)
                    val logicalP = if (isLtr) p else 1f - p
                    translationX = logicalP * spanPx + rubberOffset.x * 0.92f
                    translationY = rubberOffset.y * 0.70f
                }

                if (liquidEnabled && combinedThumbBackdrop != null) {
                    Box(
                        thumbModifier
                            .dropShadow(
                                shape = thumbShape,
                                shadow = Shadow(
                                    radius = 9.dp,
                                    color = Color.Black,
                                    alpha = if (darkSurface) 0.34f else 0.14f,
                                ),
                            )
                            .drawBackdrop(
                                backdrop = combinedThumbBackdrop,
                                shape = { thumbShape },
                                effects = {
                                    val progress = dampedDragAnimation.pressProgress
                                    val tension = pullTension
                                    vibrancy(brightness = 0f, contrast = 1.08f, saturation = 1.65f)
                                    val r = lerp(0.7.dp.toPx(), 0f, progress)
                                    if (r > 0.25f) blur(r, r)
                                    lens(
                                        refractionHeight = lerp(9.dp.toPx(), 14.dp.toPx(), progress) + 3.5.dp.toPx() * tension,
                                        refractionAmount = lerp(15.dp.toPx(), 23.dp.toPx(), progress) + 6.dp.toPx() * tension,
                                        depthEffect = true,
                                        chromaticAberration = lerp(0.28f, 0.62f, progress) + 0.24f * tension,
                                    )
                                },
                                highlight = {
                                    thumbHighlight.value.copy(
                                        alpha = (lerp(0.92f, 1.0f, dampedDragAnimation.pressProgress) + 0.08f * pullTension)
                                            .coerceAtMost(1f),
                                    )
                                },
                                layerBlock = {
                                    val nx = if (maxPullXPx > 0f) abs(rubberOffset.x) / maxPullXPx else 0f
                                    val ny = if (maxPullYPx > 0f) abs(rubberOffset.y) / maxPullYPx else 0f
                                    scaleX = dampedDragAnimation.scaleX * (1f + nx * 0.16f - ny * 0.06f)
                                    scaleY = dampedDragAnimation.scaleY * (1f + ny * 0.14f - nx * 0.06f)
                                    val v = dampedDragAnimation.velocity / 10f
                                    scaleX /= 1f - (v * 0.55f).fastCoerceIn(-0.18f, 0.18f)
                                    scaleY *= 1f - (v * 0.20f).fastCoerceIn(-0.18f, 0.18f)
                                },
                                onDrawSurface = {
                                    val p = dampedDragAnimation.pressProgress
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to Color.White.copy(alpha = lerp(if (darkSurface) 0.24f else 0.36f, 0.08f, p)),
                                            0.42f to Color.White.copy(alpha = lerp(0.06f, 0.01f, p)),
                                            1f to Color.White.copy(alpha = lerp(if (darkSurface) 0.14f else 0.22f, 0.05f, p)),
                                        ),
                                    )
                                },
                            )
                            .innerShadow(shape = thumbShape) {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = lerp(3.2f, 6.5f, progress).dp,
                                    color = Color.Black.copy(alpha = if (darkSurface) 0.22f else 0.12f),
                                    alpha = lerp(0.55f, 1f, progress),
                                )
                            }
                            .size(thumbWidthDp, thumbHeightDp),
                    )
                } else {
                    val fallbackBorder = Color.White.copy(alpha = if (darkSurface) 0.65f else 0.88f)
                    Box(
                        thumbModifier
                            .graphicsLayer {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                            }
                            .dropShadow(
                                shape = thumbShape,
                                shadow = Shadow(radius = 5.dp, color = Color.Black, alpha = 0.20f),
                            )
                            .clip(thumbShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.56f), Color.White.copy(alpha = 0.28f)),
                                ),
                            )
                            .border(1.dp, fallbackBorder, thumbShape)
                            .size(thumbWidthDp, thumbHeightDp),
                    )
                }
            }
        }

        // Crisp clickable stop labels directly aligned under each tick detent (e.g., 3 列 · 4 列 · 5 列)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { idx, stepValue ->
                val isSelected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .width(thumbWidthDp)
                        .clip(ContinuousCapsule)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (stepValue != selectedValue) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onValueSelected(stepValue)
                            }
                        }
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stepLabel(stepValue),
                        color = if (isSelected) accentColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}
