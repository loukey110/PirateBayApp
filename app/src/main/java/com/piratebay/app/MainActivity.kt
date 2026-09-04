package com.piratebay.app

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.piratebay.app.adapter.TorrentAdapter
import com.piratebay.app.databinding.ActivityMainBinding
import com.piratebay.app.model.TorrentItem
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: TorrentAdapter

    private val categories = mapOf(
        "全部" to "0",
        "视频" to "200",
        "音频" to "100",
        "应用" to "300",
        "游戏" to "400",
        "其他" to "600"
    )

    private val top100Categories = listOf(
        "全部 Top 100" to "0",
        "—— 音频 ——" to "HEADER",
        "音乐" to "101",
        "有声书" to "102",
        "音效" to "103",
        "FLAC" to "104",
        "其他音频" to "199",
        "—— 视频 ——" to "HEADER",
        "电影" to "201",
        "电影 DVDR" to "202",
        "音乐视频" to "203",
        "电影片段" to "204",
        "电视剧" to "205",
        "手持设备视频" to "206",
        "HD 电影" to "207",
        "HD 电视剧" to "208",
        "3D" to "209",
        "其他视频" to "299",
        "—— 应用 ——" to "HEADER",
        "应用程序" to "301",
        "游戏 (Applications下)" to "302",
        "手持设备应用" to "303",
        "iOS 应用" to "304",
        "Android 应用" to "305",
        "其他系统" to "399",
        "—— 游戏 ——" to "HEADER",
        "游戏 (Games下)" to "401",
        "PC 游戏" to "402",
        "PS 游戏" to "403",
        "XBOX360" to "404",
        "Wii" to "405",
        "手持游戏" to "406",
        "iOS 游戏" to "407",
        "Android 游戏" to "408",
        "其他游戏" to "499",
        "—— 成人 ——" to "HEADER",
        "成人视频" to "501",
        "成人 DVDR" to "502",
        "成人图片" to "503",
        "成人游戏" to "504",
        "其他成人" to "599",
        "—— 其他 ——" to "HEADER",
        "电子书" to "601",
        "漫画" to "602",
        "图片" to "603",
        "封面" to "604",
        "Physibles" to "605",
        "其他" to "699"
    )

    private val chipButtons = mutableListOf<androidx.appcompat.widget.AppCompatButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCategoryChips()
        setupSortButton()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupCategoryChips() {
        binding.categoryChipsContainer.removeAllViews()
        chipButtons.clear()

        val categoryList = listOf(
            "🔥 全部" to "0",
            "🎬 电影视频" to "200",
            "🎵 音乐音频" to "100",
            "📱 应用程序" to "300",
            "🎮 游戏娱乐" to "400",
            "📦 其他资源" to "600"
        )

        for ((index, pair) in categoryList.withIndex()) {
            val (name, id) = pair
            val chip = androidx.appcompat.widget.AppCompatButton(this).apply {
                text = name
                textSize = 12f
                isAllCaps = false
                includeFontPadding = false
                minHeight = 0
                minWidth = 0
                setPadding(dpToPx(14), 0, dpToPx(14), 0)

                val layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(32)
                ).apply {
                    marginEnd = dpToPx(8)
                }
                this.layoutParams = layoutParams

                setOnClickListener {
                    selectCategoryChip(index, id)
                }
            }

            chipButtons.add(chip)
            binding.categoryChipsContainer.addView(chip)
        }

        updateChipStyles(0)
    }

    private fun selectCategoryChip(selectedIndex: Int, categoryId: String) {
        updateChipStyles(selectedIndex)
        viewModel.setCategory(categoryId)
    }

    private fun updateChipStyles(selectedIndex: Int) {
        for ((i, chip) in chipButtons.withIndex()) {
            if (i == selectedIndex) {
                chip.setBackgroundResource(R.drawable.chip_background_selected)
                chip.setTextColor(android.graphics.Color.WHITE)
                chip.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                chip.setBackgroundResource(R.drawable.chip_background_unselected)
                chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
                chip.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private val sortOptions = listOf(
        "默认排序",
        "时间 ↑ (由旧到新)",
        "时间 ↓ (最新发布)",
        "大小 ↑ (由小到大)",
        "大小 ↓ (体积最大)",
        "做种 ↑ (较少做种)",
        "做种 ↓ (最多做种)"
    )

    private val sortButtonLabels = listOf(
        "⚡ 默认排序",
        "📅 时间 ↑",
        "📅 最新发布",
        "💾 大小 ↑",
        "💾 文件最大",
        "🔥 做种 ↑",
        "🔥 最多做种"
    )

    private fun setupSortButton() {
        binding.sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        AlertDialog.Builder(this)
            .setTitle("选择排序方式")
            .setSingleChoiceItems(sortOptions.toTypedArray(), viewModel.currentSort) { dialog, which ->
                viewModel.setSort(which)
                binding.sortButton.text = "${sortButtonLabels[which]} ▾"
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = TorrentAdapter(
            onTranslateClick = { torrent ->
                viewModel.toggleTranslate(torrent)
            },
            onItemClick = { torrent ->
                openMagnetLink(torrent.magnetLink)
            },
            onCopyClick = { torrent ->
                copyToClipboard(torrent.magnetLink, "磁力链接")
                Toast.makeText(this, "磁力链接已复制", Toast.LENGTH_SHORT).show()
            },
            onShareClick = { torrent ->
                shareMagnetLink(torrent.magnetLink, torrent.title)
            }
        )
        binding.torrentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.torrentsRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.searchButton.setOnClickListener {
            performSearch()
        }

        binding.topButton.setOnClickListener {
            showTop100CategoryDialog()
        }

        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.orange_primary,
            R.color.cyan_accent
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderUiState(state)
                    }
                }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is SingleEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(state: UiState) {
        binding.swipeRefreshLayout.isRefreshing = false

        when (state) {
            is UiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = "输入关键词搜索或点击 Top 100"
                binding.errorView.visibility = View.GONE
                binding.torrentsRecyclerView.visibility = View.GONE
                binding.statusSummaryTextView.text = "准备就绪 · 输入关键词搜索"
            }
            is UiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                binding.errorView.visibility = View.GONE
                binding.torrentsRecyclerView.visibility = View.GONE
                binding.statusSummaryTextView.text = "正在全网检索种子资源..."
            }
            is UiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
                binding.errorView.visibility = View.GONE
                binding.torrentsRecyclerView.visibility = View.VISIBLE
                if (state.isFuzzyMatched && state.effectiveQuery.isNotBlank()) {
                    binding.statusSummaryTextView.text = "智能匹配 \"${state.effectiveQuery}\" · 找到 ${state.torrents.size} 条结果"
                } else {
                    binding.statusSummaryTextView.text = "找到 ${state.torrents.size} 条资源结果"
                }
                adapter.submitList(state.torrents)
            }
            is UiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = "没有找到结果\n请尝试更换关键词或分类"
                binding.errorView.visibility = View.GONE
                binding.torrentsRecyclerView.visibility = View.GONE
                binding.statusSummaryTextView.text = "共找到 0 条资源结果"
            }
            is UiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
                binding.errorView.text = state.message
                binding.errorView.visibility = View.VISIBLE
                binding.torrentsRecyclerView.visibility = View.GONE
                binding.statusSummaryTextView.text = "加载失败"
            }
        }
    }

    private fun performSearch() {
        hideKeyboard()
        val query = binding.searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            binding.searchEditText.error = "请输入搜索关键词"
            return
        }
        viewModel.search(query)
    }

    private fun showTop100CategoryDialog() {
        val displayItems = top100Categories.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择 Top 100 分类")
            .setItems(displayItems) { dialog, which ->
                val selectedCategory = top100Categories[which].second
                if (selectedCategory != "HEADER") {
                    viewModel.loadTop100(selectedCategory)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openMagnetLink(magnetLink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetLink)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            copyToClipboard(magnetLink, "磁力链接")
            Toast.makeText(this, "未检测到支持磁力的客户端，已复制链接到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            copyToClipboard(magnetLink, "磁力链接")
            Toast.makeText(this, "无法启动外部应用，已复制链接到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun shareMagnetLink(magnetLink: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, magnetLink)
        }
        val chooserIntent = Intent.createChooser(intent, "分享磁力链接")
        startActivity(chooserIntent)
    }

    private fun hideKeyboard() {
        val currentFocusView = currentFocus ?: binding.root
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
    }
}

