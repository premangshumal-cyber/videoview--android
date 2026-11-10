package com.kinepulse.player.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.data.media.VideoItem
import com.kinepulse.player.databinding.FragmentVideoBinding
import com.kinepulse.player.databinding.ItemVideoCardBinding
import com.kinepulse.player.service.AudioPlaybackService
import com.kinepulse.player.ui.main.MainActivity
import com.kinepulse.player.ui.player.VideoPlayerActivity
import com.kinepulse.player.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!
    private val app by lazy { requireActivity().application as KinePulseApp }
    private val searchQuery = MutableStateFlow("")
    private val sortTrigger = MutableStateFlow(0)
    private lateinit var adapter: VideoListAdapter
    private var actionMode: ActionMode? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = VideoListAdapter(
            onPlayClick = { clicked, all -> launchVideoPlayback(clicked, all) },
            onFavoriteToggle = { video -> lifecycleScope.launch { app.repository.toggleFavorite(video) } },
            onSelectionChanged = { count -> updateSelectionActionMode(count) }
        )

        refreshLayoutMode()
        binding.rvVideos.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                app.repository.refreshLibrary()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        (activity as? MainActivity)?.setSearchQueryListener { q -> searchQuery.value = q }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(app.repository.videos, searchQuery, sortTrigger) { list, q, _ ->
                val filtered = if (q.isBlank()) list else list.filter {
                    it.title.contains(q, ignoreCase = true) || it.fileName.contains(q, ignoreCase = true)
                }
                sortVideos(filtered)
            }.collect { sorted ->
                adapter.submitList(sorted)
                binding.tvEmptyVideos.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    fun refreshLayoutMode() {
        val isGrid = app.prefs.videoGridMode
        val widthDp = resources.configuration.screenWidthDp
        val spanCount = when {
            widthDp >= 840 -> 4
            widthDp >= 600 -> 3
            else -> 2
        }
        binding.rvVideos.layoutManager = if (isGrid) {
            GridLayoutManager(requireContext(), spanCount)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    fun reloadSorted() {
        sortTrigger.value += 1
    }

    private fun sortVideos(list: List<VideoItem>): List<VideoItem> {
        val asc = app.prefs.sortAsc
        val sorted = when (app.prefs.sortBy) {
            "FILENAME" -> list.sortedBy { it.fileName.lowercase() }
            "LENGTH" -> list.sortedBy { it.durationMs }
            "DATE" -> list.sortedBy { it.dateModifiedSec }
            "ADDED" -> list.sortedBy { it.dateAddedSec }
            else -> list.sortedBy { it.title.lowercase() }
        }
        return if (asc) sorted else sorted.reversed()
    }

    private fun launchVideoPlayback(clicked: VideoItem, allVideos: List<VideoItem>) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java)
        if (app.prefs.playlistModeVideo) {
            val uris = ArrayList(allVideos.map { it.uri.toString() })
            val titles = ArrayList(allVideos.map { it.title })
            val startIndex = allVideos.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
            intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_QUEUE_URIS, uris)
            intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_QUEUE_TITLES, titles)
            intent.putExtra(VideoPlayerActivity.EXTRA_START_INDEX, startIndex)
        } else {
            intent.action = Intent.ACTION_VIEW
            intent.data = clicked.uri
            intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, clicked.title)
        }
        startActivity(intent)
    }

    private fun updateSelectionActionMode(selectedCount: Int) {
        if (selectedCount == 0) {
            actionMode?.finish()
            actionMode = null
            return
        }
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    mode.menuInflater.inflate(R.menu.video_action_mode, menu)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val selected = adapter.getSelectedItems()
                    when (item.itemId) {
                        R.id.action_play_selected -> {
                            if (selected.isNotEmpty()) launchVideoPlayback(selected.first(), selected)
                            mode.finish()
                        }
                        R.id.action_info_selected -> {
                            selected.firstOrNull()?.let { v ->
                                startActivity(Intent(requireContext(), MediaInfoActivity::class.java).apply {
                                    data = v.uri
                                    putExtra(VideoPlayerActivity.EXTRA_TITLE, v.title)
                                })
                            }
                            mode.finish()
                        }
                        R.id.action_play_as_audio -> {
                            selected.firstOrNull()?.let { v ->
                                ContextCompat.startForegroundService(
                                    requireContext(),
                                    Intent(requireContext(), AudioPlaybackService::class.java).apply {
                                        action = AudioPlaybackService.ACTION_START_AUDIO
                                        data = v.uri
                                        putExtra(AudioPlaybackService.EXTRA_TITLE, v.title)
                                    }
                                )
                            }
                            mode.finish()
                        }
                    }
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) {
                    adapter.clearSelection()
                    actionMode = null
                }
            })
        }
        actionMode?.title = "$selectedCount selected"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class VideoListAdapter(
    private val onPlayClick: (VideoItem, List<VideoItem>) -> Unit,
    private val onFavoriteToggle: (VideoItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<VideoListAdapter.VH>() {

    private var items: List<VideoItem> = emptyList()
    private val selectedIds = linkedSetOf<Long>()

    class VH(val binding: ItemVideoCardBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    fun submitList(newList: List<VideoItem>) {
        items = newList
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<VideoItem> = items.filter { selectedIds.contains(it.id) }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvTitle.text = item.title
            tvDurationBadge.text = TimeUtils.formatMs(item.durationMs)
            tvResolutionBadge.text = item.resolutionBadge
            tvResolutionBadge.visibility = if (item.resolutionBadge.isNotEmpty()) View.VISIBLE else View.GONE
            ivSeenMarker.visibility = if (item.seen) View.VISIBLE else View.GONE
            btnFavorite.setImageResource(
                if (item.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
            )
            cbSelected.visibility = if (selectedIds.isNotEmpty()) View.VISIBLE else View.GONE
            cbSelected.isChecked = selectedIds.contains(item.id)

            if (item.durationMs > 0 && item.resumePositionMs > 0) {
                progressResume.visibility = View.VISIBLE
                progressResume.max = 100
                progressResume.progress = ((item.resumePositionMs * 100) / item.durationMs).toInt()
            } else {
                progressResume.visibility = View.GONE
            }

            ivThumbnail.load(item.uri) {
                placeholder(android.R.drawable.ic_media_play)
            }

            btnFavorite.setOnClickListener { onFavoriteToggle(item) }
            root.setOnClickListener {
                if (selectedIds.isNotEmpty()) {
                    toggleSelect(item.id)
                } else {
                    onPlayClick(item, items)
                }
            }
            root.setOnLongClickListener {
                toggleSelect(item.id)
                true
            }
        }
    }

    private fun toggleSelect(id: Long) {
        if (!selectedIds.add(id)) selectedIds.remove(id)
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }
}
