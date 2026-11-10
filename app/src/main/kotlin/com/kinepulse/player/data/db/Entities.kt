package com.kinepulse.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackState(
    @PrimaryKey val uri: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayed: Long = System.currentTimeMillis(),
    val speed: Float = 1.0f,
    val audioTrackIndex: Int = -1,
    val subtitleUri: String? = null,
    val subtitleDelayMs: Long = 0L,
    val aspectMode: Int = 0,
    val seen: Boolean = false
)

@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val lastPlayed: Long = System.currentTimeMillis(),
    val type: String
)

@Entity(tableName = "fav_table")
data class Favorite(
    @PrimaryKey val uri: String,
    val type: Int,
    val title: String,
    val iconUrl: String? = null
)

@Entity(tableName = "external_subtitles_table")
data class ExternalSubtitle(
    @PrimaryKey(autoGenerate = true) val idSubtitle: Long = 0,
    val subtitlePath: String,
    val mediaPath: String,
    val subLanguageID: String = "und",
    val movieReleaseName: String = ""
)

@Entity(tableName = "CustomDirectory")
data class CustomDirectory(
    @PrimaryKey val path: String
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val position: Int,
    val isAudioOnly: Boolean = false
)

@Entity(tableName = "video_groups")
data class VideoGroupEntity(
    @PrimaryKey val groupKey: String,
    val displayName: String,
    val isUngrouped: Boolean = false,
    val customUrisCsv: String = ""
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val timeMs: Long,
    val name: String
)

@Entity(tableName = "saved_streams")
data class SavedStream(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String
)
