// Adapted from KernelSU v3.3.1 (Apache 2.0), tuned for iOS 26 Clear Liquid Glass optics
// and single-subscription lifecycle-aware gravity lighting for minimal battery draw.

package com.qpgallery.glass.ui.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val IosLiquidSpecular: Highlight = Highlight(
    width = 1.1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.16f),
        innerBlurRadius = 1.35.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.50f,
        ),
        dualPeak = true,
    ),
)

val IosClearSpecular: Highlight = Highlight(
    width = 1.25.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.22f),
        innerBlurRadius = 1.1.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.35f, -0.04f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.85f, -0.45f),
            color = Color.White,
            intensity = 0.58f,
        ),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.015f
private const val DEFAULT_GRAVITY_ANGLE = (-PI / 2).toFloat()

// 6-degree quantization keeps optical specular shifts buttery while avoiding 120Hz micro-jitter redraws & battery drain.
private const val GRAVITY_ANGLE_STEP_RAD = (6.0 * PI / 180.0).toFloat()

private val DefaultGravityAngleState: State<Float> = mutableFloatStateOf(DEFAULT_GRAVITY_ANGLE)

/**
 * Shared quantized gravity light angle state. Reading `.value` only invalidates the draw phase
 * (never recomposing the Compose tree!) and uses ONE single sensor listener for the entire app.
 */
val LocalGravityAngle = staticCompositionLocalOf<State<Float>> { DefaultGravityAngleState }

@Composable
fun ProvideSharedGravityLighting(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val sensorActive = enabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val angleState: State<Float> = if (sensorActive) {
        rememberSingleQuantizedGravityAngle()
    } else {
        DefaultGravityAngleState
    }
    CompositionLocalProvider(LocalGravityAngle provides angleState, content = content)
}

@Composable
private fun rememberSingleQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val magSq = tilt.gravityX * tilt.gravityX + tilt.gravityY * tilt.gravityY
            if (magSq > GRAVITY_DIR_THRESHOLD_SQ) {
                (atan2(tilt.gravityY, tilt.gravityX) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
            } else {
                DEFAULT_GRAVITY_ANGLE
            }
        }
    }
}

@Composable
fun rememberGravityRotatedHighlight(
    base: Highlight = IosLiquidSpecular,
    extraDegrees: Float = -45f,
): State<Highlight> {
    val baseStyle = base.style as BloomStroke
    val angle = LocalGravityAngle.current
    return remember(angle, base, extraDegrees) {
        derivedStateOf {
            val basePrimary = baseStyle.primaryLight
            val rad = angle.value + (extraDegrees * PI / 180.0).toFloat()
            base.copy(
                style = baseStyle.copy(
                    primaryLight = basePrimary.copy(
                        position = LightPosition(
                            x = LIGHT_REF_X + cos(rad),
                            y = LIGHT_REF_Y + sin(rad),
                            z = basePrimary.position.z,
                        ),
                    ),
                ),
            )
        }
    }
}

@Composable
fun rememberSurfaceBackdrop(
    enableBlur: Boolean,
    fillColor: Color = MiuixTheme.colorScheme.surface,
): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    return rememberLayerBackdrop {
        drawRect(fillColor)
        drawContent()
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    val r = 18.dp.toPx()
                    blur(r, r)
                },
                onDrawSurface = { drawRect(surface.copy(alpha = 0.78f)) },
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
