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

    private val _qualityChips = MutableStateFlow<List<String>>(emptyList())
    val qualityChips: StateFlow<List<String>> = _qualityChips.asStateFlow()

    private val _selectedQualityChip = MutableStateFlow<String?>(null)
    val selectedQualityChip: StateFlow<String?> = _selectedQualityChip.asStateFlow()

    private var parsedQuery: com.piratebay.app.util.ParsedQuery? = null

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
        _selectedQualityChip.value = null
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            // 1. 智能语义解析（提取季数、集数、分辨率与中文字符）
            val parsed = com.piratebay.app.util.QueryAnalyzer.parse(trimmedQuery)
            parsedQuery = parsed

            var searchTarget = parsed.coreQuery
            var translatedNote = ""

            // 2. 中文自动翻译转换为英文原名
            if (parsed.isChinese && translationService.isConfigured()) {
                val transResult = translationService.translate(parsed.coreQuery, from = "zh", to = "en")
                transResult.onSuccess { enQuery ->
                    val cleanEn = enQuery.trim().lowercase(java.util.Locale.ROOT)
                    if (cleanEn.isNotBlank() && cleanEn != parsed.coreQuery.lowercase(java.util.Locale.ROOT)) {
                        searchTarget = cleanEn
                        translatedNote = enQuery
                    }
                }
            }

            // 3. 执行检索
            val result = scraper.search(searchTarget, category)
            result.fold(
                onSuccess = { searchRes ->
                    rawTorrents = searchRes.torrents
                    effectiveQuery = if (translatedNote.isNotBlank()) translatedNote else searchRes.effectiveQuery
                    isFuzzyMatched = searchRes.isFuzzyMatched || translatedNote.isNotBlank()

                    if (translatedNote.isNotBlank()) {
                        emitEvent("已自动为你识别英文原名 \"$translatedNote\"")
                    } else if (searchRes.isFuzzyMatched && searchRes.effectiveQuery.isNotBlank()) {
                        emitEvent("未直接搜到，已为你联想 \"${searchRes.effectiveQuery}\"")
                    }

                    // 4. 提取可用的规格微标签 (4K, 1080p, S01, S02 等)
                    _qualityChips.value = com.piratebay.app.util.QueryAnalyzer.extractFilterChips(rawTorrents)

                    applyFilterAndSort()
                },
                onFailure = { error ->
                    _qualityChips.value = emptyList()
                    _uiState.value = UiState.Error(error.message ?: "网络请求失败，请检查网络连接")
                }
            )
        }
    }

    fun loadTop100(category: String) {
        currentQuery = ""
        effectiveQuery = ""
        isFuzzyMatched = false
        _selectedQualityChip.value = null
        parsedQuery = null
        currentCategory = category
        isTop100Mode = true
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = scraper.getTopTorrents(category)
            result.fold(
                onSuccess = { list ->
                    rawTorrents = list
                    _qualityChips.value = com.piratebay.app.util.QueryAnalyzer.extractFilterChips(rawTorrents)
                    applyFilterAndSort()
                },
                onFailure = { error ->
                    _qualityChips.value = emptyList()
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

    fun selectQualityChip(chip: String?) {
        _selectedQualityChip.value = if (_selectedQualityChip.value == chip) null else chip
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        if (rawTorrents.isEmpty()) {
            if (_uiState.value !is UiState.Loading && _uiState.value !is UiState.Idle) {
                _uiState.value = UiState.Empty
            }
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // 1. 频道大分类过滤
            var filtered = filterByCategory(rawTorrents, currentCategory)

            // 2. 搜索词中自带的季数/分辨率条件过滤
            parsedQuery?.let { p ->
                if (p.hasConstraints && _selectedQualityChip.value == null) {
                    val constrained = com.piratebay.app.util.QueryAnalyzer.filterByParsedConstraints(filtered, p)
                    if (constrained.isNotEmpty()) {
                        filtered = constrained
                    }
                }
            }

            // 3. 用户手动点击的动态规格微标签过滤 (4K, 1080p, S04 等)
            _selectedQualityChip.value?.let { chip ->
                val chipFiltered = com.piratebay.app.util.QueryAnalyzer.filterByQualityChip(filtered, chip)
                if (chipFiltered.isNotEmpty()) {
                    filtered = chipFiltered
                }
            }

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
