package com.piratebay.app.util

import android.content.Context
import android.content.SharedPreferences

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("piratebay_search_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 8
    }

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
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
        prefs.edit().putString(KEY_HISTORY, current.joinToString("|||")).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
