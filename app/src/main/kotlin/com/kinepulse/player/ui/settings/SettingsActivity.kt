package com.kinepulse.player.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.kinepulse.player.R
import com.kinepulse.player.util.ThemeHelper

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, RootPreferenceFragment())
                .commit()
        }
    }

    class RootPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_root, rootKey)
            findPreference<androidx.preference.ListPreference>("app_theme")?.setOnPreferenceChangeListener { _, newValue ->
                ThemeHelper.applyNightMode(newValue.toString())
                activity?.recreate()
                true
            }
        }
    }
}
