package com.example.ui.home.core

import com.example.core.model.Song
import com.example.core.util.ArtistHelper
import com.example.core.data.mediastore.tags.GenreUtils
import java.util.Calendar
import java.util.Random

enum class HomeChildScreen {
    NONE,
    FOR_YOU_REMIX,
    GENRE_TODAY,
    ALL_GENRE,
    NEW_RELEASE,
    ALL_RELEASE
}

data class DailyGenreItem(
    val name: String,
    val songs: List<Song>,
    val sampleSong: Song?,
    val artists: List<String>
)

object HomeContentGenerator {
    fun generateRemixQueue(
        allSongs: List<Song>,
        installSeed: Long,
        manualOffset: Int
    ): List<Song> {
        if (allSongs.isEmpty()) return emptyList()

        val calendar = Calendar.getInstance()

        val seed = installSeed + calendar.get(Calendar.DAY_OF_YEAR) * 10000L + calendar.get(Calendar.YEAR) + manualOffset

        val rnd = Random(seed)

        val shuffled = allSongs.shuffled(rnd)

        return if (shuffled.size <= 100) shuffled else shuffled.take(100)
    }

    fun generateDailyGenres(
        allSongs: List<Song>,
        installSeed: Long,
        manualOffset: Int
    ): List<DailyGenreItem> {
        if (allSongs.isEmpty()) {
            return emptyList()
        }

        val genreMap = mutableMapOf<String, MutableList<Song>>()

        for (song in allSongs) {
            song.genre?.let { g ->
                if (GenreUtils.isValidGenre(g)) {
                    val cleaned = GenreUtils.cleanGenre(g)

                    genreMap.getOrPut(cleaned) { mutableListOf() }.add(song)
                }
            }
        }

        val strictGenres = genreMap.filter { it.value.size >= 10 }.keys.toList().sorted()

        val availableGenres = if (strictGenres.isNotEmpty()) strictGenres else genreMap.keys.toList().sorted()

        val calendar = Calendar.getInstance()

        val daySeed = installSeed + calendar.get(Calendar.DAY_OF_YEAR) + (calendar.get(Calendar.YEAR) * 366)

        return if (availableGenres.isNotEmpty()) {
            val baseIndex = (Math.abs(daySeed) % availableGenres.size).toInt()

            val count = minOf(3, availableGenres.size)

            (0 until count).map { i ->
                val gIndex = (baseIndex + manualOffset + i).mod(availableGenres.size)

                val gName = availableGenres[gIndex]

                val gSongs = genreMap[gName] ?: emptyList()

                val gArtists = gSongs.map { ArtistHelper.extractMainArtist(it.artist) }
                    .filter { it.isNotBlank() && it != "<unknown>" && !it.equals("unknown artist", ignoreCase = true) }
                    .distinct()

                DailyGenreItem(
                    name = gName,
                    songs = gSongs,
                    sampleSong = gSongs.firstOrNull(),
                    artists = gArtists
                )
            }
        } else {
            val defaultArtists = allSongs.map { ArtistHelper.extractMainArtist(it.artist) }
                .filter { it.isNotBlank() && it != "<unknown>" && !it.equals("unknown artist", ignoreCase = true) }
                .distinct()
                .take(5)

            listOf(
                DailyGenreItem(
                    name = "Music",
                    songs = allSongs,
                    sampleSong = allSongs.firstOrNull(),
                    artists = defaultArtists
                )
            )
        }
    }

    fun computeTopArtist(
        songs: List<Song>,
        songPlayCounts: Map<Long, Int>
    ): Map.Entry<String, List<Song>>? {
        val playedSongs = songs.filter { (songPlayCounts[it.id] ?: 0) > 0 }
        if (playedSongs.isEmpty()) return null
        return playedSongs.groupBy { ArtistHelper.extractMainArtist(it.artist) }
            .filter { it.key.isNotBlank() && it.key != "<unknown>" && !it.key.equals("unknown artist", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<Song>>> { entry -> entry.value.sumOf { songPlayCounts[it.id] ?: 0 } }
                    .thenBy { it.value.size }
            )
    }

    fun computeTopAlbum(
        songs: List<Song>,
        songPlayCounts: Map<Long, Int>
    ): Map.Entry<String, List<Song>>? {
        val playedSongs = songs.filter { (songPlayCounts[it.id] ?: 0) > 0 }
        if (playedSongs.isEmpty()) return null
        return playedSongs.groupBy { it.album }
            .filter { it.key.isNotBlank() && it.key != "<unknown>" && !it.key.equals("unknown album", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<Song>>> { entry -> entry.value.sumOf { songPlayCounts[it.id] ?: 0 } }
                    .thenBy { it.value.size }
            )
    }

    fun computeTopSong(
        songs: List<Song>,
        songPlayCounts: Map<Long, Int>
    ): Song? {
        val playedSongs = songs.filter { (songPlayCounts[it.id] ?: 0) > 0 }
        if (playedSongs.isEmpty()) return null
        return playedSongs.maxWithOrNull(
            compareBy<Song> { songPlayCounts[it.id] ?: 0 }
                .thenByDescending { it.dateModified }
        )
    }

    fun formatListeningTime(seconds: Long): String {
        val hours = seconds / 3600

        val minutes = (seconds % 3600) / 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"

            minutes > 0 -> "${minutes}m"

            else -> "${seconds}s"
        }
    }

    fun computeGenreSummary(allSongs: List<Song>, installSeed: Long): Pair<Int, List<Song>> {
        if (allSongs.isEmpty()) return Pair(1, emptyList())
        val validSongs = allSongs.filter { it.genre != null && GenreUtils.isValidGenre(it.genre) }
        val count = validSongs.map { GenreUtils.cleanGenre(it.genre!!) }.distinct().size.coerceAtLeast(1)
        val previews = if (allSongs.size <= 3) {
            allSongs
        } else {
            val shuffled = allSongs.shuffled(Random(installSeed))
            shuffled.distinctBy { it.genre }.take(3).ifEmpty { allSongs.take(3) }
        }
        return Pair(count, previews)
    }
}
