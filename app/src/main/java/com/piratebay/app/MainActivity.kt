package com.piratebay.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.piratebay.app.adapter.TorrentAdapter
import com.piratebay.app.model.TorrentItem
import com.piratebay.app.network.TPBScraper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var scraper: TPBScraper
    private lateinit var adapter: TorrentAdapter
    
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var topButton: Button
    private lateinit var categorySpinner: Spinner
    private lateinit var sortSpinner: Spinner
    private lateinit var torrentsRecyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var errorView: TextView
    
    private var currentQuery = ""
    private var currentCategory = "0"
    private var currentSort = 0
    private var currentTorrents: List<TorrentItem> = emptyList()
    
    private val categories = mapOf(
        "全部" to "0",
        "视频" to "200",
        "音频" to "100",
        "应用" to "300",
        "游戏" to "400",
        "其他" to "600"
    )
    
    private val sortOptions = listOf(
        "默认排序",
        "时间 ↑",
        "时间 ↓",
        "大小 ↑",
        "大小 ↓",
        "种子数 ↑",
        "种子数 ↓"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        scraper = TPBScraper()
        
        initViews()
        setupCategorySpinner()
        setupSortSpinner()
        setupRecyclerView()
        setupListeners()
        
        showInitial()
    }

    private fun initViews() {
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        topButton = findViewById(R.id.topButton)
        categorySpinner = findViewById(R.id.categorySpinner)
        sortSpinner = findViewById(R.id.sortSpinner)
        torrentsRecyclerView = findViewById(R.id.torrentsRecyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        errorView = findViewById(R.id.errorView)
    }

    private fun setupCategorySpinner() {
        val categoryNames = categories.keys.toList()
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, categoryNames)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        categorySpinner.adapter = spinnerAdapter
        
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categoryNames[position]
                currentCategory = categories[selectedCategory] ?: "0"
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupSortSpinner() {
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, sortOptions)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        sortSpinner.adapter = spinnerAdapter
        
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentSort = position
                applySort()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupRecyclerView() {
        adapter = TorrentAdapter(this, mutableListOf())
        torrentsRecyclerView.layoutManager = LinearLayoutManager(this)
        torrentsRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        searchButton.setOnClickListener {
            performSearch()
        }
        
        topButton.setOnClickListener {
            loadTop100()
        }
        
        searchEditText.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        
        swipeRefreshLayout.setOnRefreshListener {
            if (currentQuery.isNotEmpty()) {
                performSearch()
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
        
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_orange_light,
            android.R.color.holo_orange_dark
        )
    }

    private fun performSearch() {
        val query = searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            searchEditText.error = "请输入搜索关键词"
            return
        }
        
        currentQuery = query
        currentTorrents = emptyList()
        adapter.clear()
        
        search(query, currentCategory)
    }

    private fun loadTop100() {
        currentQuery = ""
        currentTorrents = emptyList()
        adapter.clear()
        
        showLoading()
        lifecycleScope.launch {
            val result = scraper.getTopTorrents(currentCategory)
            handleResult(result)
        }
    }

    private fun search(query: String, category: String) {
        showLoading()
        lifecycleScope.launch {
            val result = scraper.search(query, category)
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<List<TorrentItem>>) {
        hideLoading()
        
        result.fold(
            onSuccess = { torrents ->
                if (torrents.isEmpty()) {
                    showEmpty()
                } else {
                    currentTorrents = torrents
                    applySort()
                    showContent()
                }
            },
            onFailure = { error ->
                showError(error.message ?: "未知错误")
            }
        )
    }

    private fun applySort() {
        if (currentTorrents.isEmpty()) return
        adapter.updateAndSort(currentTorrents, currentSort)
    }

    private fun showInitial() {
        progressBar.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = "输入关键词搜索或点击 Top 100"
        errorView.visibility = View.GONE
        torrentsRecyclerView.visibility = View.GONE
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        errorView.visibility = View.GONE
        torrentsRecyclerView.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    private fun showEmpty() {
        emptyView.visibility = View.VISIBLE
        emptyView.text = "没有找到结果"
        errorView.visibility = View.GONE
        torrentsRecyclerView.visibility = View.GONE
    }

    private fun showContent() {
        torrentsRecyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        errorView.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        torrentsRecyclerView.visibility = View.GONE
    }
}
