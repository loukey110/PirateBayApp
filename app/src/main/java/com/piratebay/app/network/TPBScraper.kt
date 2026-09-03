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
    
    private val apiUrls = listOf(
        "https://apibay.org",
        "https://piratebay.party"
    )
    
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
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            var lastException: Exception? = null

            for (baseUrl in apiUrls) {
                try {
                    val url = "$baseUrl/q.php?q=$encodedQuery&cat=$category"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: continue
                        val torrents = parseJsonResponse(json)
                        return@withContext Result.success(torrents)
                    } else {
                        lastException = Exception("HTTP ${response.code} from $baseUrl")
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            Result.failure(lastException ?: Exception("Network request failed"))
        }
    }
    
    private fun parseJsonResponse(json: String): List<TorrentItem> {
        val torrents = mutableListOf<TorrentItem>()
        
        try {
            val jsonArray = JSONArray(json)
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "0")
                if (id == "0") continue
                
                val name = item.optString("name", "Unknown")
                val infoHash = item.optString("info_hash", "")
                if (infoHash.isEmpty()) continue

                val seeders = item.optString("seeders", "0").toIntOrNull() ?: 0
                val leechers = item.optString("leechers", "0").toIntOrNull() ?: 0
                val sizeBytes = item.optString("size", "0").toLongOrNull() ?: 0L
                val username = item.optString("username", "Anonymous")
                val added = item.optString("added", "0").toLongOrNull() ?: 0L
                val category = item.optString("category", "0")
                
                val magnetLink = buildMagnetLink(infoHash, name)
                val categoryName = categoryMap[category] ?: "Other"
                
                torrents.add(
                    TorrentItem(
                        id = id,
                        infoHash = infoHash,
                        title = name,
                        magnetLink = magnetLink,
                        sizeBytes = sizeBytes,
                        seedersCount = seeders,
                        leechersCount = leechers,
                        uploadTimestamp = added,
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
    
    suspend fun getTopTorrents(category: String = "0"): Result<List<TorrentItem>> {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null

            for (baseUrl in apiUrls) {
                try {
                    val path = if (category == "0") {
                        "precompiled/data_top100_all.json"
                    } else {
                        "precompiled/data_top100_$category.json"
                    }
                    val topUrl = "$baseUrl/$path"
                    
                    val request = Request.Builder()
                        .url(topUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: continue
                        val torrents = parseJsonResponse(json)
                        return@withContext Result.success(torrents)
                    } else {
                        lastException = Exception("HTTP ${response.code} from $baseUrl")
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            Result.failure(lastException ?: Exception("Network request failed"))
        }
    }
}

