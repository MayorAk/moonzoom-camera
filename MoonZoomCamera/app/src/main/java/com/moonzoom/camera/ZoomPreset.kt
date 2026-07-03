package com.moonzoom.camera

/**
 * The zoom presets requested for this app. "ratio" is the CameraX/Camera2 zoom ratio
 * that will be requested via CameraControl.setZoomRatio(). Values under 1.0 select the
 * ultra-wide lens on devices with a logical multi-camera (S21+ and most modern flagships).
 *
 * NOTE: The actual max ratio a device can hit is hardware-dependent. We clamp every
 * preset to [CameraInfo.getZoomState().value.minZoomRatio, ...maxZoomRatio] at runtime,
 * so on a phone that tops out at 15x, tapping "30x" will just land on that device's max.
 */
data class ZoomPreset(val label: String, val ratio: Float)

val ZOOM_PRESETS = listOf(
    ZoomPreset("UW", 0.6f),   // Ultra-wide
    ZoomPreset("1x", 1f),
    ZoomPreset("2x", 2f),
    ZoomPreset("3x", 3f),
    ZoomPreset("5x", 5f),
    ZoomPreset("10x", 10f),
    ZoomPreset("30x", 30f),
    ZoomPreset("50x", 50f),
    ZoomPreset("100x", 100f)
)

// Ratio above which we consider ourselves in "extreme digital zoom" territory,
// where Moon Mode's extra sharpening kicks in.
const val MOON_MODE_MIN_RATIO = 10f
