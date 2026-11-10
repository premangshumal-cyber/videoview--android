package com.kinepulse.player.ui.audio

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.service.AudioPlaybackService
import com.kinepulse.player.util.TimeUtils
import kotlinx.coroutines.launch

class AudioFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val listView = ListView(requireContext())
        val app = requireActivity().application as KinePulseApp

        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.audios.collect { tracks ->
                val labels = tracks.map { "${it.title} — ${it.artist} (${TimeUtils.formatMs(it.durationMs)})" }
                listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                listView.setOnItemClickListener { _, _, pos, _ ->
                    val track = tracks[pos]
                    ContextCompat.startForegroundService(
                        requireContext(),
                        Intent(requireContext(), AudioPlaybackService::class.java).apply {
                            action = AudioPlaybackService.ACTION_START_AUDIO
                            data = track.uri
                            putExtra(AudioPlaybackService.EXTRA_TITLE, track.title)
                        }
                    )
                }
            }
        }
        return listView
    }
}
