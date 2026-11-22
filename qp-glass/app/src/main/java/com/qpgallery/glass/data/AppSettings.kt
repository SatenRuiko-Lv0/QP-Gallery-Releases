package com.qpgallery.glass.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import com.qpgallery.glass.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppIconMode(val id: Int, val aliasClass: String) {
    AUTO(0, "com.qpgallery.glass.LauncherAuto"),
    LIGHT(1, "com.qpgallery.glass.LauncherLight"),
    DARK(2, "com.qpgallery.glass.LauncherDark");

    companion object {
        fun fromId(id: Int): AppIconMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

object AppIconManager {
    fun apply(context: Context, mode: AppIconMode) {
        val pm = context.applicationContext.packageManager
        val pkg = context.applicationContext.packageName
        // Enable target alias FIRST so there is never a moment without a launcher entry
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, mode.aliasClass),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        AppIconMode.entries
            .filter { it != mode }
            .forEach { other ->
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, other.aliasClass),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
    }
}

@Immutable
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appIconMode: AppIconMode = AppIconMode.AUTO,
    val useMonet: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableLiquidGlass: Boolean = true,
    val maxBrightness: Boolean = false,
    val openLastAlbum: Boolean = false,
    val lastAlbumPath: String? = null,
    val albumGridColumns: Int = 2,
    val gridColumns: Int = 3,
    val albumViewMode: AlbumViewMode = AlbumViewMode.GRID,
    val albumSort: SortOrder = SortOrder.DATE_DESC,
    val photoSort: SortOrder = SortOrder.NAME_ASC,
    val showHidden: Boolean = false,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("qp_glass_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state.asStateFlow()

    private fun read(): UserSettings = UserSettings(
        themeMode = ThemeMode.fromId(prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.id)),
        appIconMode = AppIconMode.fromId(prefs.getInt(KEY_APP_ICON_MODE, AppIconMode.AUTO.id)),
        useMonet = prefs.getBoolean(KEY_MONET, false),
        enableBlur = prefs.getBoolean(KEY_BLUR, true),
        enableFloatingBottomBar = prefs.getBoolean(KEY_FLOATING_BAR, true),
        enableLiquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, true),
        maxBrightness = prefs.getBoolean(KEY_MAX_BRIGHTNESS, false),
        openLastAlbum = prefs.getBoolean(KEY_OPEN_LAST_ALBUM, false),
        lastAlbumPath = prefs.getString(KEY_LAST_ALBUM_PATH, null),
        albumGridColumns = prefs.getInt(KEY_ALBUM_GRID_COLUMNS, 2).coerceIn(2, 4),
        gridColumns = prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(3, 5),
        albumViewMode = AlbumViewMode.fromId(prefs.getInt(KEY_ALBUM_VIEW_MODE, AlbumViewMode.GRID.id)),
        albumSort = SortOrder.fromId(prefs.getInt(KEY_ALBUM_SORT, SortOrder.DATE_DESC.id)),
        photoSort = SortOrder.fromId(prefs.getInt(KEY_PHOTO_SORT, SortOrder.NAME_ASC.id)),
        // Privacy rule: hidden items ALWAYS start disabled on every launch and require manual user opt-in!
        showHidden = false,
        includedFolders = prefs.getStringSet(KEY_INCLUDED_FOLDERS, emptySet())?.toSet() ?: emptySet(),
        excludedFolders = prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet())?.toSet() ?: emptySet(),
    )

    fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit().apply {
            putInt(KEY_THEME_MODE, next.themeMode.id)
            putInt(KEY_APP_ICON_MODE, next.appIconMode.id)
            putBoolean(KEY_MONET, next.useMonet)
            putBoolean(KEY_BLUR, next.enableBlur)
            putBoolean(KEY_FLOATING_BAR, next.enableFloatingBottomBar)
            putBoolean(KEY_LIQUID_GLASS, next.enableLiquidGlass)
            putBoolean(KEY_MAX_BRIGHTNESS, next.maxBrightness)
            putBoolean(KEY_OPEN_LAST_ALBUM, next.openLastAlbum)
            putString(KEY_LAST_ALBUM_PATH, next.lastAlbumPath)
            putInt(KEY_ALBUM_GRID_COLUMNS, next.albumGridColumns)
            putInt(KEY_GRID_COLUMNS, next.gridColumns)
            putInt(KEY_ALBUM_VIEW_MODE, next.albumViewMode.id)
            putInt(KEY_ALBUM_SORT, next.albumSort.id)
            putInt(KEY_PHOTO_SORT, next.photoSort.id)
            remove(KEY_SHOW_HIDDEN)
            putStringSet(KEY_INCLUDED_FOLDERS, next.includedFolders)
            putStringSet(KEY_EXCLUDED_FOLDERS, next.excludedFolders)
        }.apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APP_ICON_MODE = "app_icon_mode"
        private const val KEY_MONET = "use_monet"
        private const val KEY_BLUR = "enable_blur"
        private const val KEY_FLOATING_BAR = "enable_floating_bar"
        private const val KEY_LIQUID_GLASS = "enable_liquid_glass"
        private const val KEY_MAX_BRIGHTNESS = "max_brightness"
        private const val KEY_OPEN_LAST_ALBUM = "open_last_album"
        private const val KEY_LAST_ALBUM_PATH = "last_album_path"
        private const val KEY_ALBUM_GRID_COLUMNS = "album_grid_columns"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_ALBUM_VIEW_MODE = "album_view_mode"
        private const val KEY_ALBUM_SORT = "album_sort"
        private const val KEY_PHOTO_SORT = "photo_sort"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_INCLUDED_FOLDERS = "included_folders"
        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
    }
}
