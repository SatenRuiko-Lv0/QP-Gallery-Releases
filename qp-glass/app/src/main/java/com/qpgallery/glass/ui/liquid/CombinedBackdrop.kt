// Adapted from Kyant0/AndroidLiquidGlass and KernelSU v3.3.1 (Apache 2.0).

package com.qpgallery.glass.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

@Stable
class ColorBackdrop(val color: Color) : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        drawRect(color)
    }
}

@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Stable
class TransformedBackdrop(
    val wrapped: Backdrop,
    val onDraw: DrawScope.(drawWrapped: DrawScope.() -> Unit) -> Unit,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = wrapped.isCoordinatesDependent
    override val offsetResidualX: Float get() = wrapped.offsetResidualX
    override val offsetResidualY: Float get() = wrapped.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        onDraw {
            with(wrapped) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }

@Composable
fun rememberTransformedBackdrop(
    backdrop: Backdrop,
    onDraw: DrawScope.(drawWrapped: DrawScope.() -> Unit) -> Unit,
): Backdrop = remember(backdrop, onDraw) { TransformedBackdrop(backdrop, onDraw) }
