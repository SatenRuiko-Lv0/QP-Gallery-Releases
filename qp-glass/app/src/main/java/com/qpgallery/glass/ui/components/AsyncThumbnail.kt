package com.qpgallery.glass.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.qpgallery.glass.data.ImageLoader
import com.qpgallery.glass.data.MediaItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AsyncThumbnail(
    item: MediaItem?,
    maxEdgePx: Int,
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(14.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    contentDescription: String? = null,
    allowDiskCache: Boolean = true,
) {
    val context = LocalContext.current
    val key = item?.path
    val canonicalEdge = ImageLoader.canonicalThumbEdge(maxEdgePx)
    var imageBitmap by remember(key, canonicalEdge) {
        mutableStateOf<ImageBitmap?>(
            ImageLoader.getFastThumbImageForCompose(item, canonicalEdge, allowDiskCache),
        )
    }

    LaunchedEffect(key, canonicalEdge, allowDiskCache) {
        if (item == null) {
            imageBitmap = null
            return@LaunchedEffect
        }
        if (imageBitmap != null) return@LaunchedEffect
        val fast = ImageLoader.getFastThumbImageForCompose(item, canonicalEdge, allowDiskCache)
        if (fast != null) {
            imageBitmap = fast
        } else {
            imageBitmap = ImageLoader.loadThumbImage(context.contentResolver, item, canonicalEdge, allowDiskCache)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderColor),
    ) {
        val img = imageBitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = contentDescription ?: item?.name,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
