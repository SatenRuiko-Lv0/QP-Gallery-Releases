package com.qpgallery.glass.data

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

data class CachedGalleryIndex(
    val albums: List<Album>,
    val hiddenRoots: Set<String>,
) {
    companion object {
        val EMPTY = CachedGalleryIndex(emptyList(), emptySet())
    }
}

/**
 * Compact binary snapshot of unified album summaries (both public and hidden, stored strictly inside
 * the app's private sandbox cacheDir) plus discovered hidden roots.
 * Enables <10ms Frame-0 cold-start rendering AND 0ms instant "Show hidden items" toggling just like
 * original QuickPic — while showHidden still resets to false on every cold launch!
 */
object AlbumIndexCache {
    private const val FILE_NAME = "album_index_v1.bin"
    private const val MAGIC = 0x51504735 // "QPG5"
    private const val VERSION = 3

    private fun cacheFile(context: Context): File = File(context.cacheDir, FILE_NAME)

    private fun signatureFor(settings: UserSettings): String = buildString {
        append(settings.includedFolders.sorted().joinToString(";"))
        append('|')
        append(settings.excludedFolders.sorted().joinToString(";"))
    }

    fun load(context: Context, settings: UserSettings): CachedGalleryIndex {
        val file = cacheFile(context)
        if (!file.isFile || file.length() < 16L) return CachedGalleryIndex.EMPTY
        val expectedSig = signatureFor(settings)
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream(), 32 * 1024)).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                if (magic != MAGIC || version != VERSION) return CachedGalleryIndex.EMPTY
                val sig = input.readUTF()
                if (sig != expectedSig) return CachedGalleryIndex.EMPTY
                val count = input.readInt().coerceIn(0, 50_000)
                val list = ArrayList<Album>(count)
                repeat(count) {
                    val path = input.readUTF()
                    val name = input.readUTF()
                    val itemCount = input.readInt()
                    val latestMod = input.readLong()
                    val isHidden = input.readBoolean()
                    val hasCover = input.readBoolean()
                    val cover = if (hasCover) {
                        val cPath = input.readUTF()
                        val cUriStr = input.readUTF()
                        val cName = input.readUTF()
                        val cSize = input.readLong()
                        val cMod = input.readLong()
                        val cMime = input.readUTF()
                        MediaItem(
                            path = cPath,
                            uri = Uri.parse(cUriStr),
                            name = cName,
                            folderPath = path,
                            sizeBytes = cSize,
                            lastModifiedMs = cMod,
                            mimeType = cMime,
                        )
                    } else {
                        null
                    }
                    list.add(
                        Album(
                            path = path,
                            name = name,
                            items = emptyList(),
                            latestModifiedMs = latestMod,
                            isHidden = isHidden,
                            cachedCount = itemCount,
                            cachedCover = cover,
                        ),
                    )
                }
                val rootCount = input.readInt().coerceIn(0, 10_000)
                val roots = LinkedHashSet<String>(rootCount)
                repeat(rootCount) {
                    roots.add(input.readUTF())
                }
                CachedGalleryIndex(
                    albums = GalleryScanner.sortAlbumHeaders(list, settings.albumSort),
                    hiddenRoots = roots,
                )
            }
        }.getOrElse { CachedGalleryIndex.EMPTY }
    }

    fun save(
        context: Context,
        settings: UserSettings,
        albums: List<Album>,
        hiddenRoots: Set<String> = emptySet(),
    ) {
        val target = cacheFile(context)
        val tmp = File(context.cacheDir, "$FILE_NAME.tmp")
        val sig = signatureFor(settings)
        runCatching {
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 32 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(sig)
                out.writeInt(albums.size)
                for (album in albums) {
                    out.writeUTF(album.path)
                    out.writeUTF(album.name)
                    out.writeInt(album.count)
                    out.writeLong(album.latestModifiedMs)
                    out.writeBoolean(album.isHidden)
                    val cover = album.cover
                    out.writeBoolean(cover != null)
                    if (cover != null) {
                        out.writeUTF(cover.path)
                        out.writeUTF(cover.uri.toString())
                        out.writeUTF(cover.name)
                        out.writeLong(cover.sizeBytes)
                        out.writeLong(cover.lastModifiedMs)
                        out.writeUTF(cover.mimeType)
                    }
                }
                out.writeInt(hiddenRoots.size)
                for (r in hiddenRoots) {
                    out.writeUTF(r)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }
}
