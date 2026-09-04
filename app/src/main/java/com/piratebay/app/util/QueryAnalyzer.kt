package com.piratebay.app.util

import com.piratebay.app.model.TorrentItem
import java.util.Locale
import java.util.regex.Pattern

data class ParsedQuery(
    val originalQuery: String,
    val coreQuery: String,
    val season: Int? = null,
    val episode: Int? = null,
    val resolution: String? = null,
    val isChinese: Boolean = false,
    val translatedQuery: String? = null
) {
    val hasConstraints: Boolean
        get() = season != null || episode != null || resolution != null
}

object QueryAnalyzer {

    private val SEASON_REGEX = Pattern.compile(
        """(?i)(?:season\s*(\d{1,2})|s(\d{1,2})|第\s*([0-9一二三四五六七八九十]+)\s*季)"""
    )

    private val EPISODE_REGEX = Pattern.compile(
        """(?i)(?:episode\s*(\d{1,3})|ep?(\d{1,3})|第\s*([0-9一二三四五六七八九十]+)\s*[集话回])"""
    )

    private val RESOLUTION_REGEX = Pattern.compile(
        """(?i)\b(4k|2160p|1080p|720p|480p|uhd|fhd|hd)\b"""
    )

    private val CHINESE_NUMBER_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10
    )

    fun isChinese(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fa5' }
    }

    fun parse(rawQuery: String): ParsedQuery {
        var clean = rawQuery.trim()
            .replace("\"", "")
            .replace("“", "")
            .replace("”", "")
            .replace("'", "")
            .replace(Regex("\\s+"), " ")

        val isZh = isChinese(clean)

        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var resStr: String? = null

        // 提取 Season
        val seasonMatcher = SEASON_REGEX.matcher(clean)
        if (seasonMatcher.find()) {
            val s1 = seasonMatcher.group(1)
            val s2 = seasonMatcher.group(2)
            val s3 = seasonMatcher.group(3)
            seasonNum = s1?.toIntOrNull() ?: s2?.toIntOrNull() ?: s3?.let { CHINESE_NUMBER_MAP[it] ?: it.toIntOrNull() }
            clean = seasonMatcher.replaceAll(" ").trim()
        }

        // 提取 Episode
        val epMatcher = EPISODE_REGEX.matcher(clean)
        if (epMatcher.find()) {
            val e1 = epMatcher.group(1)
            val e2 = epMatcher.group(2)
            val e3 = epMatcher.group(3)
            episodeNum = e1?.toIntOrNull() ?: e2?.toIntOrNull() ?: e3?.let { CHINESE_NUMBER_MAP[it] ?: it.toIntOrNull() }
            clean = epMatcher.replaceAll(" ").trim()
        }

        // 提取分辨率
        val resMatcher = RESOLUTION_REGEX.matcher(clean)
        if (resMatcher.find()) {
            val rawRes = resMatcher.group(1)?.lowercase(Locale.ROOT) ?: ""
            resStr = when (rawRes) {
                "4k", "uhd", "2160p" -> "2160p"
                "1080p", "fhd" -> "1080p"
                "720p", "hd" -> "720p"
                else -> rawRes
            }
            clean = resMatcher.replaceAll(" ").trim()
        }

        clean = clean.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) {
            clean = rawQuery.trim()
        }

        return ParsedQuery(
            originalQuery = rawQuery,
            coreQuery = clean.lowercase(Locale.ROOT),
            season = seasonNum,
            episode = episodeNum,
            resolution = resStr,
            isChinese = isZh
        )
    }

    /**
     * 对种子列表应用提取出来的季数/集数/分辨率限制
     */
    fun filterByParsedConstraints(items: List<TorrentItem>, parsed: ParsedQuery): List<TorrentItem> {
        var result = items

        // 1. 过滤 Season
        parsed.season?.let { s ->
            val sPad = String.format(Locale.US, "%02d", s)
            val sRegex = Regex("""(?i)\b(s0?$s|s$sPad|season\s*0?$s)\b""")
            val filtered = result.filter { sRegex.containsMatchIn(it.title) }
            if (filtered.isNotEmpty()) {
                result = filtered
            }
        }

        // 2. 过滤 Episode
        parsed.episode?.let { e ->
            val ePad = String.format(Locale.US, "%02d", e)
            val eRegex = Regex("""(?i)\b(e0?$e|e$ePad|ep0?$e|episode\s*0?$e)\b""")
            val filtered = result.filter { eRegex.containsMatchIn(it.title) }
            if (filtered.isNotEmpty()) {
                result = filtered
            }
        }

        // 3. 过滤分辨率
        parsed.resolution?.let { res ->
            val resRegex = when (res) {
                "2160p" -> Regex("""(?i)\b(2160p|4k|uhd)\b""")
                "1080p" -> Regex("""(?i)\b(1080p|1080i|fhd)\b""")
                "720p" -> Regex("""(?i)\b(720p|hd)\b""")
                else -> Regex("""(?i)\b$res\b""")
            }
            val filtered = result.filter { resRegex.containsMatchIn(it.title) }
            if (filtered.isNotEmpty()) {
                result = filtered
            }
        }

        return result
    }

    /**
     * 从当前一批种子文件名中，提取最常见可供点击的规格微标签 (Quality & Season Chips)
     */
    fun extractFilterChips(items: List<TorrentItem>): List<String> {
        if (items.isEmpty()) return emptyList()

        val chips = mutableListOf<String>()
        val titles = items.map { it.title.lowercase(Locale.ROOT) }

        // 检查 4K / 2160p
        if (titles.any { it.contains("2160p") || it.contains("4k") || it.contains("uhd") }) {
            chips.add("4K / 2160p")
        }
        // 检查 1080p
        if (titles.any { it.contains("1080p") || it.contains("1080i") }) {
            chips.add("1080p")
        }
        // 检查 720p
        if (titles.any { it.contains("720p") }) {
            chips.add("720p")
        }

        // 检查季数 (S01, S02, S03, S04, S05 ...)
        for (season in 1..9) {
            val sPad = String.format(Locale.US, "%02d", season)
            val sRegex = Regex("""(?i)\b(s$season|s$sPad|season\s*$season)\b""")
            if (titles.any { sRegex.containsMatchIn(it) }) {
                chips.add("S$sPad")
            }
        }

        // 检查编码 / 特殊格式
        if (titles.any { it.contains("x265") || it.contains("hevc") }) {
            chips.add("x265 / HEVC")
        }
        if (titles.any { it.contains("bluray") || it.contains("bdrip") }) {
            chips.add("BluRay")
        }
        if (titles.any { it.contains("complete") || it.contains("pack") }) {
            chips.add("整季合集")
        }
        if (titles.any { it.contains("fitgirl") }) {
            chips.add("FitGirl")
        }

        return chips
    }

    /**
     * 点击动态规格微标签时的快速过滤
     */
    fun filterByQualityChip(items: List<TorrentItem>, chip: String): List<TorrentItem> {
        val regex = when (chip) {
            "4K / 2160p" -> Regex("""(?i)\b(2160p|4k|uhd)\b""")
            "1080p" -> Regex("""(?i)\b(1080p|1080i|fhd)\b""")
            "720p" -> Regex("""(?i)\b(720p|hd)\b""")
            "x265 / HEVC" -> Regex("""(?i)\b(x265|hevc|h265)\b""")
            "BluRay" -> Regex("""(?i)\b(bluray|bdrip|brrip)\b""")
            "整季合集" -> Regex("""(?i)\b(complete|pack|all\.seasons|seasons?\s*[1-9]-[1-9])\b""")
            "FitGirl" -> Regex("""(?i)\bfitgirl\b""")
            else -> {
                if (chip.startsWith("S") && chip.length == 3) {
                    val num = chip.substring(1).toIntOrNull()
                    if (num != null) {
                        Regex("""(?i)\b(s0?$num|s${chip.substring(1)}|season\s*0?$num)\b""")
                    } else {
                        Regex("""(?i)\b$chip\b""")
                    }
                } else {
                    Regex("""(?i)\b$chip\b""")
                }
            }
        }
        return items.filter { regex.containsMatchIn(it.title) }
    }
}
