package com.kinepulse.player.ui.browse

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.data.db.CustomDirectory
import com.kinepulse.player.ui.main.MainActivity
import com.kinepulse.player.ui.player.VideoPlayerActivity
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {

    private val app by lazy { requireActivity().application as KinePulseApp }

    private val safDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            lifecycleScope.launch {
                app.database.playbackDao().addCustomDirectory(CustomDirectory(treeUri.toString()))
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val btnAddFolder = Button(requireContext()).apply {
            text = getString(R.string.add_custom_directory_saf)
            setOnClickListener { safDirectoryLauncher.launch(null) }
        }
        val btnOpenStream = Button(requireContext()).apply {
            text = getString(R.string.open_network_stream)
            setOnClickListener { (activity as? MainActivity)?.showOpenStreamDialog() }
        }
        val listView = ListView(requireContext())

        root.addView(btnAddFolder)
        root.addView(btnOpenStream)
        root.addView(listView)

        viewLifecycleOwner.lifecycleScope.launch {
            app.database.playbackDao().observeCustomDirectories().collect { dirs ->
                val files = mutableListOf<Pair<String, Uri>>()
                dirs.forEach { d ->
                    val tree = DocumentFile.fromTreeUri(requireContext(), Uri.parse(d.path))
                    tree?.listFiles()?.forEach { doc ->
                        if (doc.isFile && (doc.type?.startsWith("video/") == true || doc.type?.startsWith("audio/") == true)) {
                            files.add((doc.name ?: doc.uri.lastPathSegment ?: "File") to doc.uri)
                        }
                    }
                }
                listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files.map { it.first })
                listView.setOnItemClickListener { _, _, idx, _ ->
                    val target = files[idx]
                    startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = target.second
                        putExtra(VideoPlayerActivity.EXTRA_TITLE, target.first)
                    })
                }
            }
        }
        return root
    }
}
