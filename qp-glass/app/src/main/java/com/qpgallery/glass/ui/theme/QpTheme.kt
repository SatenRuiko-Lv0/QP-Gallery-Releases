package com.qpgallery.glass.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Apple Human Interface Guidelines standard iOS System Blue. */
val AppleSystemBlueLight = Color(0xFF007AFF)
val AppleSystemBlueDark = Color(0xFF0A84FF)

enum class ThemeMode(val id: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromId(id: Int): ThemeMode = entries.find { it.id == id } ?: SYSTEM
    }
}

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalEnableBlur = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { true }
val LocalEnableLiquidGlass = staticCompositionLocalOf { true }
val LocalUseMonet = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = when (LocalThemeMode.current) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
@ReadOnlyComposable
fun appleAccentColor(): Color {
    return if (LocalUseMonet.current) {
        MiuixTheme.colorScheme.primary
    } else {
        if (isInDarkTheme()) AppleSystemBlueDark else AppleSystemBlueLight
    }
}

@Composable
fun QpTheme(
    themeMode: ThemeMode,
    useMonet: Boolean,
    enableBlur: Boolean,
    enableFloatingBottomBar: Boolean,
    enableLiquidGlass: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val defaultAppleBlue = if (darkTheme) AppleSystemBlueDark else AppleSystemBlueLight
    val resolvedKeyColor: Color = if (useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context).primary
        else dynamicLightColorScheme(context).primary
    } else {
        defaultAppleBlue
    }

    val colorSchemeMode = when {
        useMonet && themeMode == ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
        useMonet && themeMode == ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        useMonet && themeMode == ThemeMode.DARK -> ColorSchemeMode.MonetDark
        themeMode == ThemeMode.LIGHT -> ColorSchemeMode.Light
        themeMode == ThemeMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val controller = ThemeController(
        colorSchemeMode = colorSchemeMode,
        keyColor = resolvedKeyColor,
        isDark = darkTheme,
    )

    val glassReady = enableLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRenderEffectSupported()
    val blurReady = enableBlur && isRenderEffectSupported()

    MiuixTheme(controller = controller) {
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        CompositionLocalProvider(
            LocalThemeMode provides themeMode,
            LocalEnableBlur provides blurReady,
            LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
            LocalEnableLiquidGlass provides glassReady,
            LocalUseMonet provides useMonet,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}
