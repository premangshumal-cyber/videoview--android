package com.kinepulse.player.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.data.db.SavedStream
import com.kinepulse.player.databinding.ActivityMainBinding
import com.kinepulse.player.ui.audio.AudioFragment
import com.kinepulse.player.ui.browse.BrowseFragment
import com.kinepulse.player.ui.more.MoreFragment
import com.kinepulse.player.ui.onboarding.OnboardingActivity
import com.kinepulse.player.ui.player.VideoPlayerActivity
import com.kinepulse.player.ui.playlists.PlaylistsFragment
import com.kinepulse.player.ui.settings.SettingsActivity
import com.kinepulse.player.ui.video.VideoFragment
import com.kinepulse.player.util.ThemeHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { application as KinePulseApp }
    private var currentSearchListener: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeHelper.applyActivityOverlay(this, app.prefs.appTheme)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (!app.prefs.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        setupBottomNav()
        if (savedInstanceState == null) {
            handleShortcutOrDefault(intent)
        }
        lifecycleScope.launch { app.repository.refreshLibrary() }
    }

    private fun handleShortcutOrDefault(intent: Intent?) {
        when (intent?.getStringExtra("shortcut_target")) {
            "resume" -> resumeLastQueue()
            "browse" -> binding.bottomNav.selectedItemId = R.id.nav_browse
            "audio" -> binding.bottomNav.selectedItemId = R.id.nav_audio
            else -> switchFragment(VideoFragment())
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_video -> VideoFragment()
                R.id.nav_audio -> AudioFragment()
                R.id.nav_browse -> BrowseFragment()
                R.id.nav_playlists -> PlaylistsFragment()
                R.id.nav_more -> MoreFragment()
                else -> VideoFragment()
            }
            switchFragment(fragment)
            true
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun setSearchQueryListener(listener: ((String) -> Unit)?) {
        currentSearchListener = listener
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_overflow_menu, menu)
        menu.findItem(R.id.action_incognito)?.isChecked = app.prefs.incognitoMode

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchListener?.invoke(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_grid -> {
                app.prefs.videoGridMode = !app.prefs.videoGridMode
                (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? VideoFragment)?.refreshLayoutMode()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_refresh -> {
                lifecycleScope.launch { app.repository.refreshLibrary() }
                true
            }
            R.id.action_resume_queue -> {
                resumeLastQueue()
                true
            }
            R.id.action_open_stream -> {
                showOpenStreamDialog()
                true
            }
            R.id.action_incognito -> {
                val next = !item.isChecked
                item.isChecked = next
                app.prefs.incognitoMode = next
                supportActionBar?.subtitle = if (next) getString(R.string.incognito_active) else null
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Name", "File name", "Length", "Date modified", "Recently added")
        val keys = arrayOf("NAME", "FILENAME", "LENGTH", "DATE", "ADDED")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setItems(options) { _, which ->
                val chosen = keys[which]
                if (app.prefs.sortBy == chosen) {
                    app.prefs.sortAsc = !app.prefs.sortAsc
                } else {
                    app.prefs.sortBy = chosen
                    app.prefs.sortAsc = true
                }
                (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? VideoFragment)?.reloadSorted()
            }
            .show()
    }

    private fun resumeLastQueue() {
        val csv = app.prefs.lastQueueUrisCsv
        if (csv.isBlank()) return
        val uris = ArrayList(csv.split("|").filter { it.isNotBlank() })
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_QUEUE_URIS, uris)
        })
    }

    fun showOpenStreamDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_open_stream, null)
        val etUrl = view.findViewById<android.widget.EditText>(R.id.etStreamUrl)
        val cbSave = view.findViewById<android.widget.CheckBox>(R.id.cbSaveStream)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.open_network_stream)
            .setView(view)
            .setPositiveButton(R.string.play) { _, _ ->
                val url = etUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (cbSave.isChecked) {
                        lifecycleScope.launch {
                            app.database.playbackDao().saveStream(SavedStream(url = url, title = url.substringAfterLast('/')))
                        }
                    }
                    startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(url)
                    })
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
