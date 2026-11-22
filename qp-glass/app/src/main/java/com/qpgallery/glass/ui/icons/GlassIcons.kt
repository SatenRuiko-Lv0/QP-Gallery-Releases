package com.qpgallery.glass.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Cohesive Apple SF-Symbols-inspired vector glyphs crafted specifically for Liquid Glass surfaces:
 * uniform 1.85dp rounded strokes, airy negative space, and crisp optical balance.
 */
object GlassIcons {

    private val strokeBrush = SolidColor(Color.White)
    private const val W = 1.85f

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** Optically centered Apple iOS navigation chevron `<`. */
    val BackChevron: ImageVector by lazy {
        icon("BackChevron") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.15f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14.5f, 5.5f)
                lineTo(8.0f, 12.0f)
                lineTo(14.5f, 18.5f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 6.5f)
                lineTo(15.0f, 12.0f)
                lineTo(9.5f, 17.5f)
            }
        }
    }

    /** Layered photo stack (SF Symbols: photo.stack). */
    val Albums: ImageVector by lazy {
        icon("Albums") {
            // Top subtle stacked edge
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(7.0f, 4.2f)
                horizontalLineTo(17.0f)
            }
            // Main rounded frame
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 7.2f)
                horizontalLineTo(17.5f)
                curveTo(19.0f, 7.2f, 20.0f, 8.2f, 20.0f, 9.7f)
                verticalLineTo(17.3f)
                curveTo(20.0f, 18.8f, 19.0f, 19.8f, 17.5f, 19.8f)
                horizontalLineTo(6.5f)
                curveTo(5.0f, 19.8f, 4.0f, 18.8f, 4.0f, 17.3f)
                verticalLineTo(9.7f)
                curveTo(4.0f, 8.2f, 5.0f, 7.2f, 6.5f, 7.2f)
                close()
            }
            // Soft mountain ridge
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.4f, 17.6f)
                lineTo(9.8f, 13.0f)
                lineTo(13.2f, 16.2f)
                lineTo(15.5f, 14.0f)
                lineTo(18.7f, 17.6f)
            }
            // Sun dot
            path(fill = strokeBrush) {
                moveTo(15.2f, 10.7f)
                arcTo(1.25f, 1.25f, 0f, true, true, 12.7f, 10.7f)
                arcTo(1.25f, 1.25f, 0f, true, true, 15.2f, 10.7f)
                close()
            }
        }
    }

    /** Clean precision sliders/gear glyph (SF Symbols: gearshape). */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Inner circle
                moveTo(15.0f, 12.0f)
                arcTo(3.0f, 3.0f, 0f, true, true, 9.0f, 12.0f)
                arcTo(3.0f, 3.0f, 0f, true, true, 15.0f, 12.0f)
                close()
            }
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.4f, 15.0f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.73f, 16.82f)
                lineTo(19.79f, 16.88f)
                arcTo(2.0f, 2.0f, 0f, false, true, 16.96f, 19.71f)
                lineTo(16.9f, 19.65f)
                arcTo(1.65f, 1.65f, 0f, false, false, 15.08f, 19.32f)
                arcTo(1.65f, 1.65f, 0f, false, false, 14.08f, 20.83f)
                verticalLineTo(21.0f)
                arcTo(2.0f, 2.0f, 0f, false, true, 10.08f, 21.0f)
                verticalLineTo(20.91f)
                arcTo(1.65f, 1.65f, 0f, false, false, 9.0f, 19.4f)
                arcTo(1.65f, 1.65f, 0f, false, false, 7.18f, 19.73f)
                lineTo(7.12f, 19.79f)
                arcTo(2.0f, 2.0f, 0f, false, true, 4.29f, 16.96f)
                lineTo(4.35f, 16.9f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.68f, 15.08f)
                arcTo(1.65f, 1.65f, 0f, false, false, 3.17f, 14.08f)
                horizontalLineTo(3.0f)
                arcTo(2.0f, 2.0f, 0f, false, true, 3.0f, 10.08f)
                horizontalLineTo(3.09f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.6f, 9.0f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.27f, 7.18f)
                lineTo(4.21f, 7.12f)
                arcTo(2.0f, 2.0f, 0f, false, true, 7.04f, 4.29f)
                lineTo(7.1f, 4.35f)
                arcTo(1.65f, 1.65f, 0f, false, false, 8.92f, 4.68f)
                horizontalLineTo(9.0f)
                arcTo(1.65f, 1.65f, 0f, false, false, 10.0f, 3.17f)
                verticalLineTo(3.0f)
                arcTo(2.0f, 2.0f, 0f, false, true, 14.0f, 3.0f)
                verticalLineTo(3.09f)
                arcTo(1.65f, 1.65f, 0f, false, false, 15.0f, 4.6f)
                arcTo(1.65f, 1.65f, 0f, false, false, 16.82f, 4.27f)
                lineTo(16.88f, 4.21f)
                arcTo(2.0f, 2.0f, 0f, false, true, 19.71f, 7.04f)
                lineTo(19.65f, 7.1f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.32f, 8.92f)
                verticalLineTo(9.0f)
                arcTo(1.65f, 1.65f, 0f, false, false, 20.83f, 10.0f)
                horizontalLineTo(21.0f)
                arcTo(2.0f, 2.0f, 0f, false, true, 21.0f, 14.0f)
                horizontalLineTo(20.91f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.4f, 15.0f)
                close()
            }
        }
    }

    /** SF Symbols magnifyingglass. */
    val Search: ImageVector by lazy {
        icon("Search") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(16.8f, 10.8f)
                arcTo(6.0f, 6.0f, 0f, true, true, 4.8f, 10.8f)
                arcTo(6.0f, 6.0f, 0f, true, true, 16.8f, 10.8f)
                close()
                moveTo(15.3f, 15.3f)
                lineTo(19.6f, 19.6f)
            }
        }
    }

    /** SF Symbols arrow.up.arrow.down (clean iOS sort). */
    val Sort: ImageVector by lazy {
        icon("Sort") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.2f, 18.5f)
                verticalLineTo(5.5f)
                moveTo(5.2f, 8.5f)
                lineTo(8.2f, 5.5f)
                lineTo(11.2f, 8.5f)
                moveTo(15.8f, 5.5f)
                verticalLineTo(18.5f)
                moveTo(12.8f, 15.5f)
                lineTo(15.8f, 18.5f)
                lineTo(18.8f, 15.5f)
            }
        }
    }

    /** SF Symbols ellipsis (three refined dots). */
    val More: ImageVector by lazy {
        icon("More") {
            for (cx in listOf(6.2f, 12.0f, 17.8f)) {
                path(fill = strokeBrush) {
                    moveTo(cx + 1.35f, 12f)
                    arcTo(1.35f, 1.35f, 0f, true, true, cx - 1.35f, 12f)
                    arcTo(1.35f, 1.35f, 0f, true, true, cx + 1.35f, 12f)
                    close()
                }
            }
        }
    }

    /** SF Symbols info.circle (stroked, airy inside glass pill!). */
    val Info: ImageVector by lazy {
        icon("Info") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(20.5f, 12.0f)
                arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12.0f)
                arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12.0f)
                close()
                moveTo(12.0f, 11.2f)
                verticalLineTo(16.0f)
            }
            path(fill = strokeBrush) {
                moveTo(13.05f, 8.1f)
                arcTo(1.05f, 1.05f, 0f, true, true, 10.95f, 8.1f)
                arcTo(1.05f, 1.05f, 0f, true, true, 13.05f, 8.1f)
                close()
            }
        }
    }

    /** SF Symbols square.grid.2x2. */
    val Grid: ImageVector by lazy {
        icon("Grid") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                for ((x, y) in listOf(4.2f to 4.2f, 13.6f to 4.2f, 4.2f to 13.6f, 13.6f to 13.6f)) {
                    moveTo(x + 1.6f, y)
                    horizontalLineTo(x + 4.6f)
                    curveTo(x + 5.6f, y, x + 6.2f, y + 0.6f, x + 6.2f, y + 1.6f)
                    verticalLineTo(y + 4.6f)
                    curveTo(x + 6.2f, y + 5.6f, x + 5.6f, y + 6.2f, x + 4.6f, y + 6.2f)
                    horizontalLineTo(x + 1.6f)
                    curveTo(x + 0.6f, y + 6.2f, x, y + 5.6f, x, y + 4.6f)
                    verticalLineTo(y + 1.6f)
                    curveTo(x, y + 0.6f, x + 0.6f, y, x + 1.6f, y)
                    close()
                }
            }
        }
    }

    /** SF Symbols list.bullet. */
    val ListView: ImageVector by lazy {
        icon("ListView") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
            ) {
                for (y in listOf(6.8f, 12.0f, 17.2f)) {
                    moveTo(9.0f, y)
                    horizontalLineTo(19.5f)
                }
            }
            for (y in listOf(6.8f, 12.0f, 17.2f)) {
                path(fill = strokeBrush) {
                    moveTo(6.1f, y)
                    arcTo(1.1f, 1.1f, 0f, true, true, 3.9f, y)
                    arcTo(1.1f, 1.1f, 0f, true, true, 6.1f, y)
                    close()
                }
            }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2.8f, 12.0f)
                curveTo(4.9f, 7.8f, 8.2f, 5.8f, 12.0f, 5.8f)
                curveTo(15.8f, 5.8f, 19.1f, 7.8f, 21.2f, 12.0f)
                curveTo(19.1f, 16.2f, 15.8f, 18.2f, 12.0f, 18.2f)
                curveTo(8.2f, 18.2f, 4.9f, 16.2f, 2.8f, 12.0f)
                close()
                moveTo(14.8f, 12.0f)
                arcTo(2.8f, 2.8f, 0f, true, true, 9.2f, 12.0f)
                arcTo(2.8f, 2.8f, 0f, true, true, 14.8f, 12.0f)
                close()
            }
        }
    }

    val EyeSlash: ImageVector by lazy {
        icon("EyeSlash") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
                moveTo(10.6f, 6.0f)
                curveTo(11.1f, 5.9f, 11.5f, 5.8f, 12.0f, 5.8f)
                curveTo(15.8f, 5.8f, 19.1f, 7.8f, 21.2f, 12.0f)
                curveTo(20.3f, 13.7f, 19.2f, 15.1f, 17.8f, 16.1f)
                moveTo(14.1f, 17.9f)
                curveTo(13.4f, 18.1f, 12.7f, 18.2f, 12.0f, 18.2f)
                curveTo(8.2f, 18.2f, 4.9f, 16.2f, 2.8f, 12.0f)
                curveTo(3.8f, 10.0f, 5.2f, 8.4f, 6.9f, 7.3f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 7.0f)
                curveTo(3.5f, 5.7f, 4.5f, 4.8f, 5.8f, 4.8f)
                horizontalLineTo(9.3f)
                lineTo(11.2f, 7.0f)
                horizontalLineTo(18.2f)
                curveTo(19.5f, 7.0f, 20.5f, 8.0f, 20.5f, 9.3f)
                verticalLineTo(16.9f)
                curveTo(20.5f, 18.2f, 19.5f, 19.2f, 18.2f, 19.2f)
                horizontalLineTo(5.8f)
                curveTo(4.5f, 19.2f, 3.5f, 18.2f, 3.5f, 16.9f)
                close()
            }
        }
    }

    val FolderPlus: ImageVector by lazy {
        icon("FolderPlus") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 7.0f)
                curveTo(3.5f, 5.7f, 4.5f, 4.8f, 5.8f, 4.8f)
                horizontalLineTo(9.3f)
                lineTo(11.2f, 7.0f)
                horizontalLineTo(18.2f)
                curveTo(19.5f, 7.0f, 20.5f, 8.0f, 20.5f, 9.3f)
                verticalLineTo(16.9f)
                curveTo(20.5f, 18.2f, 19.5f, 19.2f, 18.2f, 19.2f)
                horizontalLineTo(5.8f)
                curveTo(4.5f, 19.2f, 3.5f, 18.2f, 3.5f, 16.9f)
                close()
                moveTo(12.0f, 10.6f)
                verticalLineTo(15.6f)
                moveTo(9.5f, 13.1f)
                horizontalLineTo(14.5f)
            }
        }
    }

    val FolderMinus: ImageVector by lazy {
        icon("FolderMinus") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 7.0f)
                curveTo(3.5f, 5.7f, 4.5f, 4.8f, 5.8f, 4.8f)
                horizontalLineTo(9.3f)
                lineTo(11.2f, 7.0f)
                horizontalLineTo(18.2f)
                curveTo(19.5f, 7.0f, 20.5f, 8.0f, 20.5f, 9.3f)
                verticalLineTo(16.9f)
                curveTo(20.5f, 18.2f, 19.5f, 19.2f, 18.2f, 19.2f)
                horizontalLineTo(5.8f)
                curveTo(4.5f, 19.2f, 3.5f, 18.2f, 3.5f, 16.9f)
                close()
                moveTo(9.5f, 13.1f)
                horizontalLineTo(14.5f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.4f, 12.0f)
                arcTo(7.4f, 7.4f, 0f, true, true, 17.2f, 6.8f)
                moveTo(17.5f, 4.0f)
                verticalLineTo(7.3f)
                horizontalLineTo(14.2f)
            }
        }
    }

    /** SF Symbols square.and.arrow.up (Apple Share). */
    val Share: ImageVector by lazy {
        icon("Share") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12.0f, 14.5f)
                verticalLineTo(4.0f)
                moveTo(8.5f, 7.5f)
                lineTo(12.0f, 4.0f)
                lineTo(15.5f, 7.5f)
                moveTo(7.5f, 10.5f)
                horizontalLineTo(6.2f)
                curveTo(5.1f, 10.5f, 4.2f, 11.4f, 4.2f, 12.5f)
                verticalLineTo(18.0f)
                curveTo(4.2f, 19.1f, 5.1f, 20.0f, 6.2f, 20.0f)
                horizontalLineTo(17.8f)
                curveTo(18.9f, 20.0f, 19.8f, 19.1f, 19.8f, 18.0f)
                verticalLineTo(12.5f)
                curveTo(19.8f, 11.4f, 18.9f, 10.5f, 17.8f, 10.5f)
                horizontalLineTo(16.5f)
            }
        }
    }

    val OpenIn: ImageVector by lazy {
        icon("OpenIn") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14.0f, 4.5f)
                horizontalLineTo(19.5f)
                verticalLineTo(10.0f)
                moveTo(19.2f, 4.8f)
                lineTo(11.0f, 13.0f)
                moveTo(16.5f, 13.5f)
                verticalLineTo(17.5f)
                curveTo(16.5f, 18.6f, 15.6f, 19.5f, 14.5f, 19.5f)
                horizontalLineTo(6.5f)
                curveTo(5.4f, 19.5f, 4.5f, 18.6f, 4.5f, 17.5f)
                verticalLineTo(9.5f)
                curveTo(4.5f, 8.4f, 5.4f, 7.5f, 6.5f, 7.5f)
                horizontalLineTo(10.5f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 9.5f)
                horizontalLineTo(17.5f)
                curveTo(18.6f, 9.5f, 19.5f, 10.4f, 19.5f, 11.5f)
                verticalLineTo(18.0f)
                curveTo(19.5f, 19.1f, 18.6f, 20.0f, 17.5f, 20.0f)
                horizontalLineTo(11.0f)
                curveTo(9.9f, 20.0f, 9.0f, 19.1f, 9.0f, 18.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(6.5f, 15.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 15.0f, 4.0f, 14.1f, 4.0f, 13.0f)
                verticalLineTo(6.0f)
                curveTo(4.0f, 4.9f, 4.9f, 4.0f, 6.0f, 4.0f)
                horizontalLineTo(13.0f)
                curveTo(14.1f, 4.0f, 15.0f, 4.9f, 15.0f, 6.0f)
                verticalLineTo(6.5f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 6.8f)
                horizontalLineTo(19.5f)
                moveTo(9.2f, 6.8f)
                verticalLineTo(5.0f)
                curveTo(9.2f, 4.3f, 9.8f, 3.8f, 10.5f, 3.8f)
                horizontalLineTo(13.5f)
                curveTo(14.2f, 3.8f, 14.8f, 4.3f, 14.8f, 5.0f)
                verticalLineTo(6.8f)
                moveTo(6.5f, 6.8f)
                lineTo(7.4f, 18.2f)
                curveTo(7.5f, 19.3f, 8.4f, 20.2f, 9.5f, 20.2f)
                horizontalLineTo(14.5f)
                curveTo(15.6f, 20.2f, 16.5f, 19.3f, 16.6f, 18.2f)
                lineTo(17.5f, 6.8f)
                moveTo(10.2f, 10.8f)
                verticalLineTo(16.2f)
                moveTo(13.8f, 10.8f)
                verticalLineTo(16.2f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12.0f, 5.2f)
                verticalLineTo(18.8f)
                moveTo(5.2f, 12.0f)
                horizontalLineTo(18.8f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 12.5f)
                lineTo(9.8f, 16.8f)
                lineTo(18.5f, 7.5f)
            }
        }
    }

    /** SF Symbols sparkles (Monet / Dynamic Color). */
    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12.0f, 3.5f)
                lineTo(13.8f, 9.2f)
                lineTo(19.5f, 11.0f)
                lineTo(13.8f, 12.8f)
                lineTo(12.0f, 18.5f)
                lineTo(10.2f, 12.8f)
                lineTo(4.5f, 11.0f)
                lineTo(10.2f, 9.2f)
                close()
                moveTo(18.2f, 15.8f)
                lineTo(18.9f, 17.8f)
                lineTo(20.9f, 18.5f)
                lineTo(18.9f, 19.2f)
                lineTo(18.2f, 21.2f)
                lineTo(17.5f, 19.2f)
                lineTo(15.5f, 18.5f)
                lineTo(17.5f, 17.8f)
                close()
            }
        }
    }

    /** Airy layered translucency rings for Blur setting (replaces heavy dotted BlurOn). */
    val BlurLayers: ImageVector by lazy {
        icon("BlurLayers") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15.5f, 10.0f)
                arcTo(5.5f, 5.5f, 0f, true, true, 4.5f, 10.0f)
                arcTo(5.5f, 5.5f, 0f, true, true, 15.5f, 10.0f)
                close()
                moveTo(19.5f, 14.0f)
                arcTo(5.5f, 5.5f, 0f, true, true, 8.5f, 14.0f)
                arcTo(5.5f, 5.5f, 0f, true, true, 19.5f, 14.0f)
                close()
            }
        }
    }

    /** Floating dock capsule icon (replaces boxy filled CallToAction). */
    val FloatingDock: ImageVector by lazy {
        icon("FloatingDock") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.0f, 4.5f)
                horizontalLineTo(18.0f)
                curveTo(19.4f, 4.5f, 20.5f, 5.6f, 20.5f, 7.0f)
                verticalLineTo(17.0f)
                curveTo(20.5f, 18.4f, 19.4f, 19.5f, 18.0f, 19.5f)
                horizontalLineTo(6.0f)
                curveTo(4.6f, 19.5f, 3.5f, 18.4f, 3.5f, 17.0f)
                verticalLineTo(7.0f)
                curveTo(3.5f, 5.6f, 4.6f, 4.5f, 6.0f, 4.5f)
                close()
                // Floating pill inside bottom
                moveTo(8.5f, 15.2f)
                horizontalLineTo(15.5f)
            }
        }
    }

    /** Crisp stroked droplet (SF Symbols: drop). */
    val LiquidDrop: ImageVector by lazy {
        icon("LiquidDrop") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12.0f, 3.6f)
                curveTo(12.0f, 3.6f, 6.0f, 10.2f, 6.0f, 14.4f)
                arcTo(6.0f, 6.0f, 0f, true, false, 18.0f, 14.4f)
                curveTo(18.0f, 10.2f, 12.0f, 3.6f, 12.0f, 3.6f)
                close()
                // Specular highlight arc inside drop
                moveTo(9.2f, 14.6f)
                arcTo(2.8f, 2.8f, 0f, false, false, 11.4f, 17.2f)
            }
        }
    }

    /** SF Symbols sun.max (Max brightness). */
    val Brightness: ImageVector by lazy {
        icon("Brightness") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15.8f, 12.0f)
                arcTo(3.8f, 3.8f, 0f, true, true, 8.2f, 12.0f)
                arcTo(3.8f, 3.8f, 0f, true, true, 15.8f, 12.0f)
                close()
                moveTo(12.0f, 3.2f); verticalLineTo(5.2f)
                moveTo(12.0f, 18.8f); verticalLineTo(20.8f)
                moveTo(3.2f, 12.0f); horizontalLineTo(5.2f)
                moveTo(18.8f, 12.0f); horizontalLineTo(20.8f)
                moveTo(5.8f, 5.8f); lineTo(7.2f, 7.2f)
                moveTo(16.8f, 16.8f); lineTo(18.2f, 18.2f)
                moveTo(18.2f, 5.8f); lineTo(16.8f, 7.2f)
                moveTo(7.2f, 16.8f); lineTo(5.8f, 18.2f)
            }
        }
    }

    /** SF Symbols clock.arrow.circlepath (Remember last album). */
    val History: ImageVector by lazy {
        icon("History") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12.0f)
                arcTo(7.5f, 7.5f, 0f, true, true, 6.8f, 17.4f)
                moveTo(4.5f, 7.8f)
                verticalLineTo(12.0f)
                horizontalLineTo(8.5f)
                moveTo(12.0f, 8.2f)
                verticalLineTo(12.2f)
                lineTo(14.8f, 13.8f)
            }
        }
    }

    /** Multi-select: Select All (checkmark.square). */
    val SelectAll: ImageVector by lazy {
        icon("SelectAll") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 4.2f)
                horizontalLineTo(17.8f)
                curveTo(19.0f, 4.2f, 19.8f, 5.0f, 19.8f, 6.2f)
                verticalLineTo(17.8f)
                curveTo(19.8f, 19.0f, 19.0f, 19.8f, 17.8f, 19.8f)
                horizontalLineTo(6.2f)
                curveTo(5.0f, 19.8f, 4.2f, 19.0f, 4.2f, 17.8f)
                verticalLineTo(6.2f)
                curveTo(4.2f, 5.0f, 5.0f, 4.2f, 6.2f, 4.2f)
                close()
                moveTo(8.2f, 12.3f)
                lineTo(10.8f, 14.9f)
                lineTo(15.8f, 9.3f)
            }
        }
    }

    /** Multi-select: Invert Selection (contrast / flip selection). */
    val InvertSelect: ImageVector by lazy {
        icon("InvertSelect") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.8f, 12.0f)
                arcTo(7.8f, 7.8f, 0f, true, true, 4.2f, 12.0f)
                arcTo(7.8f, 7.8f, 0f, true, true, 19.8f, 12.0f)
                close()
            }
            path(fill = strokeBrush, pathFillType = PathFillType.NonZero) {
                moveTo(12.0f, 4.2f)
                arcTo(7.8f, 7.8f, 0f, false, true, 12.0f, 19.8f)
                close()
            }
        }
    }

    val ParentFolder: ImageVector by lazy {
        icon("ParentFolder") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.0f, 14.0f)
                lineTo(4.5f, 9.5f)
                lineTo(9.0f, 5.0f)
                moveTo(5.0f, 9.5f)
                horizontalLineTo(14.5f)
                curveTo(17.5f, 9.5f, 19.5f, 11.5f, 19.5f, 14.5f)
                verticalLineTo(19.0f)
            }
        }
    }

    /** Crisp Apple SF-Symbols arrow.up for floating Scroll-to-Top Liquid Glass button. */
    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2.05f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12.0f, 18.8f)
                verticalLineTo(5.6f)
                moveTo(6.6f, 11.0f)
                lineTo(12.0f, 5.6f)
                lineTo(17.4f, 11.0f)
            }
        }
    }
}
