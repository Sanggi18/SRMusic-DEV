package com.example.core.model

data class AlbumUiModel(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artwork: ArtworkRef = ArtworkRef()
)

data class ArtistUiModel(
    val id: Long,
    val name: String,
    val songCount: Int,
    val artwork: ArtworkRef = ArtworkRef()
)

data class FolderUiModel(
    val path: String,
    val name: String,
    val songCount: Int,
    val subfolderCount: Int = 0,
    val artwork: ArtworkRef = ArtworkRef()
)
