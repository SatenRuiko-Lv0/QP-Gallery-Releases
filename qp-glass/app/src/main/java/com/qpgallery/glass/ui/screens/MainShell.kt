package com.qpgallery.glass.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qpgallery.glass.R
import com.qpgallery.glass.data.Album
import com.qpgallery.glass.data.AlbumViewMode
import com.qpgallery.glass.data.FolderRuleKind
import com.qpgallery.glass.data.GalleryController
import com.qpgallery.glass.data.MediaItem
import com.qpgallery.glass.data.ViewerSession
import com.qpgallery.glass.ui.components.FilePreviewOverlay
import com.qpgallery.glass.ui.icons.GlassIcons
import com.qpgallery.glass.ui.liquid.BlurredBar
import com.qpgallery.glass.ui.liquid.FloatingBottomBar
import com.qpgallery.glass.ui.liquid.FloatingBottomBarItem
import com.qpgallery.glass.ui.liquid.ProvideSharedGravityLighting
import com.qpgallery.glass.ui.theme.LocalEnableBlur
import com.qpgallery.glass.ui.theme.LocalEnableFloatingBottomBar
import com.qpgallery.glass.ui.theme.LocalEnableLiquidGlass
import com.qpgallery.glass.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class RootTab(val labelRes: Int, val icon: ImageVector) {
    ALBUMS(R.string.tab_albums, GlassIcons.Albums),
    SETTINGS(R.string.tab_settings, GlassIcons.Settings),
}

