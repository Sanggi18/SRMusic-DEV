package com.example.core.data.repository

import android.content.Context
import com.example.core.data.database.AppDatabase
import com.example.core.data.database.song.SongDao
import com.example.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import java.io.File

interface SongRepository {
    val allSongsFlow: Flow<List<Song>>

    val playCountsFlow: Flow<Map<Long, Int>>

    suspend fun getAllSongs(): List<Song>

    suspend fun updateSongDuration(songId: Long, duration: Long)
}

class SongRepositoryImpl(
    private val context: Context,
    private val songDao: SongDao = AppDatabase.getInstance(context).songDao()
) : SongRepository {
    override val allSongsFlow: Flow<List<Song>> = songDao.getAllSongsWithFavorites()
        .map { list -> list.map { it.toSong() }.filter(::isValidSong) }
        .flowOn(Dispatchers.IO)

    override val playCountsFlow: Flow<Map<Long, Int>> = songDao.getAllPlayCounts()
        .map { list -> list.associate { it.songId to it.count } }
        .flowOn(Dispatchers.IO)

    override suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongsWithFavoritesDirect().map { it.toSong() }.filter(::isValidSong)
    }

    override suspend fun updateSongDuration(songId: Long, duration: Long) = withContext(Dispatchers.IO) {
        if (songId > 0 && duration > 0) {
            songDao.updateSongDuration(songId, duration)
        }
    }

    private fun isValidSong(song: Song): Boolean {
        if (song.duration <= 0L) return false
        val dp = song.dataPath
        if (!dp.isNullOrBlank()) {
            val file = File(dp)
            if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        }
        return true
    }
}
