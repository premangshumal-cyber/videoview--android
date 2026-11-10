package com.kinepulse.player.player.subtitle

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class SubtitleStyleConfig(
    val textSizeSp: Float = 16f,
    val isBold: Boolean = false,
    val textColorRgb: Int = 0xFFFFFF,
    val textOpacity: Int = 255,
    val hasBackground: Boolean = false,
    val bgColorRgb: Int = 0x000000,
    val bgOpacity: Int = 255,
    val hasShadow: Boolean = true,
    val shadowColorRgb: Int = 0x000000,
    val shadowOpacity: Int = 128,
    val hasOutline: Boolean = true,
    val outlineSizePx: Float = 4f,
    val outlineColorRgb: Int = 0x000000,
    val outlineOpacity: Int = 255
)
