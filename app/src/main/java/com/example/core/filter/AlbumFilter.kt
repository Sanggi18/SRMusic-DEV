package com.example.core.filter

import com.example.core.model.AlbumUiModel
import com.example.core.model.AppSortCriteria
import com.example.core.model.ArtworkRef
import com.example.core.model.Song

object AlbumFilter {
    class AlbumGroup(
        val model: AlbumUiModel,
        val songs: List<Song>,
        val searchBlob: String
    )

    fun buildAndSortAlbumGroups(
        songs: List<Song>,
        sortCriteria: AppSortCriteria,
        isAscending: Boolean
    ): List<AlbumGroup> {
        val grouped = songs.groupBy { it.album.trim() }
            .filterKeys { it.isNotBlank() && it != "<unknown>" && !it.equals("unknown album", ignoreCase = true) }
            .map { (albumName, albumSongs) ->
                val repSong = albumSongs.firstOrNull { !it.dataPath.isNullOrBlank() } ?: albumSongs.first()

                val validArtists = albumSongs.map { it.artist.trim() }
                    .filter { it.isNotBlank() && it != "<unknown>" && !it.equals("unknown artist", ignoreCase = true) }

                val resolvedArtist = if (validArtists.isNotEmpty()) {
                    validArtists.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: validArtists.first()
                } else {
                    repSong.artist.trim().takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                }

                val model = AlbumUiModel(
                    id = repSong.albumId.takeIf { it != -1L } ?: repSong.id,
                    name = albumName,
                    artist = resolvedArtist,
                    songCount = albumSongs.size,
                    artwork = ArtworkRef(
                        artworkUri = repSong.artworkUri,
                        contentUri = repSong.contentUri
                    )
                )
                val blob = "${MultiScriptSearchMatcher.getSearchVariants(albumName)} ${MultiScriptSearchMatcher.getSearchVariants(resolvedArtist)}"
                AlbumGroup(model, albumSongs, blob)
            }

        return when (sortCriteria) {
            AppSortCriteria.ALBUM, AppSortCriteria.TITLE -> {
                if (isAscending) {
                    grouped.sortedWith { a, b ->
                        val nameCmp = SortHelper.compareAlbums(a.model.name, b.model.name)

                        if (nameCmp != 0) nameCmp else SortHelper.compareArtists(a.model.artist, b.model.artist)
                    }
                } else {
                    grouped.sortedWith { a, b ->
                        val nameCmp = SortHelper.compareAlbums(b.model.name, a.model.name)

                        if (nameCmp != 0) nameCmp else SortHelper.compareArtists(a.model.artist, b.model.artist)
                    }
                }
            }

            AppSortCriteria.ARTIST -> {
                if (isAscending) {
                    grouped.sortedWith { a, b ->
                        val artistCmp = SortHelper.compareArtists(a.model.artist, b.model.artist)

                        if (artistCmp != 0) artistCmp else SortHelper.compareAlbums(a.model.name, b.model.name)
                    }
                } else {
                    grouped.sortedWith { a, b ->
                        val artistCmp = SortHelper.compareArtists(b.model.artist, a.model.artist)

                        if (artistCmp != 0) artistCmp else SortHelper.compareAlbums(a.model.name, b.model.name)
                    }
                }
            }

            AppSortCriteria.COUNT -> {
                if (isAscending) {
                    grouped.sortedWith { a, b ->
                        val countCmp = a.model.songCount.compareTo(b.model.songCount)

                        if (countCmp != 0) countCmp else SortHelper.compareAlbums(a.model.name, b.model.name)
                    }
                } else {
                    grouped.sortedWith { a, b ->
                        val countCmp = b.model.songCount.compareTo(a.model.songCount)

                        if (countCmp != 0) countCmp else SortHelper.compareAlbums(a.model.name, b.model.name)
                    }
                }
            }

            else -> {
                if (isAscending) {
                    grouped.sortedWith { a, b ->
                        val nameCmp = SortHelper.compareAlbums(a.model.name, b.model.name)

                        if (nameCmp != 0) nameCmp else SortHelper.compareArtists(a.model.artist, b.model.artist)
                    }
                } else {
                    grouped.sortedWith { a, b ->
                        val nameCmp = SortHelper.compareAlbums(b.model.name, a.model.name)

                        if (nameCmp != 0) nameCmp else SortHelper.compareArtists(a.model.artist, b.model.artist)
                    }
                }
            }
        }
    }

    fun filterAlbumGroups(
        groups: List<AlbumGroup>,
        searchQuery: String
    ): List<AlbumUiModel> {
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

