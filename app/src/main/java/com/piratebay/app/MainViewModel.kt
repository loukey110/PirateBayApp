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
    data class Success(val torrents: List<TorrentItem>) : UiState
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

        executeFetch { scraper.search(trimmedQuery, category) }
    }

    fun loadTop100(category: String) {
        currentQuery = ""
        currentCategory = category
        isTop100Mode = true

        executeFetch { scraper.getTopTorrents(category) }
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
    }

    fun setSort(sortType: Int) {
        currentSort = sortType
        if (rawTorrents.isNotEmpty()) {
            val sortedList = sortTorrents(rawTorrents, sortType)
            _uiState.value = UiState.Success(sortedList)
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

    private fun executeFetch(fetcher: suspend () -> Result<List<TorrentItem>>) {
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = fetcher()
            result.fold(
                onSuccess = { list ->
                    rawTorrents = list
                    if (list.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        val sorted = sortTorrents(list, currentSort)
                        _uiState.value = UiState.Success(sorted)
                    }
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "网络请求失败，请检查网络连接")
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
