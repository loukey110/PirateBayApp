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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    private val apiUrl = "https://apibay.org"

    private val webMirrors = listOf(
        "https://thepiratebay7.com",
        "https://thepiratebay11.com",
        "https://pirateproxylive.org"
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

    data class SearchResult(
        val torrents: List<TorrentItem>,
        val effectiveQuery: String,
        val isFuzzyMatched: Boolean = false
    )

    fun normalizeQuery(query: String): String {
        val cleaned = query.trim()
            .replace("\"", "")
            .replace("“", "")
            .replace("”", "")
            .replace("'", "")
            .replace(Regex("\\s+"), " ")
        return cleaned.lowercase(Locale.ROOT)
    }

    fun generateFuzzyCandidates(query: String): List<String> {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return emptyList()

        val words = q.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val candidates = mutableListOf<String>()

        // 1. 词尾单复数形态扩展 (e.g. "the boy" -> "the boys", "game of throne" -> "game of thrones")
        val lastWord = words.last()
        if (lastWord.length > 3 && lastWord.endsWith("s")) {
            // 复数转单数 (e.g. "the boys" -> "the boy", "thrones" -> "throne")
            candidates.add((words.dropLast(1) + lastWord.dropLast(1)).joinToString(" "))
            if (lastWord.length > 4 && lastWord.endsWith("es")) {
                candidates.add((words.dropLast(1) + lastWord.dropLast(2)).joinToString(" "))
            }
        } else if (lastWord.isNotEmpty()) {
            // 单数转复数 (e.g. "the boy" -> "the boys", "throne" -> "thrones")
            candidates.add((words.dropLast(1) + "${lastWord}s").joinToString(" "))
            if (lastWord.endsWith("o") || lastWord.endsWith("ch") || lastWord.endsWith("sh") ||
                lastWord.endsWith("ss") || lastWord.endsWith("x") || lastWord.endsWith("z")
            ) {
                candidates.add((words.dropLast(1) + "${lastWord}es").joinToString(" "))
            }
        }

        // 2. 冠词/常用停用词剥离 (e.g. "the walking dead" -> "walking dead")
        val prefixes = listOf("the ", "a ", "an ")
        for (prefix in prefixes) {
            if (q.startsWith(prefix)) {
                val withoutPrefix = q.removePrefix(prefix).trim()
                if (withoutPrefix.isNotEmpty()) {
                    candidates.add(withoutPrefix)
                    val subWords = withoutPrefix.split(" ").filter { it.isNotBlank() }
                    if (subWords.isNotEmpty()) {
                        val subLast = subWords.last()
                        if (!subLast.endsWith("s")) {
                            candidates.add((subWords.dropLast(1) + "${subLast}s").joinToString(" "))
                        }
                    }
                }
            }
        }

        // 3. 标点、连字符与空格变体 (e.g. "spider-man" <-> "spiderman", "spider man")
        if (q.contains("-") || q.contains(".") || q.contains("_")) {
            candidates.add(q.replace("-", " ").replace(".", " ").replace("_", " ").replace(Regex("\\s+"), " ").trim())
            candidates.add(q.replace("-", "").replace(".", "").replace("_", ""))
        } else if (q.contains(" ")) {
            candidates.add(q.replace(" ", ""))
        }

        // 4. 多词短语前缀/后缀子集尝试
        if (words.size > 2) {
            candidates.add(words.drop(1).joinToString(" "))
            candidates.add(words.dropLast(1).joinToString(" "))
        }

        // 去重并过滤掉原词
        val seen = mutableSetOf(q)
        val finalCandidates = mutableListOf<String>()
        for (cand in candidates) {
            val normalized = cand.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
            if (normalized.isNotEmpty() && seen.add(normalized)) {
                finalCandidates.add(normalized)
            }
        }
        return finalCandidates
    }

    private fun fetchTorrentsForQuery(normalizedQuery: String): List<TorrentItem> {
        val encodedQuery = URLEncoder.encode(normalizedQuery, "UTF-8").replace("+", "%20")
        val isSingleWord = !normalizedQuery.contains(" ")

        // 海盗湾 apibay 接口严重怪癖：
        // 1. 单词检索（如 futurama, avatar, spiderman）若带 cat=0 会返回 0 条，必须使用 cat=（不指定分类）才能召回 100 条完整资源；
        // 2. 多词检索（如 the boys, rick and morty）使用 cat=0 才能正确命中。
        // 因此采取自适应双重探活策略：单词优先使用 cat=，多词优先使用 cat=0，未命中时立即自动回退重试另一种。
        val catParams = if (isSingleWord) listOf("&cat=", "&cat=0") else listOf("&cat=0", "&cat=")

        for (catParam in catParams) {
            val url = "$apiUrl/q.php?q=$encodedQuery$catParam"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (!json.isNullOrBlank()) {
                        val results = parseJsonResponse(json)
                        if (results.isNotEmpty()) {
                            return results
                        }
                    }
                }
            } catch (e: Exception) {
                // 网络异常继续尝试候选
            }
        }
        return emptyList()
    }

    private fun parseSizeToBytes(sizeStr: String): Long {
        val parts = sizeStr.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return 0L
        return try {
            val num = parts[0].toDouble()
            val unit = if (parts.size > 1) parts[1].uppercase(Locale.ROOT) else "B"
            when {
                unit.contains("T") -> (num * 1024 * 1024 * 1024 * 1024).toLong()
                unit.contains("G") -> (num * 1024 * 1024 * 1024).toLong()
                unit.contains("M") -> (num * 1024 * 1024).toLong()
                unit.contains("K") -> (num * 1024).toLong()
                else -> num.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseMirrorHtml(html: String): List<TorrentItem> {
        val torrents = mutableListOf<TorrentItem>()
        val rowRegex = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("""<div class="detName">\s*<a href="[^"]*/torrent/(\d+)/[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val magnetRegex = Regex("""href="(magnet:\?xt=urn:btih:([a-zA-Z0-9]+)[^"]*)"""")
        val catRegex = Regex("""href="[^"]*/browse/(\d+)"""")
        val sizeRegex = Regex("""Size\s+([0-9.]+\s*(?:&nbsp;|\s*)[KMGTPE]?i?B)""")
        val numsRegex = Regex("""<td align="right">(\d+)</td>""")

        val rows = rowRegex.findAll(html)
        for (rowMatch in rows) {
            val row = rowMatch.groupValues[1]
            val titleMatch = titleRegex.find(row) ?: continue
            val magnetMatch = magnetRegex.find(row) ?: continue

            val id = titleMatch.groupValues[1]
            val rawTitle = titleMatch.groupValues[2].trim()
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&nbsp;", " ")
            val magnetLink = magnetMatch.groupValues[1]
            val infoHash = magnetMatch.groupValues[2]

            val catMatches = catRegex.findAll(row).toList()
            val rawCatId = if (catMatches.isNotEmpty()) catMatches.last().groupValues[1] else "0"
            val categoryName = categoryMap[rawCatId] ?: "Other"

            val sizeMatch = sizeRegex.find(row)
            val sizeStr = sizeMatch?.groupValues?.get(1)?.replace("&nbsp;", " ")?.trim() ?: "0 B"
            val sizeBytes = parseSizeToBytes(sizeStr)

            val numsMatches = numsRegex.findAll(row).toList()
            val seeders = if (numsMatches.isNotEmpty()) numsMatches[0].groupValues[1].toIntOrNull() ?: 0 else 0
            val leechers = if (numsMatches.size > 1) numsMatches[1].groupValues[1].toIntOrNull() ?: 0 else 0

            val dateMatch = Regex("""Uploaded\s+([^,]+),""").find(row)
            val dateStr = dateMatch?.groupValues?.get(1)?.replace("&nbsp;", " ")?.trim() ?: ""
            val uploadTs = parseUploadDateToTimestamp(dateStr)

            torrents.add(
                TorrentItem(
                    id = id,
                    infoHash = infoHash,
                    title = rawTitle,
                    magnetLink = magnetLink,
                    sizeBytes = sizeBytes,
                    seedersCount = seeders,
                    leechersCount = leechers,
                    uploadTimestamp = uploadTs,
                    uploader = "VIP/Member",
                    category = categoryName,
                    rawCategoryId = rawCatId
                )
            )
        }
        return torrents
    }

    private fun parseUploadDateToTimestamp(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis() / 1000L
        return try {
            val now = Calendar.getInstance()
            when {
                dateStr.startsWith("Y-day", ignoreCase = true) -> {
                    now.add(Calendar.DAY_OF_YEAR, -1)
                    now.timeInMillis / 1000L
                }
                dateStr.contains("mins ago", ignoreCase = true) -> {
                    val mins = dateStr.split(" ")[0].toIntOrNull() ?: 1
                    now.add(Calendar.MINUTE, -mins)
                    now.timeInMillis / 1000L
                }
                dateStr.contains("<b>", ignoreCase = true) || dateStr.contains(":") && !dateStr.contains("-") -> {
                    now.timeInMillis / 1000L
                }
                else -> {
                    // 格式如 "09-13 2024" 或 "09-13 11:34"
                    val parts = dateStr.split(" ")
                    if (parts.size >= 2) {
                        val md = parts[0].split("-")
                        val m = md.getOrNull(0)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)
                        val d = md.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                        val yr = parts[1].toIntOrNull() ?: now.get(Calendar.YEAR)
                        val cal = Calendar.getInstance()
                        cal.set(yr, m - 1, d)
                        cal.timeInMillis / 1000L
                    } else {
                        System.currentTimeMillis() / 1000L
                    }
                }
            }
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000L
        }
    }

    private fun fetchTopFromWebMirrors(category: String = "0"): List<TorrentItem> {
        val catPath = if (category == "0" || category.isEmpty()) "all" else category
        for (mirror in webMirrors) {
            val url = "$mirror/top/$catPath"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: continue
                    val items = parseMirrorHtml(html)
                    if (items.isNotEmpty()) {
                        return items
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个镜像
            }
        }
        return emptyList()
    }

    private fun fetchFromWebMirrors(normalizedQuery: String, category: String = "0"): List<TorrentItem> {
        val encodedQuery = URLEncoder.encode(normalizedQuery, "UTF-8").replace("+", "%20")
        val catTarget = if (category.isEmpty()) "0" else category

        for (mirror in webMirrors) {
            val url = "$mirror/search/$encodedQuery/1/99/$catTarget"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: continue
                    val items = parseMirrorHtml(html)
                    if (items.isNotEmpty()) {
                        return items
                    }
                }
            } catch (e: Exception) {
                // 自动尝试下一个活跃镜像节点
            }
        }
        return emptyList()
    }

    suspend fun search(query: String, category: String = "0"): Result<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val normalized = normalizeQuery(query)
                if (normalized.isEmpty()) {
                    return@withContext Result.success(SearchResult(emptyList(), "", false))
                }

                // 1. 先用 JSON API (apibay.org) 进行轻量检索
                var directResults = fetchTorrentsForQuery(normalized)

                // 2. 若 API 返回空（如 tokyo hot, batman, matrix 等 Sphinx 引擎溢出词），自动无缝无感切换至经典网页镜像爬虫！
                if (directResults.isEmpty()) {
                    directResults = fetchFromWebMirrors(normalized, category)
                }

                if (directResults.isNotEmpty()) {
                    return@withContext Result.success(SearchResult(directResults, normalized, false))
                }

                // 3. 自动触发智能模糊词与衍生词检索
                val candidates = generateFuzzyCandidates(normalized)
                for (cand in candidates) {
                    var candResults = fetchTorrentsForQuery(cand)
                    if (candResults.isEmpty()) {
                        candResults = fetchFromWebMirrors(cand, category)
                    }
                    if (candResults.isNotEmpty()) {
                        return@withContext Result.success(SearchResult(candResults, cand, true))
                    }
                }

                // 4. 若均未命中，返回空结果
                Result.success(SearchResult(emptyList(), normalized, false))
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
                        category = categoryName,
                        rawCategoryId = category
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
            try {
                val path = if (category == "0") {
                    "precompiled/data_top100_all.json"
                } else {
                    "precompiled/data_top100_$category.json"
                }
                val topUrl = "$apiUrl/$path"
                
                val request = Request.Builder()
                    .url(topUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = response.body?.string()
                        if (!json.isNullOrBlank()) {
                            val torrents = parseJsonResponse(json)
                            if (torrents.isNotEmpty()) {
                                return@withContext Result.success(torrents)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // API 失败，降级到镜像站抓取
                }
                
                // 降级：从网页镜像站拉取 Top 100
                val mirrorTop = fetchTopFromWebMirrors(category)
                if (mirrorTop.isNotEmpty()) {
                    return@withContext Result.success(mirrorTop)
                }

                Result.failure(Exception("无法获取 Top 100 数据，请检查网络"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

