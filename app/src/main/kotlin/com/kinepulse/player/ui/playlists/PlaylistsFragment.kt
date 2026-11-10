package com.kinepulse.player.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.data.db.Playlist
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val app = requireActivity().application as KinePulseApp
        val dao = app.database.playbackDao()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val btnCreate = Button(requireContext()).apply {
            text = getString(R.string.create_playlist)
            setOnClickListener {
                val input = EditText(requireContext())
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.create_playlist)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            lifecycleScope.launch { dao.createPlaylist(Playlist(name = name)) }
                        }
                    }
                    .show()
            }
        }
        val listView = ListView(requireContext())
        root.addView(btnCreate)
        root.addView(listView)

        viewLifecycleOwner.lifecycleScope.launch {
            dao.observePlaylists().collect { lists ->
                listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, lists.map { it.name })
            }
        }
        return root
    }
}
