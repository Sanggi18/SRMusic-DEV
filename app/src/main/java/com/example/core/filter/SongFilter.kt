package com.example.core.filter

import com.example.core.model.AppSortCriteria
import com.example.core.model.Song

object SongFilter {
    fun filterSongs(
        songs: List<Song>,
        searchQuery: String
    ): List<Song> {
        val plan = MultiScriptSearchMatcher.prepareQuery(searchQuery)
        if (plan.isBlank) return songs
        return songs.filter { plan.matchesSong(it) }
    }

    fun sortSongs(
        songs: List<Song>,
        sortCriteria: AppSortCriteria,
        isAscending: Boolean,
        playCounts: Map<Long, Int> = emptyMap()
    ): List<Song> {
        val filtered = songs

        return when (sortCriteria) {
            AppSortCriteria.TITLE -> {
                val cmp = Comparator<Song> { a, b -> SortHelper.compareTitles(a.title, b.title) }

                if (isAscending) filtered.sortedWith(cmp) else filtered.sortedWith(cmp.reversed())
            }

            AppSortCriteria.ARTIST -> {
                if (isAscending) {
                    filtered.sortedWith { a, b ->
                        val artistCmp = SortHelper.compareArtists(a.artist, b.artist)

                        if (artistCmp != 0) artistCmp else SortHelper.compareTitles(a.title, b.title)
                    }
                } else {
                    filtered.sortedWith { a, b ->
                        val artistCmp = SortHelper.compareArtists(b.artist, a.artist)

                        if (artistCmp != 0) artistCmp else SortHelper.compareTitles(a.title, b.title)
                    }
                }
            }

            AppSortCriteria.ALBUM -> {
                if (isAscending) {
                    filtered.sortedWith { a, b ->
                        val albumCmp = SortHelper.compareAlbums(a.album, b.album)

                        if (albumCmp != 0) {
                            albumCmp
                        } else {
                            val aTrack = a.trackNumber

                            val bTrack = b.trackNumber

                            val trackCmp = when {
                                aTrack != null && bTrack != null -> aTrack.compareTo(bTrack)

                                else -> 0
                            }

                            if (trackCmp != 0 && aTrack != null && bTrack != null) trackCmp

                            else if (aTrack != null && bTrack == null) -1

                            else if (aTrack == null && bTrack != null) 1

                            else SortHelper.compareTitles(a.title, b.title)
                        }
                    }
                } else {
                    filtered.sortedWith { a, b ->
                        val albumCmp = SortHelper.compareAlbums(b.album, a.album)

                        if (albumCmp != 0) {
                            albumCmp
                        } else {
                            val aTrack = a.trackNumber

                            val bTrack = b.trackNumber

                            val trackCmp = when {
                                aTrack != null && bTrack != null -> aTrack.compareTo(bTrack)

                                else -> 0
                            }

                            if (trackCmp != 0 && aTrack != null && bTrack != null) trackCmp

                            else if (aTrack != null && bTrack == null) -1

                            else if (aTrack == null && bTrack != null) 1

                            else SortHelper.compareTitles(a.title, b.title)
                        }
                    }
                }
            }

            AppSortCriteria.DATE_ADDED -> {
                val cmp = compareBy<Song> { it.dateAdded?.takeIf { d -> d > 0 } ?: (it.year ?: 0).toLong() }

                if (isAscending) filtered.sortedWith(cmp) else filtered.sortedWith(cmp.reversed())
            }

            AppSortCriteria.DATE_MODIFIED -> {
                val cmp = compareBy<Song> { it.dateModified?.takeIf { d -> d > 0 } ?: it.dateAdded ?: 0L }

                if (isAscending) filtered.sortedWith(cmp) else filtered.sortedWith(cmp.reversed())
            }

            AppSortCriteria.DURATION -> {
                val cmp = compareBy<Song> { it.duration }

                if (isAscending) filtered.sortedWith(cmp) else filtered.sortedWith(cmp.reversed())
            }

            AppSortCriteria.COUNT -> {
                if (isAscending) {
                    filtered.sortedWith { a, b ->
                        val countA = playCounts[a.id] ?: 0

                        val countB = playCounts[b.id] ?: 0

                        val cmp = countA.compareTo(countB)

                        if (cmp != 0) cmp else SortHelper.compareTitles(a.title, b.title)
                    }
                } else {
                    filtered.sortedWith { a, b ->
                        val countA = playCounts[a.id] ?: 0

                        val countB = playCounts[b.id] ?: 0

                        val cmp = countB.compareTo(countA)

                        if (cmp != 0) cmp else SortHelper.compareTitles(a.title, b.title)
                    }
                }
            }

            else -> filtered
        }
    }
}

