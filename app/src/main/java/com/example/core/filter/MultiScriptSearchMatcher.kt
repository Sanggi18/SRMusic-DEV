package com.example.core.filter

import android.os.Build
import com.example.core.model.Song
import com.example.core.util.ArtistHelper
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object MultiScriptSearchMatcher {
    private val textVariantsCache = ConcurrentHashMap<String, String>(2048)
    private val songSearchBlobCache = ConcurrentHashMap<Long, String>(2048)
    private val queryCache = ConcurrentHashMap<String, QueryPlan>(128)
    private val whitespaceRegex = "\\s+".toRegex()

    private val icuTransliterator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    class QueryPlan(
        val raw: String,
        val rawLower: String,
        val clean: String,
        val tokens: Array<String>
    ) {
        val isBlank: Boolean = clean.isEmpty() && rawLower.isEmpty()

        fun matchesPrebuiltBlob(blob: String): Boolean {
            if (isBlank) return true
            if (blob.isEmpty()) return false
            if (tokens.isEmpty()) {
                return blob.indexOf(clean) >= 0 || (rawLower != clean && blob.indexOf(rawLower) >= 0)
            }
            for (i in tokens.indices) {
                if (blob.indexOf(tokens[i]) < 0) return false
            }
            return true
        }

        fun matchesText(text: String?): Boolean {
            if (isBlank) return true
            if (text.isNullOrBlank()) return false
            val variant = getSearchVariants(text)
            return matchesPrebuiltBlob(variant)
        }

        fun matchesSong(song: Song): Boolean {
            if (isBlank) return true
            val blob = getSongBlob(song)
            return matchesPrebuiltBlob(blob)
        }
    }

    private val EMPTY_PLAN = QueryPlan("", "", "", emptyArray())

    private fun hasNonLatin(str: String): Boolean {
        for (i in 0 until str.length) {
            if (str[i].code >= 0x0370) return true
        }
        return false
    }

    fun stripDiacritics(input: String): String {
        var hasNonAscii = false
        for (i in 0 until input.length) {
            if (input[i].code > 127) {
                hasNonAscii = true
                break
            }
        }
        if (!hasNonAscii) return input
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (i in 0 until decomposed.length) {
            val c = decomposed[i]
            if (c.code !in 0x0300..0x036F) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun getSearchVariants(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val cached = textVariantsCache[text]
        if (cached != null) return cached

        val lower = text.lowercase()
        val normalized = stripDiacritics(lower)
        val transliterated = if (hasNonLatin(text)) {
            icuTransliterator?.let { transliterator ->
                try {
                    synchronized(transliterator) {
                        stripDiacritics(transliterator.transliterate(text).lowercase())
                    }
                } catch (_: Throwable) {
                    ""
                }
            }.orEmpty()
        } else {
            ""
        }

        val sb = StringBuilder(normalized.length + lower.length + transliterated.length + 2)
        sb.append(normalized)
        if (lower != normalized) {
            sb.append(' ').append(lower)
        }
        if (transliterated.isNotEmpty() && transliterated != normalized && transliterated != lower) {
            sb.append(' ').append(transliterated)
        }
        val result = sb.toString()
        textVariantsCache[text] = result
        return result
    }

    fun prepareQuery(query: String): QueryPlan {
        if (query.isBlank()) return EMPTY_PLAN
        queryCache[query]?.let { return it }

        if (queryCache.size > 256) {
            queryCache.clear()
        }
        return queryCache.getOrPut(query) {
            val rawLower = query.lowercase().trim()
            val clean = stripDiacritics(rawLower)
            val tokens = if (clean.indexOf(' ') >= 0) {
                clean.split(whitespaceRegex).filter { it.isNotEmpty() }.toTypedArray()
            } else {
                emptyArray()
            }
            QueryPlan(query, rawLower, clean, tokens)
        }
    }

    fun getSongBlob(song: Song): String {
        songSearchBlobCache[song.id]?.let { return it }
        val built = buildSongSearchBlob(song)
        songSearchBlobCache[song.id] = built
        return built
    }

    fun prewarmSongs(songs: List<Song>) {
        for (i in songs.indices) {
            val song = songs[i]
            if (!songSearchBlobCache.containsKey(song.id)) {
                songSearchBlobCache[song.id] = buildSongSearchBlob(song)
            }
            ArtistHelper.extractMainArtist(song.artist)
        }
    }

    fun clearCache() {
        textVariantsCache.clear()
        songSearchBlobCache.clear()
        queryCache.clear()
        ArtistHelper.clearCache()
    }

    private fun appendVariant(sb: StringBuilder, text: String?) {
        if (!text.isNullOrBlank()) {
            val variant = getSearchVariants(text)
            if (variant.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(variant)
            }
        }
    }

    private fun buildSongSearchBlob(song: Song): String {
        val sb = StringBuilder(128)
        appendVariant(sb, song.title)
        appendVariant(sb, song.artist)
        appendVariant(sb, song.album)
        appendVariant(sb, song.displayName)
        song.dataPath?.let { path ->
            val lastSlash = path.lastIndexOf('/')
            val fileName = if (lastSlash >= 0) path.substring(lastSlash + 1) else path
            appendVariant(sb, fileName)
        }
        return sb.toString()
    }

    fun matchesText(text: String?, query: String): Boolean {
        if (query.isBlank()) return true
        return prepareQuery(query).matchesText(text)
    }

    fun matchesSong(song: Song, query: String): Boolean {
        if (query.isBlank()) return true
        return prepareQuery(query).matchesSong(song)
    }
}

