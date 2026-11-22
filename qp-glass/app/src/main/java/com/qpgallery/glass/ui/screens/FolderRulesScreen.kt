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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.FolderEntry
import com.qpgallery.glass.data.FolderRuleKind
import com.qpgallery.glass.data.GalleryScanner
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.LiquidGlassIconButton
import com.qpgallery.glass.ui.liquid.LiquidGlassSurface
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRulesScreen(
    kind: FolderRuleKind,
    folders: Set<String>,
    showHidden: Boolean,
    backEnabled: Boolean,
    onAddFolders: (Collection<String>) -> Unit,
    onRemoveFolders: (Collection<String>) -> Unit,
    onBack: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        FolderPickerScreen(
            showHidden = showHidden,
            backEnabled = backEnabled,
            onSelectFolders = { paths ->
                picking = false
                onAddFolders(paths)
            },
            onBack = { picking = false },
        )
        return
    }

    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val haptic = LocalHapticFeedback.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val rulesBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val sorted = remember(folders) { folders.sorted() }
    var selectedPaths by remember(folders) { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedPaths.isNotEmpty()

    fun toggleSelect(path: String) {
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
    }

    fun selectAll() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedPaths = if (selectedPaths.size == sorted.size && sorted.isNotEmpty()) {
            emptySet()
        } else {
            sorted.toSet()
        }
    }

    fun invertSelection() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedPaths = sorted.filterNot { it in selectedPaths }.toSet()
    }

    BackHandler(enabled = backEnabled) {
        if (isSelectionMode) {
            selectedPaths = emptySet()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        if (sorted.isEmpty()) {
            EmptyGalleryState(
                title = stringResource(
                    if (kind == FolderRuleKind.INCLUDE) R.string.menu_included_folders
                    else R.string.menu_excluded_folders,
                ),
                subtitle = stringResource(
                    if (kind == FolderRuleKind.INCLUDE) R.string.folder_rules_empty_include
                    else R.string.folder_rules_empty_exclude,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(rulesBackdrop),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 74.dp,
                    bottom = if (isSelectionMode) 96.dp else 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .layerBackdrop(rulesBackdrop),
            ) {
                item(key = "hint") {
                    Text(
                        text = stringResource(R.string.folder_rules_hint),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                items(sorted, key = { it }) { path ->
                    val selected = path in selectedPaths
                    SelectableFolderCard(
                        title = File(path).name.ifEmpty { path },
                        subtitle = path,
                        isSelected = selected,
                        showSelectionRing = isSelectionMode,
                        isHidden = false,
                        accent = accent,
                        trailingAction = if (!isSelectionMode) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(ContinuousCapsule)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onRemoveFolders(listOf(path))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = GlassIcons.Trash,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = Color(0xFFFF453A),
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        onClick = {
                            if (isSelectionMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                toggleSelect(path)
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggleSelect(path)
                        },
                        onToggleCheck = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            toggleSelect(path)
                        },
                    )
                }
            }
        }

        // Top floating Liquid Glass bar (adapts smoothly to Multi-Select Mode!)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidGlassIconButton(
                onClick = {
                    if (isSelectionMode) selectedPaths = emptySet() else onBack()
                },
                backdrop = rulesBackdrop,
                contentDescription = stringResource(if (isSelectionMode) R.string.action_close else R.string.action_back),
                darkSurface = isDark,
            ) {
                Icon(
                    imageVector = if (isSelectionMode) GlassIcons.Close else GlassIcons.BackChevron,
                    contentDescription = stringResource(if (isSelectionMode) R.string.action_close else R.string.action_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp),
                )
            }

            LiquidGlassSurface(
                backdrop = rulesBackdrop,
                shape = ContinuousCapsule,
                darkSurface = isDark,
                elasticDrag = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (isSelectionMode) {
                        stringResource(R.string.selected_count, selectedPaths.size)
                    } else {
                        stringResource(
                            if (kind == FolderRuleKind.INCLUDE) R.string.menu_included_folders
                            else R.string.menu_excluded_folders,
                        )
                    },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (isSelectionMode) {
                // Grouped Select All + Invert Selection Liquid Glass capsule (2D elastic pull)
                LiquidGlassSurface(
                    backdrop = rulesBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        TopPillIcon(
                            icon = GlassIcons.SelectAll,
                            description = stringResource(R.string.action_select_all),
                            tint = if (selectedPaths.size == sorted.size && sorted.isNotEmpty()) accent else MiuixTheme.colorScheme.onSurface,
                            onClick = ::selectAll,
                        )
                        TopPillIcon(
                            icon = GlassIcons.InvertSelect,
                            description = stringResource(R.string.action_invert_selection),
                            tint = MiuixTheme.colorScheme.onSurface,
                            onClick = ::invertSelection,
                        )
                    }
                }
            } else {
                if (sorted.isNotEmpty()) {
                    LiquidGlassIconButton(
                        onClick = ::selectAll,
                        backdrop = rulesBackdrop,
                        contentDescription = stringResource(R.string.action_select_all),
                        darkSurface = isDark,
                    ) {
                        Icon(
                            imageVector = GlassIcons.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                LiquidGlassIconButton(
                    onClick = { picking = true },
                    backdrop = rulesBackdrop,
                    contentDescription = stringResource(R.string.action_add_folder),
                    darkSurface = isDark,
                ) {
                    Icon(
                        imageVector = GlassIcons.Plus,
                        contentDescription = stringResource(R.string.action_add_folder),
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        // Bottom floating batch Remove capsule when items are selected
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            LiquidGlassSurface(
                backdrop = rulesBackdrop,
                shape = ContinuousCapsule,
                tintColor = Color(0xFFFF453A).copy(alpha = 0.78f),
                blurRadius = 4.dp,
                refractionHeight = 16.dp,
                refractionAmount = 22.dp,
                chromaticAberration = 0.20f,
                highlightAlpha = 0.92f,
                darkSurface = true,
                elasticDrag = true,
                modifier = Modifier
                    .height(48.dp)
                    .clip(ContinuousCapsule)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val batch = selectedPaths.toList()
                        selectedPaths = emptySet()
                        onRemoveFolders(batch)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = GlassIcons.Trash,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_remove_selected, selectedPaths.size),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPickerScreen(
    showHidden: Boolean,
    backEnabled: Boolean,
    onSelectFolders: (Collection<String>) -> Unit,
    onBack: () -> Unit,
) {
    val root = remember { GalleryScanner.storageRoot() }
    var currentDir by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<FolderEntry>>(emptyList()) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }

    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val haptic = LocalHapticFeedback.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val pickerBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    LaunchedEffect(currentDir, showHidden) {
        entries = withContext(Dispatchers.IO) {
            GalleryScanner.listSubfolders(currentDir, showHidden)
        }
    }

    val visiblePaths = remember(entries) { entries.map { it.path }.toSet() }
    val allVisibleSelected = visiblePaths.isNotEmpty() && visiblePaths.all { it in selectedPaths }
    val isSelectionMode = selectedPaths.isNotEmpty()
    val canGoUp = currentDir.absolutePath != root.absolutePath && currentDir.parentFile != null

    fun toggleSelect(path: String) {
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
    }

    fun selectAllVisible() {
        if (visiblePaths.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedPaths = if (allVisibleSelected) {
            selectedPaths - visiblePaths
        } else {
            selectedPaths + visiblePaths
        }
    }

    fun invertVisibleSelection() {
        if (visiblePaths.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val invertedVisible = visiblePaths.filterNot { it in selectedPaths }.toSet()
        selectedPaths = (selectedPaths - visiblePaths) + invertedVisible
    }

    BackHandler(enabled = backEnabled) {
        when {
            isSelectionMode -> selectedPaths = emptySet()
            canGoUp -> currentDir = currentDir.parentFile ?: root
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 74.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .layerBackdrop(pickerBackdrop),
        ) {
            item(key = "picker_hint") {
                Text(
                    text = stringResource(R.string.folder_rules_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (canGoUp) {
                item(key = "..") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentDir = currentDir.parentFile ?: root
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = GlassIcons.ParentFolder,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "..  (${stringResource(R.string.folder_parent)})",
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.folder_picker_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                items(entries, key = { it.path }) { entry ->
                    val isSelected = entry.path in selectedPaths
                    SelectableFolderCard(
                        title = entry.name,
                        subtitle = "${stringResource(R.string.folder_subfolders_count, entry.subfolderCount)} · ${
                            stringResource(R.string.folder_items_count, entry.imageCount)
                        }",
                        isSelected = isSelected,
                        showSelectionRing = true,
                        isHidden = entry.isHidden,
                        accent = accent,
                        trailingAction = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isSelectionMode) {
                                toggleSelect(entry.path)
                            } else {
                                currentDir = File(entry.path)
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggleSelect(entry.path)
                        },
                        onToggleCheck = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            toggleSelect(entry.path)
                        },
                    )
                }
            }
        }

        // Top floating Liquid Glass bar showing current path (or selection count) + Select All / Invert!
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidGlassIconButton(
                onClick = {
                    if (isSelectionMode) selectedPaths = emptySet() else onBack()
                },
                backdrop = pickerBackdrop,
                contentDescription = stringResource(if (isSelectionMode) R.string.action_close else R.string.action_back),
                darkSurface = isDark,
            ) {
                Icon(
                    imageVector = if (isSelectionMode) GlassIcons.Close else GlassIcons.BackChevron,
                    contentDescription = stringResource(if (isSelectionMode) R.string.action_close else R.string.action_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp),
                )
            }

            LiquidGlassSurface(
                backdrop = pickerBackdrop,
                shape = ContinuousCapsule,
                darkSurface = isDark,
                elasticDrag = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (isSelectionMode) {
                        stringResource(R.string.selected_count, selectedPaths.size)
                    } else {
                        currentDir.absolutePath
                    },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = if (isSelectionMode) 15.sp else 13.sp,
                    fontWeight = if (isSelectionMode) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (entries.isNotEmpty()) {
                LiquidGlassSurface(
                    backdrop = pickerBackdrop,
                    shape = ContinuousCapsule,
                    darkSurface = isDark,
                    elasticDrag = true,
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        TopPillIcon(
                            icon = GlassIcons.SelectAll,
                            description = stringResource(R.string.action_select_all),
                            tint = if (allVisibleSelected) accent else MiuixTheme.colorScheme.onSurface,
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
            }
        }

        // Bottom floating Liquid Glass confirm capsule (2D elastic pull + batch add)
        LiquidGlassSurface(
            backdrop = pickerBackdrop,
            shape = ContinuousCapsule,
            tintColor = accent.copy(alpha = 0.78f),
            blurRadius = 4.dp,
            refractionHeight = 16.dp,
            refractionAmount = 22.dp,
            chromaticAberration = 0.20f,
            highlightAlpha = 0.92f,
            darkSurface = true,
            elasticDrag = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .height(48.dp)
                .clip(ContinuousCapsule)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (selectedPaths.isNotEmpty()) {
                        onSelectFolders(selectedPaths.toList())
                    } else {
                        onSelectFolders(listOf(currentDir.absolutePath))
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = GlassIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = if (selectedPaths.isNotEmpty()) {
                        stringResource(R.string.action_add_selected_folders, selectedPaths.size)
                    } else {
                        stringResource(R.string.action_select_current)
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableFolderCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    showSelectionRing: Boolean,
    isHidden: Boolean,
    accent: Color,
    trailingAction: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCheck: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 450f),
        label = "folderCardScale",
    )
    val cardShape = remember { ContinuousRoundedRectangle(18.dp) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, accent, cardShape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = GlassIcons.Folder,
                contentDescription = null,
                tint = when {
                    isSelected -> accent
                    isHidden -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> accent
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (trailingAction != null) {
                trailingAction()
            } else if (showSelectionRing) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(ContinuousCapsule)
                        .background(if (isSelected) accent else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) Color.Transparent else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
                            shape = ContinuousCapsule,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleCheck,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = GlassIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
