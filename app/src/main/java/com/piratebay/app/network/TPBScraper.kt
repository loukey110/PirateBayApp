package com.piratebay.app.network

import com.piratebay.app.model.TorrentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TPBScraper {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    private val apiUrl = "https://apibay.org"
    
    private val trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://public.popcorn-tracker.org:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://open.demonii.com:1337/announce"
    )
    
    private val categoryMap = mapOf(
        "0" to "All",
        "101" to "Audio",
        "102" to "Audio Books",
        "103" to "Sound Clips",
        "104" to "FLAC",
        "199" to "Audio Other",
        "201" to "Movies",
        "202" to "Movies DVDR",
        "203" to "Music Videos",
        "204" to "Movie Clips",
        "205" to "TV Shows",
        "206" to "Handheld",
        "207" to "HD Movies",
        "208" to "HD TV Shows",
        "209" to "3D",
        "299" to "Video Other",
        "301" to "Applications",
        "302" to "Games",
        "303" to "Handheld",
        "304" to "IOS (iPad/iPhone)",
        "305" to "Android",
        "399" to "Other OS",
        "401" to "Games",
        "402" to "PC Games",
        "403" to "PSx",
        "404" to "XBOX360",
        "405" to "Wii",
        "406" to "Handheld",
        "407" to "IOS (iPad/iPhone)",
        "408" to "Android",
        "499" to "Games Other",
        "501" to "Porn",
        "502" to "Porn DVDR",
        "503" to "Porn Pictures",
        "504" to "Games",
        "599" to "Porn Other",
        "601" to "E-books",
        "602" to "Comics",
        "603" to "Pictures",
        "604" to "Covers",
        "605" to "Physibles",
        "699" to "Other Other"
    )
    
    suspend fun search(query: String, category: String = "0"): Result<List<TorrentItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$apiUrl/q.php?q=$encodedQuery&cat=$category"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val json = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val torrents = parseJsonResponse(json)
                Result.success(torrents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun parseJsonResponse(json: String): List<TorrentItem> {
        val torrents = mutableListOf<TorrentItem>()
        
        try {
            val jsonArray = JSONArray(json)
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                
                if (item.getString("id") == "0") continue
                
                val name = item.getString("name")
                val infoHash = item.getString("info_hash")
                val seeders = item.getString("seeders")
                val leechers = item.getString("leechers")
                val sizeBytes = item.getString("size").toLongOrNull() ?: 0
                val username = item.getString("username")
                val added = item.getString("added").toLongOrNull() ?: 0
                val category = item.getString("category")
                
                val magnetLink = buildMagnetLink(infoHash, name)
                
                val size = formatSize(sizeBytes)
                val uploadDate = formatDate(added)
                val categoryName = categoryMap[category] ?: "Other"
                
                torrents.add(
                    TorrentItem(
                        title = name,
                        magnetLink = magnetLink,
                        size = size,
                        seeders = seeders,
                        leechers = leechers,
                        uploadDate = uploadDate,
                        uploader = username,
                        category = categoryName
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return torrents
    }
    
    private fun buildMagnetLink(infoHash: String, name: String): String {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val sb = StringBuilder()
        sb.append("magnet:?xt=urn:btih:$infoHash&dn=$encodedName")
        
        for (tracker in trackers) {
            sb.append("&tr=").append(URLEncoder.encode(tracker, "UTF-8"))
        }
        
        return sb.toString()
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            bytes < 1024L * 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
            else -> String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp * 1000))
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    suspend fun getTopTorrents(category: String = "0"): Result<List<TorrentItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val topUrl = if (category == "0") {
                    "$apiUrl/precompiled/data_top100_all.json"
                } else {
                    "$apiUrl/precompiled/data_top100_$category.json"
                }
                
                val request = Request.Builder()
                    .url(topUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val json = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val torrents = parseJsonResponse(json)
                Result.success(torrents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
