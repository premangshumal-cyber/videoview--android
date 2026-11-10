package com.kinepulse.player.ui.more

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kinepulse.player.BuildConfig
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.ui.player.VideoPlayerActivity
import com.kinepulse.player.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val app = requireActivity().application as KinePulseApp
        val dao = app.database.playbackDao()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvAbout = TextView(requireContext()).apply {
            text = "KinePulse v${BuildConfig.VERSION_NAME} • VideoView Engine"
        }
        val btnSettings = Button(requireContext()).apply {
            text = getString(R.string.settings)
            setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        }
        val btnClearHistory = Button(requireContext()).apply {
            text = getString(R.string.clear_playback_history)
            setOnClickListener { lifecycleScope.launch { dao.clearHistory() } }
        }
        val btnLicenses = Button(requireContext()).apply {
            text = getString(R.string.open_source_licenses)
            setOnClickListener { startActivity(Intent(requireContext(), LicensesActivity::class.java)) }
        }
        val lvHistory = ListView(requireContext())

        root.addView(tvAbout)
        root.addView(btnSettings)
        root.addView(btnClearHistory)
        root.addView(btnLicenses)
        root.addView(lvHistory)

        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeHistory().collect { entries ->
                lvHistory.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    entries.map { "[${it.type}] ${it.title}" }
                )
                lvHistory.setOnItemClickListener { _, _, idx, _ ->
                    val entry = entries[idx]
                    startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(entry.uri)
                        putExtra(VideoPlayerActivity.EXTRA_TITLE, entry.title)
                    })
                }
            }
        }
        return root
    }
}
