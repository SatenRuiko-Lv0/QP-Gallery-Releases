package com.qpgallery.glass.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class MediaItem(
    val path: String,
    val uri: Uri,
    val name: String,
    val folderPath: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val mimeType: String,
) {
    val file: File get() = File(path)
    val extension: String get() = name.substringAfterLast('.', "").uppercase()
}

@Immutable
data class Album(
    val path: String,
    val name: String,
    val items: List<MediaItem>,
    val latestModifiedMs: Long,
    val isHidden: Boolean,
    val cachedCount: Int = items.size,
    val cachedCover: MediaItem? = items.firstOrNull(),
) {
    val count: Int get() = if (items.isNotEmpty()) items.size else cachedCount
    val cover: MediaItem? get() = cachedCover ?: items.firstOrNull()
    val hasFullItems: Boolean get() = items.isNotEmpty()
}

@Immutable
data class FolderEntry(
    val path: String,
    val name: String,
    val subfolderCount: Int,
    val imageCount: Int,
    val isHidden: Boolean,
)

enum class AlbumViewMode(val id: Int) {
    GRID(0),
    LIST(1);

    companion object {
        fun fromId(id: Int): AlbumViewMode = entries.find { it.id == id } ?: GRID
    }
}

enum class SortOrder(val id: Int) {
    NAME_ASC(0),
    NAME_DESC(1),
    DATE_DESC(2),
    DATE_ASC(3),
    COUNT_DESC(4);

    companion object {
        fun fromId(id: Int): SortOrder = entries.find { it.id == id } ?: NAME_ASC
    }
}

/**
 * Natural alphanumeric comparison so manga pages sort as 1, 2, 10 instead of 1, 10, 2.
 */
fun naturalCompare(a: String, b: String): Int {
    var ia = 0
    var ib = 0
    val la = a.length
    val lb = b.length
    while (ia < la && ib < lb) {
        val ca = a[ia]
        val cb = b[ib]
        if (ca.isDigit() && cb.isDigit()) {
            var za = ia
            while (za < la && a[za] == '0') za++
            var ea = za
            while (ea < la && a[ea].isDigit()) ea++

            var zb = ib
            while (zb < lb && b[zb] == '0') zb++
            var eb = zb
            while (eb < lb && b[eb].isDigit()) eb++

            val lenDiff = (ea - za) - (eb - zb)
            if (lenDiff != 0) return lenDiff
            for (k in 0 until (ea - za)) {
                val digitDiff = a[za + k] - b[zb + k]
                if (digitDiff != 0) return digitDiff
            }
            val zeroDiff = (za - ia) - (zb - ib)
            if (zeroDiff != 0) return zeroDiff
            ia = ea
            ib = eb
        } else {
            val diff = ca.lowercaseChar() - cb.lowercaseChar()
            if (diff != 0) return diff
            ia++
            ib++
        }
    }
    return (la - ia) - (lb - ib)
}
