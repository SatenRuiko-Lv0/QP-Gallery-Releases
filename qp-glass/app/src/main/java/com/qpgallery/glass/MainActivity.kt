package com.qpgallery.glass

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.qpgallery.glass.data.GalleryController
import com.qpgallery.glass.data.GalleryScanner
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.MediaItem
import com.qpgallery.glass.data.SettingsStore
import com.qpgallery.glass.data.ViewerSession
import com.qpgallery.glass.ui.screens.MainShell
import com.qpgallery.glass.ui.theme.QpTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var controller: GalleryController
    private val externalViewerState = mutableStateOf<ViewerSession?>(null)

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        controller.refreshPermissionsAndScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        ImageLoader.initDiskCache(applicationContext.cacheDir)
        settingsStore = SettingsStore(this)
        controller = GalleryController(this, lifecycleScope, settingsStore)
        handleIncomingIntent(intent)

        setContent {
            val settings by settingsStore.state.collectAsState()
            QpTheme(
                themeMode = settings.themeMode,
                useMonet = settings.useMonet,
                enableBlur = settings.enableBlur,
                enableFloatingBottomBar = settings.enableFloatingBottomBar,
                enableLiquidGlass = settings.enableLiquidGlass,
            ) {
                MainShell(
                    controller = controller,
                    externalViewerSession = externalViewerState.value,
                    onClearExternalViewer = { externalViewerState.value = null },
                    onRequestAllFilesAccess = ::requestAllFilesAccess,
                    onRequestMediaAccess = ::requestMediaPermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshPermissionsAndScan(force = false)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ImageLoader.trimMemory(level)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Image"
        val item = MediaItem(
            path = uri.path ?: uri.toString(),
            uri = uri,
            name = name,
            folderPath = File(uri.path ?: "").parent ?: "",
            sizeBytes = 0L,
            lastModifiedMs = System.currentTimeMillis(),
            mimeType = intent.type ?: GalleryScanner.mimeTypeFor(name),
        )
        externalViewerState.value = ViewerSession(
            title = name,
            items = listOf(item),
            startIndex = 0,
            albumPath = null,
        )
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            runCatching { startActivity(intent) }.onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            requestMediaPermission()
        }
    }

    private fun requestMediaPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        mediaPermissionLauncher.launch(perm)
    }
}
