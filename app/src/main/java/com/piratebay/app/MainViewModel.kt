package com.piratebay.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piratebay.app.model.TorrentItem
import com.piratebay.app.network.TPBScraper
import com.piratebay.app.network.TranslationService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(
        val torrents: List<TorrentItem>,
        val effectiveQuery: String = "",
        val isFuzzyMatched: Boolean = false
    ) : UiState
    object Empty : UiState
    data class Error(val message: String) : UiState
}

sealed interface SingleEvent {
    data class ShowToast(val message: String) : SingleEvent
}

class MainViewModel(
    private val scraper: TPBScraper = TPBScraper(),
    private val translationService: TranslationService = TranslationService()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<SingleEvent>()
    val eventFlow: SharedFlow<SingleEvent> = _eventFlow.asSharedFlow()

    private var rawTorrents: List<TorrentItem> = emptyList()

    var currentQuery: String = ""
        private set

    var effectiveQuery: String = ""
        private set

    var isFuzzyMatched: Boolean = false
        private set

    var currentCategory: String = "0"
        private set

    var currentSort: Int = 0
        private set

    private var isTop100Mode: Boolean = false

    fun search(query: String, category: String = currentCategory) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return
        }

        currentQuery = trimmedQuery
        currentCategory = category
        isTop100Mode = false
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = scraper.search(trimmedQuery, category)
            result.fold(
                onSuccess = { searchRes ->
                    rawTorrents = searchRes.torrents
                    effectiveQuery = searchRes.effectiveQuery
                    isFuzzyMatched = searchRes.isFuzzyMatched

                    if (searchRes.isFuzzyMatched && searchRes.effectiveQuery.isNotBlank()) {
                        emitEvent("未直接搜到，已为你联想 \"${searchRes.effectiveQuery}\"")
                    }
                    applyFilterAndSort()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "网络请求失败，请检查网络连接")
                }
            )
        }
    }

    fun loadTop100(category: String) {
        currentQuery = ""
        effectiveQuery = ""
        isFuzzyMatched = false
        currentCategory = category
        isTop100Mode = true
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = scraper.getTopTorrents(category)
            result.fold(
                onSuccess = { list ->
                    rawTorrents = list
                    applyFilterAndSort()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "网络请求失败，请检查网络连接")
                }
            )
        }
    }

    fun refresh() {
        if (isTop100Mode) {
            loadTop100(currentCategory)
        } else if (currentQuery.isNotEmpty()) {
            search(currentQuery, currentCategory)
        }
    }

    fun setCategory(category: String) {
        currentCategory = category
        applyFilterAndSort()
    }

    fun setSort(sortType: Int) {
        currentSort = sortType
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        if (rawTorrents.isEmpty()) {
            if (_uiState.value !is UiState.Loading && _uiState.value !is UiState.Idle) {
                _uiState.value = UiState.Empty
            }
            return
        }

        val filtered = filterByCategory(rawTorrents, currentCategory)
        if (filtered.isEmpty()) {
            _uiState.value = UiState.Empty
        } else {
            val sorted = sortTorrents(filtered, currentSort)
            _uiState.value = UiState.Success(
                torrents = sorted,
                effectiveQuery = effectiveQuery,
                isFuzzyMatched = isFuzzyMatched
            )
        }
    }

    private fun filterByCategory(items: List<TorrentItem>, category: String): List<TorrentItem> {
        if (category == "0" || category.isEmpty()) return items

        // 大分类前缀匹配: 200 -> 2xx, 100 -> 1xx, 300 -> 3xx, 400 -> 4xx, 600 -> 6xx
        val prefix = category.firstOrNull()?.toString() ?: return items
        return items.filter { item ->
            item.rawCategoryId == category || item.rawCategoryId.startsWith(prefix)
        }
    }

    fun toggleTranslate(item: TorrentItem) {
        if (item.isTranslating) return

        if (item.isTranslated) {
            // 恢复原文
            updateTorrentItem(item.id) { it.copy(translatedTitle = null) }
            emitEvent("已恢复原文")
            return
        }

        // 开始翻译
        updateTorrentItem(item.id) { it.copy(isTranslating = true) }

        viewModelScope.launch {
            val result = translationService.translate(item.title)
            result.fold(
                onSuccess = { translated ->
                    updateTorrentItem(item.id) {
                        it.copy(translatedTitle = translated, isTranslating = false)
                    }
                    emitEvent("翻译完成")
                },
                onFailure = { error ->
                    updateTorrentItem(item.id) { it.copy(isTranslating = false) }
                    emitEvent("翻译失败: ${error.message}")
                }
            )
        }
    }

    private fun updateTorrentItem(id: String, transform: (TorrentItem) -> TorrentItem) {
        rawTorrents = rawTorrents.map { if (it.id == id) transform(it) else it }
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val updatedList = currentState.torrents.map { if (it.id == id) transform(it) else it }
            _uiState.value = UiState.Success(updatedList)
        }
    }

    private fun sortTorrents(items: List<TorrentItem>, sortType: Int): List<TorrentItem> {
        return when (sortType) {
            1 -> items.sortedBy { it.uploadTimestamp }
            2 -> items.sortedByDescending { it.uploadTimestamp }
            3 -> items.sortedBy { it.sizeBytes }
            4 -> items.sortedByDescending { it.sizeBytes }
            5 -> items.sortedBy { it.seedersCount }
            6 -> items.sortedByDescending { it.seedersCount }
            else -> items
        }
    }

    private fun emitEvent(message: String) {
        viewModelScope.launch {
            _eventFlow.emit(SingleEvent.ShowToast(message))
        }
    }
}