@Composable
fun MainShell(
    controller: GalleryController,
    externalViewerSession: ViewerSession?,
    onClearExternalViewer: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestMediaAccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by controller.settingsStore.state.collectAsState()
    val albums by controller.albums.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    val hasAllFiles by controller.hasAllFilesAccess.collectAsState()
    val hasMedia by controller.hasMediaAccess.collectAsState()
    val lastViewedByAlbum by controller.lastViewedIndexByAlbum.collectAsState()

    val totalPhotos = remember(albums) { albums.sumOf { it.count } }
    val hasStorageAccess = hasAllFiles || hasMedia

    val pagerState = rememberPagerState(initialPage = 0) { RootTab.entries.size }
    var openAlbumPath by remember { mutableStateOf<String?>(null) }
    var activeViewer by remember { mutableStateOf<ViewerSession?>(null) }
    var folderRuleKind by remember { mutableStateOf<FolderRuleKind?>(null) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    var restoredLastAlbum by remember { mutableStateOf(false) }
    // Snapshot once at cold launch so toggling "Open last album" in Settings only takes effect on NEXT launch!
    val shouldRestoreOnLaunch = remember { settings.openLastAlbum }
    val launchLastAlbumPath = remember { settings.lastAlbumPath }
    var albumSelectionMode by remember { mutableStateOf(false) }

    var cachedOpenAlbum by remember { mutableStateOf<Album?>(null) }
    var cachedRuleKind by remember { mutableStateOf<FolderRuleKind?>(null) }
    var cachedViewer by remember { mutableStateOf<ViewerSession?>(null) }

    LaunchedEffect(externalViewerSession) {
        if (externalViewerSession != null) {
            activeViewer = externalViewerSession
            onClearExternalViewer()
        }
    }

    LaunchedEffect(albums) {
        if (!restoredLastAlbum && shouldRestoreOnLaunch && !launchLastAlbumPath.isNullOrBlank() && albums.isNotEmpty()) {
            restoredLastAlbum = true
            if (albums.any { it.path == launchLastAlbumPath }) {
                openAlbumPath = launchLastAlbumPath
            }
        }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBar = LocalEnableFloatingBottomBar.current
    val enableLiquidGlass = LocalEnableLiquidGlass.current

    val albumsBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val settingsBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val sheetBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    // Zero nested full-screen layerBackdrop passes: FloatingBottomBar directly refracts the active page's single backdrop!
    val activeRootBackdrop = if (pagerState.currentPage == 0) albumsBackdrop else settingsBackdrop

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBottomPadding = if (navBottomInset > 0.dp) 8.dp + navBottomInset else 20.dp
    val bottomBarClearance = if (enableFloatingBar) 64.dp + floatingBottomPadding + 8.dp else 72.dp + navBottomInset

    val openAlbum = remember(albums, openAlbumPath) {
        openAlbumPath?.let { path -> albums.firstOrNull { it.path == path } }
    }
    if (openAlbum != null) cachedOpenAlbum = openAlbum
    if (folderRuleKind != null) cachedRuleKind = folderRuleKind
    if (activeViewer != null) cachedViewer = activeViewer

    // Privacy safeguard: if hidden albums auto-lock on background, also immediately close any open hidden album/viewer!
    LaunchedEffect(settings.showHidden) {
        if (!settings.showHidden && cachedOpenAlbum?.isHidden == true) {
            activeViewer = null
            previewItem = null
            openAlbumPath = null
            cachedOpenAlbum = null
        }
    }

    BackHandler(enabled = pagerState.currentPage != 0 && openAlbum == null && folderRuleKind == null && activeViewer == null && previewItem == null) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    ProvideSharedGravityLighting(enabled = enableLiquidGlass) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (previewItem != null) Modifier.layerBackdrop(sheetBackdrop)
                        else Modifier
                    ),
            ) {
                // Root 2-tab shell (Albums · Settings — ZERO nested full-screen layerBackdrop copies!)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    userScrollEnabled = !albumSelectionMode,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (RootTab.entries[page]) {
                        RootTab.ALBUMS -> AlbumsScreen(
                            albums = albums,
                            totalPhotos = totalPhotos,
                            settings = settings,
                            isScanning = isScanning,
                            hasStorageAccess = hasStorageAccess,
                            hasAllFilesAccess = hasAllFiles,
                            contentBackdrop = albumsBackdrop,
                            bottomBarClearance = bottomBarClearance,
                            onOpenAlbum = { album ->
                                openAlbumPath = album.path
                                controller.updateSettings { it.copy(lastAlbumPath = album.path) }
                            },
                            onToggleViewMode = {
                                controller.updateSettings {
                                    it.copy(
                                        albumViewMode = if (it.albumViewMode == AlbumViewMode.GRID) {
                                            AlbumViewMode.LIST
                                        } else {
                                            AlbumViewMode.GRID
                                        },
                                    )
                                }
                            },
                            onSetAlbumColumns = { cols ->
                                controller.updateSettings {
                                    it.copy(
                                        albumViewMode = AlbumViewMode.GRID,
                                        albumGridColumns = cols.coerceIn(2, 4),
                                    )
                                }
                            },
                            onSelectSort = { sort ->
                                controller.updateSettings(resortNeeded = true) { it.copy(albumSort = sort) }
                            },
                            onToggleShowHidden = {
                                controller.updateSettings(rescanNeeded = true) { it.copy(showHidden = !it.showHidden) }
                            },
                            onOpenFolderRules = { kind -> folderRuleKind = kind },
                            onExcludeAlbums = { selected ->
                                controller.addFolderRules(FolderRuleKind.EXCLUDE, selected.map { it.path })
                            },
                            onDeleteAlbums = { selected ->
                                controller.deleteAlbums(context, selected)
                            },
                            onSelectionModeChanged = { selecting -> albumSelectionMode = selecting },
                            onRefresh = { controller.refreshPermissionsAndScan(force = true) },
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onRequestMediaAccess = onRequestMediaAccess,
                        )

                        RootTab.SETTINGS -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (enableLiquidGlass || enableBlur) Modifier.layerBackdrop(settingsBackdrop)
                                    else Modifier
                                ),
                        ) {
                            SettingsScreen(
                                settings = settings,
                                backdrop = settingsBackdrop,
                                bottomBarClearance = bottomBarClearance,
                                onUpdateSettings = { rescan, block ->
                                    controller.updateSettings(rescanNeeded = rescan, block = block)
                                },
                                onSelectAppIconMode = { mode -> controller.setAppIconMode(mode) },
                                onOpenFolderRules = { kind -> folderRuleKind = kind },
                            )
                        }
                    }
                }

            // Clean 2-tab Floating Liquid Glass Bottom Bar (auto-hides during Albums multi-select)
            val showBottomNav = !(albumSelectionMode && pagerState.currentPage == 0)
            if (enableFloatingBar) {
                AnimatedVisibility(
                    visible = showBottomNav,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    FloatingBottomBar(
                        modifier = Modifier
                            .pointerInput(Unit) { detectTapGestures { } }
                            .padding(start = 52.dp, end = 52.dp, bottom = floatingBottomPadding),
                        selectedIndex = pagerState.currentPage,
                        onSelected = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                        backdrop = activeRootBackdrop,
                        tabsCount = RootTab.entries.size,
                        isGlassEnabled = enableLiquidGlass,
                    ) { activateTab ->
                        RootTab.entries.forEachIndexed { index, tab ->
                            val label = stringResource(tab.labelRes)
                            FloatingBottomBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = { activateTab(index) },
                                modifier = Modifier.defaultMinSize(minWidth = 104.dp),
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            }
                        }
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = showBottomNav,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BlurredBar(activeRootBackdrop, blurActive = enableBlur) {
                        NavigationBar(
                            color = if (enableBlur) Color.Transparent else MiuixTheme.colorScheme.surface,
                        ) {
                            RootTab.entries.forEachIndexed { index, tab ->
                                val label = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    modifier = Modifier.weight(1f),
                                    icon = tab.icon,
                                    label = label,
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Screen 1: Translucent Liquid Glass Album Detail Grid
            AnimatedVisibility(
                visible = openAlbum != null,
                enter = scaleIn(
                    initialScale = 0.95f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                ) + fadeIn(spring(stiffness = 460f)),
                exit = scaleOut(
                    targetScale = 0.96f,
                    animationSpec = spring(stiffness = 500f),
                ) + fadeOut(spring(stiffness = 520f)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val shownAlbum = openAlbum ?: cachedOpenAlbum
                if (shownAlbum != null) {
                    AlbumDetailScreen(
                        album = shownAlbum,
                        settings = settings,
                        lastViewedIndex = lastViewedByAlbum[shownAlbum.path],
                        backEnabled = openAlbum != null && activeViewer == null && folderRuleKind == null && previewItem == null,
                        loadAlbumItems = { target, sort -> controller.loadSortedAlbumItems(target, sort) },
                        onOpenPhoto = { items, idx ->
                            activeViewer = ViewerSession(
                                title = shownAlbum.name,
                                items = items,
                                startIndex = idx,
                                albumPath = shownAlbum.path,
                            )
                        },
                        onPreviewPhoto = { previewItem = it },
                        onSelectPhotoSort = { order ->
                            controller.updateSettings(resortNeeded = true) { it.copy(photoSort = order) }
                        },
                        onSetColumns = { cols ->
                            controller.updateSettings { it.copy(gridColumns = cols.coerceIn(3, 5)) }
                        },
                        onCycleColumns = {
                            controller.updateSettings {
                                val next = when (it.gridColumns) {
                                    3 -> 4
                                    4 -> 5
                                    else -> 3
                                }
                                it.copy(gridColumns = next)
                            }
                        },
                        onExcludeAlbum = {
                            val path = shownAlbum.path
                            openAlbumPath = null
                            controller.addFolderRule(FolderRuleKind.EXCLUDE, path)
                        },
                        onBack = { openAlbumPath = null },
                    )
                }
            }

            // Secondary Screen 2: Included / Excluded Folder Rules + Multi-Select Directory Browser
            AnimatedVisibility(
                visible = folderRuleKind != null,
                enter = scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 430f),
                ) + fadeIn(spring(stiffness = 460f)),
                exit = scaleOut(
                    targetScale = 0.96f,
                    animationSpec = spring(stiffness = 500f),
                ) + fadeOut(spring(stiffness = 520f)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val shownKind = folderRuleKind ?: cachedRuleKind
                if (shownKind != null) {
                    FolderRulesScreen(
                        kind = shownKind,
                        folders = if (shownKind == FolderRuleKind.INCLUDE) settings.includedFolders else settings.excludedFolders,
                        showHidden = settings.showHidden,
                        backEnabled = folderRuleKind != null && activeViewer == null && previewItem == null,
                        onAddFolders = { paths -> controller.addFolderRules(shownKind, paths) },
                        onRemoveFolders = { paths -> controller.removeFolderRules(shownKind, paths) },
                        onBack = { folderRuleKind = null },
                    )
                }
            }

            // Topmost Fullscreen Vertical Comic Reader
            AnimatedVisibility(
                visible = activeViewer != null,
                enter = scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 410f),
                ) + fadeIn(spring(stiffness = 440f)),
                exit = scaleOut(
                    targetScale = 0.95f,
                    animationSpec = spring(stiffness = 480f),
                ) + fadeOut(spring(stiffness = 500f)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val viewer = activeViewer ?: cachedViewer
                if (viewer != null) {
                    ComicReaderScreen(
                        items = viewer.items,
                        initialIndex = viewer.startIndex,
                        maxBrightness = settings.maxBrightness,
                        backEnabled = activeViewer != null && previewItem == null,
                        onPageChanged = { page -> controller.recordLastViewedIndex(viewer.albumPath, page) },
                        onInspectItem = { previewItem = it },
                        onBack = { page ->
                            controller.recordLastViewedIndex(viewer.albumPath, page)
                            activeViewer = null
                        },
                    )
                }
            }
        }

        // Global Liquid Glass File Preview, Info & Two-Step Delete Sheet (refracts topmost active screen!)
        FilePreviewOverlay(
            item = previewItem,
            backdrop = sheetBackdrop,
            onDismiss = { previewItem = null },
            onShare = { controller.shareItem(context, it) },
            onOpenWith = { controller.openWith(context, it) },
            onCopyPath = { controller.copyPath(context, it) },
            onDelete = { target ->
                controller.deleteItem(context, target) {
                    val cur = activeViewer
                    if (cur != null) {
                        val remaining = cur.items.filterNot { it.path == target.path }
                        activeViewer = if (remaining.isEmpty()) {
                            null
                        } else {
                            cur.copy(
                                items = remaining,
                                startIndex = cur.startIndex.coerceAtMost(remaining.lastIndex),
                            )
                        }
                    }
                }
            },
            darkSurface = activeViewer != null || isInDarkTheme(),
        )
        }
    }
}
