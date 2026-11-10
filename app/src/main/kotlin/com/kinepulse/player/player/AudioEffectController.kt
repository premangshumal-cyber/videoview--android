package com.kinepulse.player.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

class AudioEffectController {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    var boostPercent: Int = 100
        private set

    fun attachSession(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = false }
        } catch (_: Throwable) {}
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = false }
        } catch (_: Throwable) {}
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(100, 200)
        val enhancer = loudnessEnhancer ?: return
        try {
            if (boostPercent > 100) {
                val gainMb = ((boostPercent - 100) * 6)
                enhancer.setTargetGain(gainMb)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
        } catch (_: Throwable) {}
    }

    fun getEqualizer(): Equalizer? = equalizer

    fun release() {
        try { loudnessEnhancer?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        loudnessEnhancer = null
        equalizer = null
    }
}
