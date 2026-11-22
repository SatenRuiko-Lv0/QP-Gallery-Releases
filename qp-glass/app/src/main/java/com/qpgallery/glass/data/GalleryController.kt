package com.qpgallery.glass.data

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.qpgallery.glass.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ViewerSession(
    val title: String,
    val items: List<MediaItem>,
    val startIndex: Int,
    val albumPath: String?,
)

enum class FolderRuleKind {
    INCLUDE,
    EXCLUDE,
}

class GalleryController(
    context: Context,
    private val scope: CoroutineScope,
    val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext

    // Unified in-memory album index (public + hidden, always sorted in current albumSort order).
    // Only public albums are published to `_albums` while showHidden=false; toggling showHidden
    // flips the view filter in <1ms (0ms instant toggle, matching original QuickPic!).
    private var allAlbumsCache: List<Album> = emptyList()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasAllFilesAccess = MutableStateFlow(GalleryScanner.hasAllFilesAccess())
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private val _hasMediaAccess = MutableStateFlow(checkMediaAccess())
    val hasMediaAccess: StateFlow<Boolean> = _hasMediaAccess.asStateFlow()

    private val _lastViewedIndexByAlbum = MutableStateFlow<Map<String, Int>>(emptyMap())
    val lastViewedIndexByAlbum: StateFlow<Map<String, Int>> = _lastViewedIndexByAlbum.asStateFlow()

    private var scanJob: Job? = null
    private var hiddenPrewarmJob: Job? = null
    private var lastFullScanElapsedMs: Long = 0L
    private var lastHiddenScanElapsedMs: Long = 0L
    private var cacheHydrated: Boolean = false

    init {
        if (_hasAllFilesAccess.value || _hasMediaAccess.value) {
            hydrateFromDiskCacheSync()
        }
    }

    private fun publishVisibleAlbums(settings: UserSettings = settingsStore.state.value) {
        val visible = if (settings.showHidden) {
            allAlbumsCache
        } else {
            allAlbumsCache.filterNot { it.isHidden }
        }
        _albums.value = visible
    }

    private fun hydrateFromDiskCacheSync() {
        if (cacheHydrated) return
        cacheHydrated = true
        val initialSettings = settingsStore.state.value
        val t0 = SystemClock.elapsedRealtime()
        val cached = AlbumIndexCache.load(appContext, initialSettings)
        val dt = SystemClock.elapsedRealtime() - t0
        if (cached.hiddenRoots.isNotEmpty()) {
            GalleryScanner.seedDiscoveredHiddenRoots(cached.hiddenRoots)
        }
        if (cached.albums.isNotEmpty() && allAlbumsCache.isEmpty()) {
            allAlbumsCache = cached.albums
            publishVisibleAlbums(initialSettings)
            val hiddenCount = cached.albums.count { it.isHidden }
            Log.i(
                "QpPerf",
                "Warm index cache hydrated ${cached.albums.size} albums ($hiddenCount hidden ready for 0ms toggle) in ${dt}ms",
            )
        }
    }

    fun refreshPermissionsAndScan(force: Boolean = true) {
        val prevAllFiles = _hasAllFilesAccess.value
        val prevMedia = _hasMediaAccess.value
        val allFiles = GalleryScanner.hasAllFilesAccess()
        val media = checkMediaAccess()
        _hasAllFilesAccess.value = allFiles
        _hasMediaAccess.value = media

        if (!allFiles && !media) {
            allAlbumsCache = emptyList()
            _albums.value = emptyList()
            lastFullScanElapsedMs = 0L
            lastHiddenScanElapsedMs = 0L
            return
        }

        if (!cacheHydrated) {
            hydrateFromDiskCacheSync()
        }

        val permsChanged = (allFiles != prevAllFiles) || (media != prevMedia)
        val now = SystemClock.elapsedRealtime()
        val isStale = lastFullScanElapsedMs == 0L || (now - lastFullScanElapsedMs) > RESUME_RESCAN_INTERVAL_MS
        if (force || permsChanged || isStale) {
            rescan(clearStaleHidden = permsChanged)
        }
    }

    private fun checkMediaAccess(): Boolean {
        if (GalleryScanner.hasAllFilesAccess()) return true
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun rescan(clearStaleHidden: Boolean = false) {
        scanJob?.cancel()
        hiddenPrewarmJob?.cancel()
        val startSettings = settingsStore.state.value
        scanJob = scope.launch {
            _isScanning.value = true
            try {
                if (GalleryScanner.hasAllFilesAccess()) {
                    // Phase 1: True first-ever launch (empty cache) — ~50ms SQLite MediaStore fast-pass
                    if (allAlbumsCache.isEmpty()) {
                        val tFast = SystemClock.elapsedRealtime()
                        val fastAlbums = withContext(Dispatchers.IO) {
                            GalleryScanner.scanMediaStoreFast(appContext, startSettings.copy(showHidden = false))
                        }
                        val fastMs = SystemClock.elapsedRealtime() - tFast
                        if (fastAlbums.isNotEmpty() && allAlbumsCache.isEmpty()) {
                            allAlbumsCache = fastAlbums
                            publishVisibleAlbums()
                            Log.i("QpPerf", "Phase-1 MediaStore fast-pass rendered ${fastAlbums.size} albums in ${fastMs}ms")
                        }
                    }

                    // Phase 2A: Authoritative parallel NIO scan of public folders (also discovers all hidden roots)
                    val t0 = SystemClock.elapsedRealtime()
                    val publicSettings = settingsStore.state.value.copy(showHidden = false)
                    val publicScanned = withContext(Dispatchers.IO) {
                        GalleryScanner.scanAlbums(appContext, publicSettings)
                    }
                    val scanMs = SystemClock.elapsedRealtime() - t0
                    Log.i("QpPerf", "Phase-2A public parallel NIO scan: ${publicScanned.size} albums in ${scanMs}ms")

                    val latestSettings = settingsStore.state.value
                    val preservedHidden = if (clearStaleHidden) {
                        emptyList()
                    } else {
                        allAlbumsCache.filter { it.isHidden }
                    }
                    allAlbumsCache = mergeAndSort(publicScanned, preservedHidden, latestSettings.albumSort)
                    publishVisibleAlbums(latestSettings)
                    lastFullScanElapsedMs = SystemClock.elapsedRealtime()
                    _isScanning.value = false

                    // Phase 2B: Silent background hidden pre-warm (zero UI spinner, zero impact on foreground)
                    // Keeps hidden albums fresh in RAM + private cache so tapping "Show hidden" is 0ms instant!
                    launchSilentHiddenPrewarm()
                } else {
                    val t0 = SystemClock.elapsedRealtime()
                    val scanned = withContext(Dispatchers.IO) {
                        GalleryScanner.scanAlbums(appContext, startSettings)
                    }
                    val scanMs = SystemClock.elapsedRealtime() - t0
                    Log.i("QpPerf", "MediaStore fallback scan: ${scanned.size} albums in ${scanMs}ms")
                    allAlbumsCache = scanned
                    publishVisibleAlbums()
                    lastFullScanElapsedMs = SystemClock.elapsedRealtime()
                    withContext(Dispatchers.IO) {
                        AlbumIndexCache.save(appContext, settingsStore.state.value, allAlbumsCache)
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun launchSilentHiddenPrewarm() {
        hiddenPrewarmJob?.cancel()
        hiddenPrewarmJob = scope.launch {
            val s = settingsStore.state.value
            val t0 = SystemClock.elapsedRealtime()
            val hiddenAlbums = withContext(Dispatchers.IO) {
                GalleryScanner.scanDiscoveredHiddenOnly(s.copy(showHidden = true))
            }
            val dt = SystemClock.elapsedRealtime() - t0
            Log.i("QpPerf", "Phase-2B silent hidden pre-warm indexed ${hiddenAlbums.size} hidden albums in ${dt}ms")

            val latest = settingsStore.state.value
            val publicOnly = allAlbumsCache.filterNot { it.isHidden }
            val merged = withContext(Dispatchers.Default) {
                mergeAndSort(publicOnly, hiddenAlbums, latest.albumSort)
            }
            allAlbumsCache = merged
            lastHiddenScanElapsedMs = SystemClock.elapsedRealtime()
            if (latest.showHidden) {
                publishVisibleAlbums(latest)
            }
            withContext(Dispatchers.IO) {
                AlbumIndexCache.save(
                    context = appContext,
                    settings = latest,
                    albums = merged,
                    hiddenRoots = GalleryScanner.getDiscoveredHiddenRoots(),
                )
            }
        }
    }

    private fun mergeAndSort(
        publicAlbums: List<Album>,
        hiddenAlbums: List<Album>,
        albumSort: SortOrder,
    ): List<Album> {
        if (hiddenAlbums.isEmpty()) {
            return GalleryScanner.sortAlbumHeaders(publicAlbums, albumSort)
        }
        val mergedMap = LinkedHashMap<String, Album>(publicAlbums.size + hiddenAlbums.size)
        for (a in publicAlbums) mergedMap[a.path] = a
        for (h in hiddenAlbums) mergedMap[h.path] = h
        return GalleryScanner.sortAlbumHeaders(mergedMap.values.toList(), albumSort)
    }

    fun applySorting() {
        val currentSettings = settingsStore.state.value
        val currentAll = allAlbumsCache
        if (currentAll.isEmpty()) return
        scope.launch {
            val sortedAll = withContext(Dispatchers.Default) {
                GalleryScanner.sortAlbums(currentAll, currentSettings.albumSort, currentSettings.photoSort)
            }
            allAlbumsCache = sortedAll
            publishVisibleAlbums(settingsStore.state.value)
            withContext(Dispatchers.IO) {
                AlbumIndexCache.save(
                    context = appContext,
                    settings = settingsStore.state.value,
                    albums = sortedAll,
                    hiddenRoots = GalleryScanner.getDiscoveredHiddenRoots(),
                )
            }
        }
    }

    /**
     * Resolves and sorts photos for a single opened album lazily (either sorting in-memory items
     * or doing a ~3ms single-directory NIO scan if opened from instant cold-start cache).
     */
    suspend fun loadSortedAlbumItems(album: Album, photoSort: SortOrder): List<MediaItem> {
        val showHidden = settingsStore.state.value.showHidden || album.isHidden
        return if (album.hasFullItems) {
            withContext(Dispatchers.Default) {
                GalleryScanner.sortPhotos(album.items, photoSort)
            }
        } else {
            withContext(Dispatchers.IO) {
                GalleryScanner.scanSingleAlbum(album.path, showHidden, photoSort)
            }
        }
    }

    fun recordLastViewedIndex(albumPath: String?, index: Int) {
        if (albumPath == null || index < 0) return
        _lastViewedIndexByAlbum.value = _lastViewedIndexByAlbum.value + (albumPath to index)
    }

    fun updateSettings(rescanNeeded: Boolean = false, resortNeeded: Boolean = false, block: (UserSettings) -> UserSettings) {
        val prev = settingsStore.state.value
        settingsStore.update(block)
        val next = settingsStore.state.value
        val rulesChanged = prev.includedFolders != next.includedFolders || prev.excludedFolders != next.excludedFolders
        when {
            // Instant 0ms toggle of Show/Hide hidden items (matches original QuickPic instant flip!)
            rescanNeeded && !rulesChanged && prev.showHidden != next.showHidden && GalleryScanner.hasAllFilesAccess() -> {
                val t0 = SystemClock.elapsedRealtime()
                publishVisibleAlbums(next)
                val dt = SystemClock.elapsedRealtime() - t0
                Log.i("QpPerf", "Instant showHidden=${next.showHidden} toggle published ${_albums.value.size} albums in ${dt}ms")
                if (next.showHidden) {
                    val now = SystemClock.elapsedRealtime()
                    val hiddenStale = lastHiddenScanElapsedMs == 0L || (now - lastHiddenScanElapsedMs) > HIDDEN_RESCAN_INTERVAL_MS
                    if (hiddenStale && scanJob?.isActive != true && hiddenPrewarmJob?.isActive != true) {
                        if (GalleryScanner.hasDiscoveredHiddenRoots()) {
                            launchSilentHiddenPrewarm()
                        } else {
                            rescan(clearStaleHidden = false)
                        }
                    }
                }
            }
            rescanNeeded -> rescan(clearStaleHidden = rulesChanged)
            resortNeeded -> applySorting()
        }
    }

    fun setAppIconMode(mode: AppIconMode) {
        updateSettings { it.copy(appIconMode = mode) }
        AppIconManager.apply(appContext, mode)
    }

    fun addFolderRule(kind: FolderRuleKind, path: String) {
        addFolderRules(kind, listOf(path))
    }

    fun addFolderRules(kind: FolderRuleKind, paths: Collection<String>) {
        if (paths.isEmpty()) return
        val cleaned = paths.map { raw ->
            runCatching { File(raw).canonicalPath }.getOrElse { raw }.trimEnd('/')
        }.toSet()
        updateSettings(rescanNeeded = true) { s ->
            when (kind) {
                FolderRuleKind.INCLUDE -> s.copy(
                    includedFolders = s.includedFolders + cleaned,
                    excludedFolders = s.excludedFolders - cleaned,
                )
                FolderRuleKind.EXCLUDE -> s.copy(
                    excludedFolders = s.excludedFolders + cleaned,
                    includedFolders = s.includedFolders - cleaned,
                )
            }
        }
    }

    fun removeFolderRule(kind: FolderRuleKind, path: String) {
        removeFolderRules(kind, listOf(path))
    }

    fun removeFolderRules(kind: FolderRuleKind, paths: Collection<String>) {
        if (paths.isEmpty()) return
        val target = paths.toSet()
        updateSettings(rescanNeeded = true) { s ->
            when (kind) {
                FolderRuleKind.INCLUDE -> s.copy(includedFolders = s.includedFolders - target)
                FolderRuleKind.EXCLUDE -> s.copy(excludedFolders = s.excludedFolders - target)
            }
        }
    }

    fun shareItem(activityContext: Context, item: MediaItem) {
        val contentUri = shareableUri(activityContext, item) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            activityContext.startActivity(Intent.createChooser(intent, activityContext.getString(R.string.action_share)))
        }
    }

    fun openWith(activityContext: Context, item: MediaItem) {
        val contentUri = shareableUri(activityContext, item) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            activityContext.startActivity(Intent.createChooser(intent, activityContext.getString(R.string.action_open_with)))
        }
    }

    fun copyPath(activityContext: Context, item: MediaItem) {
        val clipboard = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("path", item.path))
        Toast.makeText(activityContext, activityContext.getString(R.string.copied_path), Toast.LENGTH_SHORT).show()
    }

    fun deleteItem(activityContext: Context, item: MediaItem, onDeleted: () -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                deleteSingleFile(item)
            }
            if (ok) {
                ImageLoader.invalidate(item.path)
                MediaScannerConnection.scanFile(appContext, arrayOf(item.path), null, null)
                Toast.makeText(activityContext, activityContext.getString(R.string.deleted_file), Toast.LENGTH_SHORT).show()
                rescan()
                onDeleted()
            } else {
                Toast.makeText(activityContext, activityContext.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Safely deletes all media files inside selected albums and removes any directory that becomes empty
     * (never deletes unrelated non-image documents or subfolders).
     */
    fun deleteAlbums(activityContext: Context, targetAlbums: List<Album>, onDone: () -> Unit = {}) {
        if (targetAlbums.isEmpty()) return
        val settings = settingsStore.state.value
        scope.launch {
            val deletedPaths = ArrayList<String>()
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                for (album in targetAlbums) {
                    val itemsToDelete = if (album.hasFullItems) {
                        album.items
                    } else {
                        GalleryScanner.scanSingleAlbum(album.path, settings.showHidden || album.isHidden, settings.photoSort)
                    }
                    for (item in itemsToDelete) {
                        if (deleteSingleFile(item)) {
                            deletedCount++
                            deletedPaths.add(item.path)
                            ImageLoader.invalidate(item.path)
                        }
                    }
                    // Clean up the album directory only if completely empty (or only contains .nomedia)
                    val dir = File(album.path)
                    if (dir.isDirectory) {
                        val remaining = dir.listFiles().orEmpty()
                        if (remaining.isEmpty()) {
                            dir.delete()
                        } else if (remaining.size == 1 && remaining[0].name == ".nomedia" && remaining[0].isFile) {
                            if (remaining[0].delete()) {
                                dir.delete()
                            }
                        }
                    }
                }
            }
            if (deletedPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(appContext, deletedPaths.toTypedArray(), null, null)
            }
            if (deletedCount > 0) {
                Toast.makeText(
                    activityContext,
                    activityContext.getString(R.string.deleted_albums_result, deletedCount),
                    Toast.LENGTH_SHORT,
                ).show()
                rescan()
            } else {
                Toast.makeText(activityContext, activityContext.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
    }

    private fun deleteSingleFile(item: MediaItem): Boolean {
        val file = item.file
        val deleted = if (file.exists()) file.delete() else false
        if (!deleted && item.uri.scheme == "content") {
            return runCatching { appContext.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
        }
        return deleted
    }

    private fun shareableUri(ctx: Context, item: MediaItem): Uri? {
        if (item.uri.scheme == "content") return item.uri
        return runCatching {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", item.file)
        }.getOrNull()
    }

    companion object {
        private const val RESUME_RESCAN_INTERVAL_MS = 45_000L
        private const val HIDDEN_RESCAN_INTERVAL_MS = 30_000L
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}
