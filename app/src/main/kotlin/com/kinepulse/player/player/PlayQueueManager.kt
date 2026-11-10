package com.kinepulse.player.player

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode { NONE, ALL, ONE }

data class QueueItem(
    val uri: Uri,
    val title: String,
    val durationMs: Long = 0L
)

class PlayQueueManager {
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    var repeatMode: RepeatMode = RepeatMode.NONE
    var isShuffled: Boolean = false
        private set

    private var originalOrder: List<QueueItem> = emptyList()

    fun setQueue(list: List<QueueItem>, startIndex: Int = 0) {
        originalOrder = list.toList()
        _items.value = list.toList()
        _currentIndex.value = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    fun addToQueue(item: QueueItem) {
        val updated = _items.value.toMutableList().apply { add(item) }
        originalOrder = updated
        _items.value = updated
    }

    fun currentItem(): QueueItem? = _items.value.getOrNull(_currentIndex.value)

    fun moveToNext(fromCompletion: Boolean = false): QueueItem? {
        val list = _items.value
        if (list.isEmpty()) return null
        if (fromCompletion && repeatMode == RepeatMode.ONE) {
            return currentItem()
        }
        val nextIdx = _currentIndex.value + 1
        return if (nextIdx < list.size) {
            _currentIndex.value = nextIdx
            list[nextIdx]
        } else if (repeatMode == RepeatMode.ALL) {
            _currentIndex.value = 0
            list[0]
        } else {
            null
        }
    }

    fun moveToPrevious(): QueueItem? {
        val list = _items.value
        if (list.isEmpty()) return null
        val prevIdx = _currentIndex.value - 1
        return if (prevIdx >= 0) {
            _currentIndex.value = prevIdx
            list[prevIdx]
        } else if (repeatMode == RepeatMode.ALL) {
            _currentIndex.value = list.lastIndex
            list.last()
        } else {
            null
        }
    }

    fun toggleShuffle() {
        val current = currentItem()
        isShuffled = !isShuffled
        if (isShuffled) {
            val shuffled = _items.value.shuffled()
            _items.value = shuffled
            _currentIndex.value = shuffled.indexOfFirst { it.uri == current?.uri }.coerceAtLeast(0)
        } else {
            _items.value = originalOrder
            _currentIndex.value = originalOrder.indexOfFirst { it.uri == current?.uri }.coerceAtLeast(0)
        }
    }
}
