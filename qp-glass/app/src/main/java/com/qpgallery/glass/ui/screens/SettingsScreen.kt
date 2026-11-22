package com.qpgallery.glass.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.R
import com.qpgallery.glass.data.AppIconMode
import com.qpgallery.glass.data.FolderRuleKind
import com.qpgallery.glass.data.UserSettings
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.ColorBackdrop
import com.qpgallery.glass.ui.liquid.LiquidSteppedSlider
import com.qpgallery.glass.ui.liquid.LiquidToggle
import com.qpgallery.glass.ui.theme.ThemeMode
import com.qpgallery.glass.ui.theme.appleAccentColor
import com.qpgallery.glass.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: UserSettings,
    backdrop: Backdrop?,
    bottomBarClearance: Dp,
    onUpdateSettings: (rescanNeeded: Boolean, block: (UserSettings) -> UserSettings) -> Unit,
    onSelectAppIconMode: (AppIconMode) -> Unit,
    onOpenFolderRules: (FolderRuleKind) -> Unit,
) {
    val isDark = isInDarkTheme()
    val accent = appleAccentColor()
    val cardColor = MiuixTheme.colorScheme.surfaceContainer
    val controlBackdrop = remember(cardColor) { ColorBackdrop(cardColor) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 12.dp,
            bottom = bottomBarClearance + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                QpThemePreviewPhone(
                    isDark = isDark,
                    accent = accent,
                    floatingBar = settings.enableFloatingBottomBar,
                    liquidGlass = settings.enableLiquidGlass,
                )
            }
        }

        item {
            val themeTabs = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            )
            TabRow(
                tabs = themeTabs,
                selectedTabIndex = settings.themeMode.id.coerceIn(0, 2),
                onTabSelected = { idx ->
                    onUpdateSettings(false) { it.copy(themeMode = ThemeMode.fromId(idx)) }
                },
            )
        }

        // Apple iOS 26 Liquid Glass App Icon Switcher (System Day/Night · Light Glass · Dark Glass)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_app_icon),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_app_icon_desc),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppIconChoiceTile(
                            mode = AppIconMode.AUTO,
                            label = stringResource(R.string.icon_mode_auto),
                            selected = settings.appIconMode == AppIconMode.AUTO,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectAppIconMode(AppIconMode.AUTO) },
                        )
                        AppIconChoiceTile(
                            mode = AppIconMode.LIGHT,
                            label = stringResource(R.string.icon_mode_light),
                            selected = settings.appIconMode == AppIconMode.LIGHT,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectAppIconMode(AppIconMode.LIGHT) },
                        )
                        AppIconChoiceTile(
                            mode = AppIconMode.DARK,
                            label = stringResource(R.string.icon_mode_dark),
                            selected = settings.appIconMode == AppIconMode.DARK,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectAppIconMode(AppIconMode.DARK) },
                        )
                    }
                }
            }
        }

        // Monet color card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LiquidSwitchRow(
                    icon = GlassIcons.Sparkles,
                    title = stringResource(R.string.settings_monet),
                    summary = null,
                    checked = settings.useMonet,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(useMonet = v) } },
                )
            }
        }

        // Appearance & Liquid Glass switches
        item {
            SectionHeader(stringResource(R.string.settings_section_theme))
            Card(modifier = Modifier.fillMaxWidth()) {
                LiquidSwitchRow(
                    icon = GlassIcons.BlurLayers,
                    title = stringResource(R.string.settings_blur),
                    summary = stringResource(R.string.settings_blur_desc),
                    checked = settings.enableBlur,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableBlur = v) } },
                )
                SettingsDivider()
                LiquidSwitchRow(
                    icon = GlassIcons.FloatingDock,
                    title = stringResource(R.string.settings_floating_bar),
                    summary = stringResource(R.string.settings_floating_bar_desc),
                    checked = settings.enableFloatingBottomBar,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableFloatingBottomBar = v) } },
                )
                SettingsDivider()
                LiquidSwitchRow(
                    icon = GlassIcons.LiquidDrop,
                    title = stringResource(R.string.settings_liquid_glass),
                    summary = stringResource(R.string.settings_liquid_glass_desc),
                    checked = settings.enableLiquidGlass,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableLiquidGlass = v) } },
                )
            }
        }

        // Viewing & Reader card
        item {
            SectionHeader(stringResource(R.string.settings_section_view))
            Card(modifier = Modifier.fillMaxWidth()) {
                LiquidSwitchRow(
                    icon = GlassIcons.Brightness,
                    title = stringResource(R.string.settings_max_brightness),
                    summary = stringResource(R.string.settings_max_brightness_desc),
                    checked = settings.maxBrightness,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(maxBrightness = v) } },
                )
                SettingsDivider()
                LiquidSwitchRow(
                    icon = GlassIcons.History,
                    title = stringResource(R.string.settings_open_last_album),
                    summary = stringResource(R.string.settings_open_last_album_desc),
                    checked = settings.openLastAlbum,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(openLastAlbum = v) } },
                )
                SettingsDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = GlassIcons.Albums,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.settings_album_grid_columns),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.settings_grid_columns_desc, settings.albumGridColumns),
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val aTick2 = stringResource(R.string.settings_grid_columns_tick, 2)
                    val aTick3 = stringResource(R.string.settings_grid_columns_tick, 3)
                    val aTick4 = stringResource(R.string.settings_grid_columns_tick, 4)
                    LiquidSteppedSlider(
                        steps = listOf(2, 3, 4),
                        selectedValue = settings.albumGridColumns.coerceIn(2, 4),
                        onValueSelected = { cols ->
                            if (cols != settings.albumGridColumns) {
                                onUpdateSettings(false) { it.copy(albumGridColumns = cols) }
                            }
                        },
                        stepLabel = { col ->
                            when (col) {
                                2 -> aTick2
                                3 -> aTick3
                                else -> aTick4
                            }
                        },
                        backdrop = controlBackdrop,
                        darkSurface = isDark,
                        accentColor = accent,
                    )
                }
                SettingsDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = GlassIcons.Grid,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.settings_grid_columns),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.settings_grid_columns_desc, settings.gridColumns),
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val tick3 = stringResource(R.string.settings_grid_columns_tick, 3)
                    val tick4 = stringResource(R.string.settings_grid_columns_tick, 4)
                    val tick5 = stringResource(R.string.settings_grid_columns_tick, 5)
                    LiquidSteppedSlider(
                        steps = listOf(3, 4, 5),
                        selectedValue = settings.gridColumns.coerceIn(3, 5),
                        onValueSelected = { cols ->
                            if (cols != settings.gridColumns) {
                                onUpdateSettings(false) { it.copy(gridColumns = cols) }
                            }
                        },
                        stepLabel = { col ->
                            when (col) {
                                3 -> tick3
                                4 -> tick4
                                else -> tick5
                            }
                        },
                        backdrop = controlBackdrop,
                        darkSurface = isDark,
                        accentColor = accent,
                    )
                }
            }
        }

        // Folders & Hidden Items card
        item {
            SectionHeader(stringResource(R.string.settings_section_folders))
            Card(modifier = Modifier.fillMaxWidth()) {
                LiquidSwitchRow(
                    icon = GlassIcons.Eye,
                    title = stringResource(R.string.settings_show_hidden),
                    summary = stringResource(R.string.settings_show_hidden_desc),
                    checked = settings.showHidden,
                    accent = accent,
                    backdrop = controlBackdrop,
                    onCheckedChange = { v -> onUpdateSettings(true) { it.copy(showHidden = v) } },
                )
                SettingsDivider()
                SettingsArrowRow(
                    icon = GlassIcons.FolderPlus,
                    title = stringResource(R.string.menu_included_folders),
                    summary = if (settings.includedFolders.isEmpty()) {
                        stringResource(R.string.settings_included_desc_empty)
                    } else {
                        stringResource(R.string.settings_included_desc_count, settings.includedFolders.size)
                    },
                    onClick = { onOpenFolderRules(FolderRuleKind.INCLUDE) },
                )
                SettingsDivider()
                SettingsArrowRow(
                    icon = GlassIcons.FolderMinus,
                    title = stringResource(R.string.menu_excluded_folders),
                    summary = if (settings.excludedFolders.isEmpty()) {
                        stringResource(R.string.settings_excluded_desc_empty)
                    } else {
                        stringResource(R.string.settings_excluded_desc_count, settings.excludedFolders.size)
                    },
                    onClick = { onOpenFolderRules(FolderRuleKind.EXCLUDE) },
                )
            }
        }
    }
}

