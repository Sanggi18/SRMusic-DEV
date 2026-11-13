package com.example.core.data.mediastore.tags

import com.example.core.model.Song

object GenreUtils {
    fun isValidGenre(genre: String?): Boolean {
        if (genre.isNullOrBlank()) return false

        val lower = genre.trim().lowercase()

        return lower != "<unknown>" &&
            lower != "unknown" &&
            lower != "unknown genre" &&
            genre.trim().length in 2..50
    }

    fun cleanGenre(genre: String): String {
        return genre.trim().trim('"', '\'', ';', '/', '\\', '\u0000').trim()
    }

    fun groupSongsByGenre(allSongs: List<Song>): List<Pair<String, List<Song>>> {
        val grouped = mutableMapOf<String, MutableList<Song>>()
        val unknownList = mutableListOf<Song>()

        for (song in allSongs) {
            val genre = song.genre
            if (isValidGenre(genre)) {
                val clean = cleanGenre(genre!!)
                grouped.getOrPut(clean) { mutableListOf() }.add(song)
            } else {
                unknownList.add(song)
            }
        }

        val sortedKeys = grouped.keys.sortedBy { it.lowercase() }
        val result = ArrayList<Pair<String, List<Song>>>(sortedKeys.size + 1)
        for (genre in sortedKeys) {
            val songsInGenre = grouped[genre]?.sortedBy { it.title.lowercase() } ?: emptyList()
            result.add(genre to songsInGenre)
        }
        if (unknownList.isNotEmpty()) {
            result.add("Other" to unknownList.sortedBy { it.title.lowercase() })
        }
        return result
    }
}
