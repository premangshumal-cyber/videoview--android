package com.kinepulse.player.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPrefs(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(v) = prefs.edit().putBoolean("onboarding_completed", v).apply()

    var appTheme: String
        get() = prefs.getString("app_theme", "system") ?: "system"
        set(v) = prefs.edit().putString("app_theme", v).apply()

    var incognitoMode: Boolean
        get() = prefs.getBoolean("incognito_mode", false)
        set(v) = prefs.edit().putBoolean("incognito_mode", v).apply()

    val persistentIncognito: Boolean
        get() = prefs.getBoolean("persistent_incognito", true)

    var videoGridMode: Boolean
        get() = prefs.getBoolean("video_grid_mode", true)
        set(v) = prefs.edit().putBoolean("video_grid_mode", v).apply()

    var sortBy: String
        get() = prefs.getString("video_sort_by", "NAME") ?: "NAME"
        set(v) = prefs.edit().putString("video_sort_by", v).apply()

    var sortAsc: Boolean
        get() = prefs.getBoolean("video_sort_asc", true)
        set(v) = prefs.edit().putBoolean("video_sort_asc", v).apply()

    val showHeaders: Boolean get() = prefs.getBoolean("show_headers", true)
    val foldersFirst: Boolean get() = prefs.getBoolean("folders_first", true)
    val showHiddenFiles: Boolean get() = prefs.getBoolean("browser_show_hidden_files", false)
    val mediaSeen: Boolean get() = prefs.getBoolean("media_seen", true)
    val playlistModeVideo: Boolean get() = prefs.getBoolean("playlist_mode_video", false)
    val showVideoThumbnails: Boolean get() = prefs.getBoolean("show_video_thumbnails", true)
    val alwaysFastSeek: Boolean get() = prefs.getBoolean("always_fast_seek", false)
    val videoMatchFrameRate: Boolean get() = prefs.getBoolean("video_match_frame_rate", false)
    val audioBoost: Boolean get() = prefs.getBoolean("audio_boost", true)
    val videoConfirmResume: String get() = prefs.getString("video_confirm_resume", "ask") ?: "ask"
    val videoActionSwitch: String get() = prefs.getString("video_action_switch", "pip") ?: "pip"

    val enableBrightnessGesture: Boolean get() = prefs.getBoolean("enable_brightness_gesture", true)
    val saveBrightness: Boolean get() = prefs.getBoolean("save_brightness", false)
    var savedBrightnessValue: Float
        get() = prefs.getFloat("saved_brightness_value", -1f)
        set(v) = prefs.edit().putFloat("saved_brightness_value", v).apply()

    val enableVolumeGesture: Boolean get() = prefs.getBoolean("enable_volume_gesture", true)
    val enableSwipeSeek: Boolean get() = prefs.getBoolean("enable_swipe_seek", true)
    val enableScaleGesture: Boolean get() = prefs.getBoolean("enable_scale_gesture", true)
    val enableDoubleTapSeek: Boolean get() = prefs.getBoolean("enable_double_tap_seek", true)
    val enableDoubleTapPlay: Boolean get() = prefs.getBoolean("enable_double_tap_play", true)
    val enableFastplay: Boolean get() = prefs.getBoolean("enable_fastplay", false)
    val fastplaySpeed: Float get() = (prefs.getString("fastplay_speed", "20")?.toFloatOrNull() ?: 20f) / 10f
    val screenshotMode: String get() = prefs.getString("screenshot_mode", "button") ?: "button"

    val enableSeekButtons: Boolean get() = prefs.getBoolean("enable_seek_buttons", false)
    val jumpDelaySec: Int get() = prefs.getString("jump_delay", "10")?.toIntOrNull() ?: 10
    val longJumpDelaySec: Int get() = prefs.getString("long_jump_delay", "20")?.toIntOrNull() ?: 20
    val hudTimeoutSec: Int get() = prefs.getString("video_hud_timeout_in_s", "4")?.toIntOrNull() ?: 4
    val videoTransitionShow: Boolean get() = prefs.getBoolean("video_transition_show", true)
    val lockUseSensor: Boolean get() = prefs.getBoolean("lock_use_sensor", true)

    val subtitlesAutoload: Boolean get() = prefs.getBoolean("subtitles_autoload", true)
    val subtitleTextEncoding: String get() = prefs.getString("subtitle_text_encoding", "UTF-8") ?: "UTF-8"
    val subtitlesSizeSp: Float get() = (prefs.getString("subtitles_size", "16")?.toFloatOrNull() ?: 16f)
    val subtitlesBold: Boolean get() = prefs.getBoolean("subtitles_bold", false)
    val subtitlesColor: Int get() = prefs.getInt("subtitles_color", 0xFFFFFF)
    val subtitlesColorOpacity: Int get() = prefs.getInt("subtitles_color_opacity", 255)
    val subtitlesBackground: Boolean get() = prefs.getBoolean("subtitles_background", false)
    val subtitlesShadow: Boolean get() = prefs.getBoolean("subtitles_shadow", true)
    val subtitlesOutline: Boolean get() = prefs.getBoolean("subtitles_outline", true)

    var globalSubtitleDelayMs: Long
        get() = prefs.getLong("global_subtitle_delay_ms", 0L)
        set(v) = prefs.edit().putLong("global_subtitle_delay_ms", v).apply()

    var globalPlaybackSpeed: Float
        get() = prefs.getFloat("global_playback_speed", 1.0f)
        set(v) = prefs.edit().putFloat("global_playback_speed", v).apply()

    val meteredConnectionPolicy: String
        get() = prefs.getString("metered_connection", "ask") ?: "ask"

    var lastQueueUrisCsv: String
        get() = prefs.getString("last_queue_uris", "") ?: ""
        set(v) = prefs.edit().putString("last_queue_uris", v).apply()
}
