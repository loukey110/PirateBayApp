package com.piratebay.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("piratebay_search_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 8
    }

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            // 解析失败（可能是旧版本分隔符遗留）则返回空列表，并自动在下一次写入时修正
        }
        return list
    }

    fun addSearchQuery(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = getHistory().toMutableList()
        current.remove(q)
        current.add(0, q)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.size - 1)
        }
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
