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

    suspend fun search(query: String, category: String = "0"): Result<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val normalized = normalizeQuery(query)
                if (normalized.isEmpty()) {
                    return@withContext Result.success(SearchResult(emptyList(), "", false))
                }

                // 1. 精确规范化查询
                val directResults = fetchTorrentsForQuery(normalized)
                if (directResults.isNotEmpty()) {
                    return@withContext Result.success(SearchResult(directResults, normalized, false))
                }

                // 2. 自动触发智能模糊词与衍生词检索
                val candidates = generateFuzzyCandidates(normalized)
                for (cand in candidates) {
                    val candResults = fetchTorrentsForQuery(cand)
                    if (candResults.isNotEmpty()) {
                        return@withContext Result.success(SearchResult(candResults, cand, true))
                    }
                }

                // 3. 若均未命中，返回空结果
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

