package com.kinepulse.player.ui.audio

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.data.media.AudioItem
import com.kinepulse.player.service.AudioPlaybackService
import com.kinepulse.player.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * VLC-inspired four-section audio catalog backed by existing MediaStore queries.
 * Genres are displayed from indexed metadata; the current repository defaults
 * unspecified genres to "General".
 */
class AudioFragment : Fragment() {
    private data class CatalogRow(
        val label: String,
        val tracks: List<AudioItem>
    )

    private lateinit var tabs: TabLayout
    private lateinit var list: ListView
    private lateinit var playAll: Button
    private lateinit var shuffleAll: Button
    private var tracks: List<AudioItem> = emptyList()
    private var rows: List<CatalogRow> = emptyList()
    private var selectedTab = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabs = TabLayout(context)
        arrayOf("Artists", "Albums", "Tracks", "Genres").forEach { title ->
            tabs.addTab(tabs.newTab().setText(title))
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        playAll = Button(context).apply { text = "Play all" }
        shuffleAll = Button(context).apply { text = "Shuffle all" }
        actions.addView(playAll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(shuffleAll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        list = ListView(context)
        root.addView(tabs)
        root.addView(actions)
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as KinePulseApp
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                render()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
        playAll.setOnClickListener { playQueue(tracks) }
        shuffleAll.setOnClickListener { playQueue(tracks.shuffled()) }
        list.setOnItemClickListener { _, _, pos, _ ->
            val row = rows.getOrNull(pos) ?: return@setOnItemClickListener
            if (selectedTab == 2 || row.tracks.size == 1) {
                playQueue(row.tracks)
            } else {
                val options = row.tracks.map { "${it.title} — ${it.artist}" }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(row.label)
                    .setPositiveButton("Play all") { _, _ -> playQueue(row.tracks) }
                    .setItems(options) { _, selected ->
                        playQueue(row.tracks.drop(selected))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.audios.collect { current ->
                tracks = current
                render()
            }
        }
    }

    private fun render() {
        rows = when (selectedTab) {
            0 -> tracks.groupBy { it.artist.ifBlank { "Unknown artist" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (artist, songs) ->
                    CatalogRow("$artist  ·  ${songs.size} tracks", songs)
                }
            1 -> tracks.groupBy { it.album.ifBlank { "Unknown album" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (album, songs) ->
                    CatalogRow("$album  ·  ${songs.first().artist}", songs)
                }
            2 -> tracks.sortedBy { it.title.lowercase() }.map { item ->
                CatalogRow("${item.title}  ·  ${item.artist}  ·  ${TimeUtils.formatMs(item.durationMs)}", listOf(item))
            }
            else -> tracks.groupBy { it.genre.ifBlank { "General" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (genre, songs) ->
                    CatalogRow("$genre  ·  ${songs.size} tracks", songs)
                }
        }
        list.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            if (rows.isEmpty()) listOf("No audio found — grant media access") else rows.map { it.label }
        )
        playAll.isEnabled = tracks.isNotEmpty()
        shuffleAll.isEnabled = tracks.isNotEmpty()
    }

    private fun playQueue(items: List<AudioItem>) {
        if (items.isEmpty()) return
        val first = items.first()
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), AudioPlaybackService::class.java).apply {
                action = AudioPlaybackService.ACTION_START_AUDIO
                data = first.uri
                putExtra(AudioPlaybackService.EXTRA_TITLE, first.title)
                putStringArrayListExtra(
                    AudioPlaybackService.EXTRA_QUEUE_URIS,
                    ArrayList(items.map { it.uri.toString() })
                )
                putStringArrayListExtra(
                    AudioPlaybackService.EXTRA_QUEUE_TITLES,
                    ArrayList(items.map { it.title })
                )
            }
        )
    }
}
