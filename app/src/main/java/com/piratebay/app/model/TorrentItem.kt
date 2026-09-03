package com.piratebay.app.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TorrentItem(
    val id: String,
    val infoHash: String,
    val title: String,
    val magnetLink: String,
    val sizeBytes: Long,
    val seedersCount: Int,
    val leechersCount: Int,
    val uploadTimestamp: Long,
    val uploader: String,
    val category: String,
    val translatedTitle: String? = null,
    val isTranslating: Boolean = false
) {
    val displayTitle: String
        get() = translatedTitle ?: title

    val isTranslated: Boolean
        get() = !translatedTitle.isNullOrEmpty()

    val formattedSize: String
        get() = formatSize(sizeBytes)

    val formattedDate: String
        get() = formatDate(uploadTimestamp)

    // 保持向后兼容属性
    val size: String get() = formattedSize
    val seeders: String get() = seedersCount.toString()
    val leechers: String get() = leechersCount.toString()
    val uploadDate: String get() = formattedDate

    companion object {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
                bytes < 1024L * 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
                else -> String.format(Locale.US, "%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
            }
        }

        fun formatDate(timestamp: Long): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(timestamp * 1000))
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }
}

