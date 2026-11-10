package com.kinepulse.player.util

import java.util.Locale

object TimeUtils {
    fun formatMs(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun parseHhMmSsToMs(input: String): Long? {
        val parts = input.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> null
        }
    }
}
