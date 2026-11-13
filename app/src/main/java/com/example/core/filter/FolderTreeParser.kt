package com.example.core.filter

import com.example.core.model.ArtworkRef
import com.example.core.model.FolderUiModel
import com.example.core.model.Song
import java.util.concurrent.ConcurrentHashMap

object FolderTreeParser {
    private val songDirCache = ConcurrentHashMap<Long, String>(2048)

    class BaseSubfolderGroup(
        val model: FolderUiModel,
        val songsInSubfolder: List<Song>,
        val searchBlob: String
    )

    class BaseFolderContent(
        val songsUnderCurrent: List<Song>,
        val directSongs: List<Song>,
        val subfolderGroups: List<BaseSubfolderGroup>
    )

    fun prewarmDirectories(songs: List<Song>) {
        for (i in songs.indices) {
            getSongRelativeDirectory(songs[i])
        }
    }

    fun clearCache() {
        songDirCache.clear()
    }

    fun getSongRelativeDirectory(song: Song): String {
        songDirCache[song.id]?.let { return it }

        val rp = song.relativePath

        val dp = song.dataPath

        val path = when {
            !rp.isNullOrBlank() -> rp.trim('/')

            !dp.isNullOrBlank() -> {
                val dir = dp.substringBeforeLast('/', "")

                when {
                    dir.contains("/Music/") -> dir.substringAfter("/Music/").trim('/')

                    dir.contains("/Music") -> dir.substringAfter("/Music").trim('/')

                    dir.contains("/sdcard/") -> dir.substringAfter("/sdcard/").trim('/')

                    dir.contains("/storage/emulated/0/") -> dir.substringAfter("/storage/emulated/0/").trim('/')

                    else -> dir.trim('/')
                }
            }

            else -> ""
        }

        val normalized = path.removePrefix("Music/").removePrefix("Music").trim('/')
        songDirCache[song.id] = normalized
        return normalized
    }

    fun buildBaseFolderContent(
        allSongs: List<Song>,
        effectiveRelativePath: String
    ): BaseFolderContent {
        val normalizedCurrentPath = effectiveRelativePath.trim('/')
        val prefixWithSlash = if (normalizedCurrentPath.isEmpty()) "" else "$normalizedCurrentPath/"

        val songsUnderCurrent = ArrayList<Song>()
        val directSongs = ArrayList<Song>()
        val subfolderMap = LinkedHashMap<String, MutableList<Song>>()

        for (i in allSongs.indices) {
            val song = allSongs[i]
            val songDir = getSongRelativeDirectory(song)

            val isUnder = if (normalizedCurrentPath.isEmpty()) {
                true
            } else {
                songDir == normalizedCurrentPath || songDir.startsWith(prefixWithSlash)
            }

            if (isUnder) {
                songsUnderCurrent.add(song)
                if (songDir == normalizedCurrentPath) {
                    directSongs.add(song)
                } else if (songDir.isNotEmpty()) {
                    val remainingPath = if (normalizedCurrentPath.isEmpty()) {
                        songDir
                    } else {
                        songDir.removePrefix(prefixWithSlash).trim('/')
                    }
                    val immediateChild = remainingPath.substringBefore('/')
                    if (immediateChild.isNotEmpty()) {
                        subfolderMap.getOrPut(immediateChild) { ArrayList() }.add(song)
                    }
                }
            }
        }

        val subfolderGroups = subfolderMap.map { (folderName, songsInSubfolder) ->
            val subfolderRelPath = if (normalizedCurrentPath.isEmpty()) folderName else "$normalizedCurrentPath/$folderName"
            val subPrefix = "$subfolderRelPath/"
            val nestedDirs = HashSet<String>()

            for (i in songsInSubfolder.indices) {
                val dir = getSongRelativeDirectory(songsInSubfolder[i])
                if (dir.startsWith(subPrefix)) {
                    val child = dir.removePrefix(subPrefix).substringBefore('/')
                    if (child.isNotEmpty()) nestedDirs.add(child)
                }
            }

            val repSong = songsInSubfolder.firstOrNull { it.artworkUri != null || !it.dataPath.isNullOrBlank() }
                ?: songsInSubfolder.firstOrNull()

            val model = FolderUiModel(
                path = subfolderRelPath,
                name = folderName,
                songCount = songsInSubfolder.size,
                subfolderCount = nestedDirs.size,
                artwork = ArtworkRef(
                    artworkUri = repSong?.artworkUri,
                    contentUri = repSong?.contentUri
                )
            )
            val blob = "${MultiScriptSearchMatcher.getSearchVariants(folderName)} ${MultiScriptSearchMatcher.getSearchVariants(subfolderRelPath)}"
            BaseSubfolderGroup(model, songsInSubfolder, blob)
        }.sortedBy { it.model.name.lowercase() }

        return BaseFolderContent(
            songsUnderCurrent = songsUnderCurrent,
            directSongs = directSongs,
            subfolderGroups = subfolderGroups
        )
    }

    fun filterFolderContent(
        base: BaseFolderContent,
        searchQuery: String
    ): Pair<List<FolderUiModel>, List<Song>> {
        val plan = MultiScriptSearchMatcher.prepareQuery(searchQuery)
        if (plan.isBlank) {
            return Pair(base.subfolderGroups.map { it.model }, base.directSongs)
        }

        val matchedSongs = base.songsUnderCurrent.filter { plan.matchesSong(it) }
        val matchedFolders = base.subfolderGroups.mapNotNull { group ->
            val matches = plan.matchesPrebuiltBlob(group.searchBlob) ||
                group.songsInSubfolder.any { plan.matchesSong(it) }
            if (matches) group.model else null
        }

        return Pair(matchedFolders, matchedSongs)
    }

    fun getSongsInFolder(allSongs: List<Song>, folderPath: String): List<Song> {
        val targetRelPath = folderPath.trim('/')
        val prefix = "$targetRelPath/"

        return allSongs.filter { s ->
            val dir = getSongRelativeDirectory(s)

            dir == targetRelPath || dir.startsWith(prefix)
        }
    }
}

