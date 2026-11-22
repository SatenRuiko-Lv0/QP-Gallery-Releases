package com.qpgallery.glass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.ImageDimensions
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.MediaItem
import com.qpgallery.glass.data.formatBytes
import com.qpgallery.glass.data.formatTimestamp
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
fun FilePreviewOverlay(
    item: MediaItem?,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onShare: (MediaItem) -> Unit,
    onOpenWith: (MediaItem) -> Unit,
    onCopyPath: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    darkSurface: Boolean = isInDarkTheme(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var cachedItem by remember { mutableStateOf(item) }
    if (item != null) cachedItem = item
    val shown = cachedItem
    var dimensions by remember(shown?.path) {
        mutableStateOf<ImageDimensions?>(shown?.let { ImageLoader.getCachedDimensions(it.path) })
    }
    var confirmDelete by remember(shown?.path, item != null) { mutableStateOf(false) }

    LaunchedEffect(shown?.path) {
        if (shown != null && dimensions == null) {
            dimensions = ImageLoader.probeDimensions(context.contentResolver, shown)
        }
    }

    if (item != null) {
        BackHandler {
            if (confirmDelete) {
                confirmDelete = false
            } else {
                onDismiss()
            }
        }
    }

    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn(spring(stiffness = 460f)),
        exit = fadeOut(spring(stiffness = 520f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        confirmDelete = false
                        onDismiss()
                    },
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = item != null,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 400f),
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = spring(stiffness = 520f),
                ) + fadeOut(),
            ) {
                if (shown != null) {
                    val sheetShape = remember { ContinuousRoundedRectangle(28.dp) }
                    val fg = if (darkSurface) Color.White else Color(0xFF14171F)
                    val subFg = fg.copy(alpha = 0.65f)
                    val sheetTint = if (darkSurface) {
                        Color(0xFF0D1017).copy(alpha = 0.42f)
                    } else {
                        Color.White.copy(alpha = 0.54f)
                    }

                    LiquidGlassSurface(
                        backdrop = backdrop,
                        shape = sheetShape,
                        tintColor = sheetTint,
                        blurRadius = 8.dp,
                        refractionHeight = 18.dp,
                        refractionAmount = 24.dp,
                        chromaticAberration = 0.18f,
                        highlightAlpha = 0.90f,
                        shadowRadius = 22.dp,
                        darkSurface = darkSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                .padding(18.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncThumbnail(
                                    item = shown,
                                    maxEdgePx = 320,
                                    shape = ContinuousRoundedRectangle(14.dp),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shown.name,
                                        color = fg,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val meta = buildString {
                                        dimensions?.let { append("${it.width} × ${it.height} · ") }
                                        append(formatBytes(shown.sizeBytes))
                                        if (shown.extension.isNotEmpty()) append(" · ${shown.extension}")
                                    }
                                    Text(
                                        text = meta,
                                        color = subFg,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(ContinuousCapsule)
                                        .background(fg.copy(alpha = 0.10f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            confirmDelete = false
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = GlassIcons.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = fg,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = fg.copy(alpha = 0.10f))
                            Spacer(Modifier.height(12.dp))

                            DetailRow(stringResource(R.string.details_path), shown.path, fg, subFg)
                            Spacer(Modifier.height(8.dp))
                            DetailRow(
                                stringResource(R.string.details_modified),
                                formatTimestamp(shown.lastModifiedMs),
                                fg,
                                subFg,
                            )
                            dimensions?.let { d ->
                                Spacer(Modifier.height(8.dp))
                                DetailRow(
                                    stringResource(R.string.details_dimensions),
                                    "${d.width} × ${d.height} px",
                                    fg,
                                    subFg,
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            if (confirmDelete) {
                                // Unmistakable deterministic two-step delete confirmation banner
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ContinuousRoundedRectangle(18.dp))
                                        .background(Color(0xFFFF453A).copy(alpha = 0.14f))
                                        .border(
                                            0.8.dp,
                                            Color(0xFFFF453A).copy(alpha = 0.42f),
                                            ContinuousRoundedRectangle(18.dp),
                                        )
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.delete_confirm_title),
                                        color = Color(0xFFFF453A),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.delete_confirm_message, shown.name),
                                        color = fg.copy(alpha = 0.88f),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        SheetActionButton(
                                            label = stringResource(R.string.action_cancel),
                                            icon = GlassIcons.Close,
                                            fg = fg,
                                            bg = fg.copy(alpha = 0.12f),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                confirmDelete = false
                                            },
                                        )
                                        SheetActionButton(
                                            label = stringResource(R.string.action_confirm_delete),
                                            icon = GlassIcons.Trash,
                                            fg = Color.White,
                                            bg = Color(0xFFFF453A),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                confirmDelete = false
                                                onDismiss()
                                                onDelete(shown)
                                            },
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SheetActionButton(
                                        label = stringResource(R.string.action_share),
                                        icon = GlassIcons.Share,
                                        fg = fg,
                                        bg = fg.copy(alpha = 0.10f),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDismiss()
                                            onShare(shown)
                                        },
                                    )
                                    SheetActionButton(
                                        label = stringResource(R.string.action_open_with),
                                        icon = GlassIcons.OpenIn,
                                        fg = fg,
                                        bg = fg.copy(alpha = 0.10f),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDismiss()
                                            onOpenWith(shown)
                                        },
                                    )
                                    SheetActionButton(
                                        label = stringResource(R.string.action_copy_path),
                                        icon = GlassIcons.Copy,
                                        fg = fg,
                                        bg = fg.copy(alpha = 0.10f),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCopyPath(shown)
                                        },
                                    )
                                    SheetActionButton(
                                        label = stringResource(R.string.action_delete),
                                        icon = GlassIcons.Trash,
                                        fg = Color(0xFFFF453A),
                                        bg = Color(0xFFFF453A).copy(alpha = 0.14f),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            confirmDelete = true
                                        },
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

@Composable
private fun DetailRow(label: String, value: String, fg: Color, subFg: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = subFg,
            fontSize = 12.sp,
            modifier = Modifier.width(68.dp),
        )
        Text(
            text = value,
            color = fg.copy(alpha = 0.92f),
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SheetActionButton(
    label: String,
    icon: ImageVector,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 480f),
        label = "sheetBtnScale",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ContinuousRoundedRectangle(16.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
