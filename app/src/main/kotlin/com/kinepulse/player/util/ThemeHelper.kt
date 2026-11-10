package com.kinepulse.player.util

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import com.kinepulse.player.R

object ThemeHelper {
    fun applyNightMode(themePref: String) {
        when (themePref) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark", "black" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applyActivityOverlay(activity: Activity, themePref: String) {
        if (themePref == "black") {
            activity.setTheme(R.style.Theme_KinePulse_Black)
        }
    }
}