@Composable
private fun AppIconChoiceTile(
    mode: AppIconMode,
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 460f),
        label = "iconChoiceScale",
    )
    val tileShape = remember { ContinuousRoundedRectangle(16.dp) }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(tileShape)
            .background(
                if (selected) accent.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            )
            .border(
                width = if (selected) 1.6.dp else 0.7.dp,
                color = if (selected) accent else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                shape = tileShape,
            )
            .clickable(
                interactionSource = source,
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val previewRes = when (mode) {
            AppIconMode.AUTO -> R.drawable.ic_icon_preview_auto
            AppIconMode.LIGHT -> R.drawable.ic_icon_preview_light
            AppIconMode.DARK -> R.drawable.ic_icon_preview_dark
        }
        Image(
            painter = painterResource(id = previewRes),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(54.dp),
        )
        Text(
            text = label,
            color = if (selected) accent else MiuixTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
private fun LiquidSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    accent: Color,
    backdrop: Backdrop?,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            backdrop = backdrop,
            accentColor = accent,
        )
    }
}

@Composable
private fun SettingsArrowRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = GlassIcons.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QpThemePreviewPhone(
    isDark: Boolean,
    accent: Color,
    floatingBar: Boolean,
    liquidGlass: Boolean,
) {
    val frameShape = remember { ContinuousRoundedRectangle(24.dp) }
    val cardShape = remember { ContinuousRoundedRectangle(10.dp) }
    val bg = if (isDark) Color(0xFF0A0C10) else Color(0xFFF3F5FA)
    val tileA = if (isDark) Color(0xFF1D3B5E) else Color(0xFFBFD7F5)
    val tileB = if (isDark) Color(0xFF3E2D4C) else Color(0xFFDCC9EE)
    val tileC = if (isDark) Color(0xFF1F3B36) else Color(0xFFC0E4DA)
    val tileD = if (isDark) Color(0xFF473322) else Color(0xFFF3D5B7)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f)

    Box(
        modifier = Modifier
            .size(width = 128.dp, height = 196.dp)
            .clip(frameShape)
            .background(bg)
            .border(1.5.dp, borderColor, frameShape)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "QP Gallery",
                color = if (isDark) Color.White else Color(0xFF141820),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.weight(1f).height(46.dp).clip(cardShape).background(tileA))
                Box(Modifier.weight(1f).height(46.dp).clip(cardShape).background(tileB))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.weight(1f).height(46.dp).clip(cardShape).background(tileC))
                Box(Modifier.weight(1f).height(46.dp).clip(cardShape).background(tileD))
            }
        }

        val barShape = if (floatingBar) ContinuousCapsule else ContinuousRoundedRectangle(8.dp)
        val barBg = if (liquidGlass) {
            if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.72f)
        } else {
            if (isDark) Color(0xFF1C2028) else Color.White
        }
        val barBorder = if (liquidGlass) {
            Color.White.copy(alpha = if (isDark) 0.42f else 0.85f)
        } else {
            borderColor
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (floatingBar) 12.dp else 0.dp)
                .height(22.dp)
                .clip(barShape)
                .background(barBg)
                .border(0.8.dp, barBorder, barShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(Modifier.size(22.dp, 10.dp).clip(ContinuousCapsule).background(accent))
            Box(Modifier.size(14.dp, 8.dp).clip(ContinuousCapsule).background(borderColor))
        }
    }
}
