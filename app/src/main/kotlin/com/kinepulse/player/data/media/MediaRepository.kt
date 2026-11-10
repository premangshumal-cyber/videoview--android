package com.kinepulse.player.data.media

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.kinepulse.player.data.db.AppDatabase
import com.kinepulse.player.data.db.Favorite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StorageLocation { INTERNAL, SD_CARD, USB_OTG, NETWORK, MISSING }

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateModifiedSec: Long,
    val dateAddedSec: Long,
    val filePath: String,
    val storageLocation: StorageLocation = StorageLocation.INTERNAL,
    val resumePositionMs: Long = 0L,
    val seen: Boolean = false,
    val isFavorite: Boolean = false
) {
    val resolutionBadge: String
        get() {
            val maxDim = maxOf(width, height)
            return when {
                maxDim >= 3800 -> "4K"
                maxDim >= 2500 -> "1440p"
                maxDim >= 1900 -> "1080p"
                maxDim >= 1200 -> "720p"
                maxDim >= 700 -> "480p"
                maxDim > 0 -> "${minOf(width, height)}p"
                else -> ""
            }
        }
}

data class AudioItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val trackNumber: Int
)

class MediaRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val _rawVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _rawAudios = MutableStateFlow<List<AudioItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val videos: Flow<List<VideoItem>> = combine(
        _rawVideos,
        db.playbackDao().observeAllStates(),
        db.playbackDao().observeFavorites()
    ) { list, states, favs ->
        val stateMap = states.associateBy { it.uri }
        val favSet = favs.map { it.uri }.toSet()
        list.map { item ->
            val st = stateMap[item.uri.toString()]
            item.copy(
                resumePositionMs = st?.positionMs ?: 0L,
                seen = st?.seen ?: false,
                isFavorite = favSet.contains(item.uri.toString())
            )
        }
    }

    val audios: StateFlow<List<AudioItem>> = _rawAudios.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { refreshLibrary() }
        }
    }

    init {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (_: SecurityException) {}
    }

    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _rawVideos.value = queryVideos()
        _rawAudios.value = queryAudios()
        _isLoading.value = false
    }

    private fun queryVideos(): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val addCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val path = cursor.getString(dataCol) ?: ""
                    val storageLoc = when {
                        path.startsWith("/storage/emulated") -> StorageLocation.INTERNAL
                        path.contains("usb", ignoreCase = true) -> StorageLocation.USB_OTG
                        path.startsWith("/storage/") -> StorageLocation.SD_CARD
                        else -> StorageLocation.INTERNAL
                    }
                    result.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = cursor.getString(titleCol) ?: "Unknown",
                            fileName = cursor.getString(nameCol) ?: "Unknown",
                            durationMs = cursor.getLong(durCol),
                            width = cursor.getInt(wCol),
                            height = cursor.getInt(hCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            dateModifiedSec = cursor.getLong(modCol),
                            dateAddedSec = cursor.getLong(addCol),
                            filePath = path,
                            storageLocation = storageLoc
                        )
                    )
                }
            }
        } catch (_: SecurityException) {}
        return result
    }

    private fun queryAudios(): List<AudioItem> {
        val result = mutableListOf<AudioItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
        )
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    result.add(
                        AudioItem(
                            id = id,
                            uri = uri,
                            title = cursor.getString(titleCol) ?: "Unknown Track",
                            artist = cursor.getString(artistCol) ?: "Unknown Artist",
                            album = cursor.getString(albumCol) ?: "Unknown Album",
                            genre = "General",
                            durationMs = cursor.getLong(durCol),
                            trackNumber = cursor.getInt(trackCol)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {}
        return result
    }

    suspend fun toggleFavorite(video: VideoItem) = withContext(Dispatchers.IO) {
        if (video.isFavorite) {
            db.playbackDao().removeFavorite(video.uri.toString())
        } else {
            db.playbackDao().addFavorite(
                Favorite(uri = video.uri.toString(), type = 2, title = video.title)
            )
        }
    }
}
