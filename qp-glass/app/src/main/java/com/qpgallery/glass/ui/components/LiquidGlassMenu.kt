package com.qpgallery.glass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.liquid.LiquidSteppedSlider
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GlassMenuSliderSpec(
    val steps: List<Int>,
    val selectedValue: Int,
    val valueSummary: String,
    val stepLabel: (Int) -> String,
    val onValueSelected: (Int) -> Unit,
)

data class GlassMenuItem(
    val title: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val dividerBefore: Boolean = false,
    val sliderSpec: GlassMenuSliderSpec? = null,
    val onClick: () -> Unit = {},
)

/**
 * Coordinates classic QuickPic / iOS 26 "Press button -> Slide straight down into menu -> Release"
 * selection without conflicting with horizontal elastic Liquid Glass pill dragging!
 */
@Stable
class GlassMenuPullDownState {
    var isPulling by mutableStateOf(false)
        internal set
    var hoveredIndex by mutableStateOf<Int?>(null)
        internal set

    internal val clickableBounds = HashMap<Int, Rect>()
    internal val clickableActions = HashMap<Int, () -> Unit>()

    fun beginPull() {
        isPulling = true
        hoveredIndex = null
    }

    fun updatePointer(rootPos: Offset, onHaptic: () -> Unit) {
        if (!isPulling) return

        // Only match standard single-click menu items (never trigger inline sliders during pull-down!)
        var hitIdx: Int? = null
        for ((idx, rect) in clickableBounds) {
            if (rect.contains(rootPos)) {
                hitIdx = idx
                break
            }
        }
        if (hitIdx != hoveredIndex) {
            hoveredIndex = hitIdx
            if (hitIdx != null) {
                onHaptic()
            }
        }
    }

    fun finishPull(finalRootPos: Offset?, onDismiss: () -> Unit, onHaptic: () -> Unit) {
        if (finalRootPos != null) {
            updatePointer(finalRootPos) {}
        }
        val target = hoveredIndex
        val action = if (target != null) clickableActions[target] else null
        isPulling = false
        hoveredIndex = null
        onDismiss()
        if (action != null) {
            onHaptic()
            action()
        }
    }

    fun cancelPull() {
        isPulling = false
        hoveredIndex = null
    }

    internal fun clearBounds() {
        clickableBounds.clear()
        clickableActions.clear()
    }
}

@Composable
fun rememberGlassMenuPullDownState(): GlassMenuPullDownState = remember { GlassMenuPullDownState() }

/**
 * Floating iOS 26-style Liquid Glass popup menu anchored cleanly to top-end,
 * supporting both tap-to-open (with interactive inline stepped sliders) and
 * continuous press-and-slide-down selection directly from the top bar button!
 */
@Composable
fun LiquidGlassDropdownOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop?,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    pullDownState: GlassMenuPullDownState? = null,
) {
    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val haptic = LocalHapticFeedback.current
    val menuShape = remember { ContinuousRoundedRectangle(22.dp) }
    val hoverRowShape = remember { ContinuousRoundedRectangle(14.dp) }
    val menuTint = if (isDark) {
        Color(0xFF0E1118).copy(alpha = 0.36f)
    } else {
        Color.White.copy(alpha = 0.48f)
    }

    LaunchedEffect(visible) {
        if (!visible) {
            pullDownState?.clearBounds()
            pullDownState?.cancelPull()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 500f)),
        exit = fadeOut(spring(stiffness = 550f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.TopEnd,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(0.92f, 0.05f),
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(0.92f, 0.05f),
                    animationSpec = spring(stiffness = 520f),
                ) + fadeOut(),
            ) {
                LiquidGlassSurface(
                    backdrop = backdrop,
                    shape = menuShape,
                    tintColor = menuTint,
                    blurRadius = 6.5.dp,
                    refractionHeight = 16.dp,
                    refractionAmount = 22.dp,
                    chromaticAberration = 0.18f,
                    highlightAlpha = 0.90f,
                    shadowRadius = 20.dp,
                    darkSurface = isDark,
                    modifier = modifier
                        .widthIn(min = 216.dp, max = 264.dp)
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
                            .padding(vertical = 6.dp),
                    ) {
                        items.forEachIndexed { index, item ->
                            if (item.dividerBefore && index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                )
                            }
                            val isHovered = pullDownState?.isPulling == true && pullDownState.hoveredIndex == index
                            val hoverBg = if (isHovered) {
                                if (isDark) Color.White.copy(alpha = 0.14f) else accent.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }

                            val sliderSpec = item.sliderSpec
                            if (sliderSpec != null) {
                                // Inline compact Apple iOS 26 embedded-dot stepped slider inside dropdown menu
                                // (Strictly ignored during pull-down gesture so sliding past it never alters columns!)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            tint = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(19.dp),
                                        )
                                        Text(
                                            text = item.title,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = sliderSpec.valueSummary,
                                            color = accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                        )
                                    }
                                    LiquidSteppedSlider(
                                        steps = sliderSpec.steps,
                                        selectedValue = sliderSpec.selectedValue,
                                        onValueSelected = sliderSpec.onValueSelected,
                                        stepLabel = sliderSpec.stepLabel,
                                        backdrop = backdrop,
                                        darkSurface = isDark,
                                        accentColor = accent,
                                        compact = true,
                                    )
                                }
                            } else {
                                pullDownState?.clickableActions?.put(index, item.onClick)
                                val textColor = when {
                                    item.destructive -> Color(0xFFFF453A)
                                    item.selected || isHovered -> accent
                                    else -> MiuixTheme.colorScheme.onSurface
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .clip(hoverRowShape)
                                        .background(hoverBg)
                                        .onGloballyPositioned { coords ->
                                            pullDownState?.clickableBounds?.put(index, coords.boundsInRoot())
                                        }
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDismiss()
                                            item.onClick()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = textColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = item.title,
                                        color = textColor,
                                        fontSize = 15.sp,
                                        fontWeight = if (item.selected || isHovered) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (item.selected) {
                                        Icon(
                                            imageVector = GlassIcons.Check,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(18.dp),
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
}

/**
 * Compact floating Liquid Glass search capsule that focuses immediately and never blocks the screen.
 */
@Composable
fun LiquidGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val textColor = MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = ContinuousCapsule,
        tintColor = if (isDark) Color(0xFF0D1017).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.38f),
        blurRadius = 4.5.dp,
        refractionHeight = 14.dp,
        refractionAmount = 18.dp,
        chromaticAberration = 0.16f,
        highlightAlpha = 0.88f,
        darkSurface = isDark,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = GlassIcons.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = hintColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = hintColor,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = GlassIcons.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = textColor.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (query.isNotEmpty()) {
                            onQueryChange("")
                        } else {
                            keyboard?.hide()
                            onClose()
                        }
                    },
            )
        }
    }
}
