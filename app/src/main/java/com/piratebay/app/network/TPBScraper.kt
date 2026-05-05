package com.piratebay.app.network

import com.piratebay.app.model.TorrentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class TPBScraper {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    private val baseUrl = "https://thepiratebay.org"
    
    private val mirrors = listOf(
        "https://thepiratebay.org",
        "https://thepiratebay10.org",
        "https://pirateproxy.live",
        "https://thepiratebay.party"
    )
    
    suspend fun search(query: String, category: String = "0"): Result<List<TorrentItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/search/$query/1/99/$category"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val html = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val torrents = parseSearchResults(html)
                Result.success(torrents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun parseSearchResults(html: String): List<TorrentItem> {
        val torrents = mutableListOf<TorrentItem>()
        val doc = Jsoup.parse(html)
        
        val rows = doc.select("table#searchResult tr")
        
        for (row in rows) {
            try {
                val titleElement = row.select("div.detName a.detLink").first()
                val magnetElement = row.select("a[href^=magnet:]").first()
                
                if (titleElement == null || magnetElement == null) continue
                
                val title = titleElement.text()
                val magnetLink = magnetElement.attr("href")
                
                val detailsText = row.select("font.detDesc").text()
                
                val sizeMatch = Regex("Size (.*?),").find(detailsText)
                val size = sizeMatch?.groupValues?.get(1)?.replace("iB", "B") ?: "Unknown"
                
                val uploadMatch = Regex("Uploaded (.*?),").find(detailsText)
                val uploadDate = uploadMatch?.groupValues?.get(1) ?: "Unknown"
                
                val uploaderMatch = Regex("ULed by (.*?)$").find(detailsText)
                val uploader = uploaderMatch?.groupValues?.get(1) ?: "Anonymous"
                
                val tds = row.select("td")
                val seeders = if (tds.size >= 3) tds[tds.size - 2].text() else "0"
                val leechers = if (tds.size >= 4) tds[tds.size - 1].text() else "0"
                
                val categoryElement = row.select("td a").first()
                val category = categoryElement?.text() ?: "Other"
                
                torrents.add(
                    TorrentItem(
                        title = title,
                        magnetLink = magnetLink,
                        size = size,
                        seeders = seeders,
                        leechers = leechers,
                        uploadDate = uploadDate,
                        uploader = uploader,
                        category = category
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
        
        return torrents
    }
    
    suspend fun getTopTorrents(category: String = "all"): Result<List<TorrentItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val categoryPath = when (category) {
                    "video" -> "201"
                    "audio" -> "101"
                    "applications" -> "300"
                    "games" -> "400"
                    else -> "0"
                }
                
                val url = "$baseUrl/top/$categoryPath"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val html = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val torrents = parseSearchResults(html)
                Result.success(torrents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
