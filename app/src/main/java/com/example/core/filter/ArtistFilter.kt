package com.example.core.filter

import com.example.core.model.AppSortCriteria
import com.example.core.model.ArtistUiModel
import com.example.core.model.ArtworkRef
import com.example.core.model.Song
import com.example.core.util.ArtistHelper

object ArtistFilter {
    class ArtistGroup(
        val model: ArtistUiModel,
        val songs: List<Song>,
        val searchBlob: String
    )

    fun buildAndSortArtistGroups(
        songs: List<Song>,
        sortCriteria: AppSortCriteria,
        isAscending: Boolean
    ): List<ArtistGroup> {
        val grouped = songs.groupBy { ArtistHelper.extractMainArtist(it.artist) }
            .filterKeys { it.isNotBlank() && it != "<unknown>" && !it.equals("unknown artist", ignoreCase = true) }
            .map { (artistName, artistSongs) ->
                val representativeSong = artistSongs.firstOrNull { it.artworkUri != null || !it.dataPath.isNullOrBlank() } ?: artistSongs.first()

                val model = ArtistUiModel(
                    id = representativeSong.id,
                    name = artistName,
                    songCount = artistSongs.size,
                    artwork = ArtworkRef(
                        artworkUri = representativeSong.artworkUri,
                        contentUri = representativeSong.contentUri
                    )
                )
                val blob = MultiScriptSearchMatcher.getSearchVariants(artistName)
                ArtistGroup(model, artistSongs, blob)
            }

        val sorted = when (sortCriteria) {
            AppSortCriteria.ARTIST, AppSortCriteria.TITLE -> grouped.sortedWith { a, b -> SortHelper.compareTitles(a.model.name, b.model.name) }

            AppSortCriteria.COUNT -> grouped.sortedWith { a, b ->
                val countCmp = a.model.songCount.compareTo(b.model.songCount)

                if (countCmp != 0) countCmp else SortHelper.compareTitles(a.model.name, b.model.name)
            }

            else -> grouped.sortedWith { a, b -> SortHelper.compareTitles(a.model.name, b.model.name) }
        }

        return if (isAscending) sorted else sorted.reversed()
    }

    fun filterArtistGroups(
        groups: List<ArtistGroup>,
        searchQuery: String
    ): List<ArtistUiModel> {
        val plan = MultiScriptSearchMatcher.prepareQuery(searchQuery)
        if (plan.isBlank) {
            return groups.map { it.model }
        }
        return groups.mapNotNull { group ->
            val matched = plan.matchesPrebuiltBlob(group.searchBlob) ||
                group.songs.any { plan.matchesSong(it) }
            if (matched) group.model else null
        }
    }
}

