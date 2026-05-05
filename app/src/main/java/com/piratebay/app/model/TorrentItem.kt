package com.piratebay.app.model

data class TorrentItem(
    val title: String,
    val magnetLink: String,
    val size: String,
    val seeders: String,
    val leechers: String,
    val uploadDate: String,
    val uploader: String,
    val category: String
)
