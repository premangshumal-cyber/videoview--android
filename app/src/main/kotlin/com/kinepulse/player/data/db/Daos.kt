package com.kinepulse.player.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_state")
    fun observeAllStates(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback_state WHERE uri = :uri LIMIT 1")
    suspend fun getState(uri: String): PlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: PlaybackState)

    @Query("SELECT * FROM history_entries ORDER BY lastPlayed DESC")
    fun observeHistory(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntry)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()

    @Query("SELECT * FROM fav_table")
    fun observeFavorites(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: Favorite)

    @Query("DELETE FROM fav_table WHERE uri = :uri")
    suspend fun removeFavorite(uri: String)

    @Query("SELECT * FROM external_subtitles_table WHERE mediaPath = :mediaUri")
    suspend fun getSubtitlesForMedia(mediaUri: String): List<ExternalSubtitle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalSubtitle(sub: ExternalSubtitle)

    @Query("SELECT * FROM CustomDirectory")
    fun observeCustomDirectories(): Flow<List<CustomDirectory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCustomDirectory(dir: CustomDirectory)

    @Delete
    suspend fun removeCustomDirectory(dir: CustomDirectory)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<Playlist>>

    @Insert
    suspend fun createPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItem>

    @Insert
    suspend fun insertPlaylistItem(item: PlaylistItem)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Query("SELECT * FROM bookmarks WHERE mediaUri = :mediaUri ORDER BY timeMs ASC")
    fun observeBookmarks(mediaUri: String): Flow<List<Bookmark>>

    @Insert
    suspend fun addBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM saved_streams ORDER BY id DESC")
    fun observeStreams(): Flow<List<SavedStream>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStream(stream: SavedStream)

    @Delete
    suspend fun deleteStream(stream: SavedStream)

    @Query("SELECT * FROM video_groups")
    fun observeVideoGroups(): Flow<List<VideoGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoGroup(group: VideoGroupEntity)
}
