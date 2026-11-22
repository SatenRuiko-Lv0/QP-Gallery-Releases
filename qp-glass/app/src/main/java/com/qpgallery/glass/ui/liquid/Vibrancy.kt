// Adapted from Kyant0/AndroidLiquidGlass and KernelSU v3.3.1 (Apache 2.0).

package com.qpgallery.glass.ui.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1.5f,
) {
    colorControls(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
    )
}
