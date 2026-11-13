package com.example.core.util

import java.util.concurrent.ConcurrentHashMap

object ArtistHelper {
    private val ARTIST_DELIMITER_REGEX = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|&|vs\\.?|x|/)\\s+|,\\s*|;\\s*")
    private val mainArtistCache = ConcurrentHashMap<String, String>()

    fun extractMainArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return "Unknown Artist"

        mainArtistCache[rawArtist]?.let { return it }

        val trimmed = rawArtist.trim()

        if (trimmed.equals("<unknown>", ignoreCase = true) || trimmed.equals("unknown artist", ignoreCase = true)) {
            mainArtistCache[rawArtist] = "Unknown Artist"
            return "Unknown Artist"
        }

        val parts = trimmed.split(ARTIST_DELIMITER_REGEX)

        val first = parts.firstOrNull { it.isNotBlank() }?.trim() ?: trimmed

        val result = if (first.isNotBlank()) first else "Unknown Artist"
        mainArtistCache[rawArtist] = result
        return result
    }

    fun clearCache() {
        mainArtistCache.clear()
    }
}

