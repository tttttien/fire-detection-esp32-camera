package com.example.camera_fire

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.camera_fire.adapter.HistoryAdapter
import com.example.camera_fire.databinding.ActivityHistoryBinding
import com.example.camera_fire.models.FireEvent
import com.example.camera_fire.utils.EndlessRecyclerViewScrollListener
import com.google.android.material.tabs.TabLayout
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter
    private var nextCursorFrames: String? = null
    private var nextCursorVideos: String? = null
    private var currentTab = 0 // 0 = frames, 1 = videos
    private lateinit var scrollListener: EndlessRecyclerViewScrollListener
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnBack.setOnClickListener { finish() }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                clearAndFetchHistory()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.swipeRefreshLayout.setOnRefreshListener { clearAndFetchHistory() }

        clearAndFetchHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = historyAdapter
        }
        scrollListener = object : EndlessRecyclerViewScrollListener(layoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                fetchHistory(false)
            }
        }
        binding.recyclerView.addOnScrollListener(scrollListener)
    }

    private fun clearAndFetchHistory() {
        historyAdapter.submitList(emptyList())
        nextCursorFrames = null
        nextCursorVideos = null
        scrollListener.resetState()
        fetchHistory(true)
    }

    private fun fetchHistory(isInitialLoad: Boolean) {
        if (isLoading) return
        isLoading = true
        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val eventType = if (currentTab == 0) "frame" else "video"
            val cursor = if (currentTab == 0) nextCursorFrames else nextCursorVideos
            val pageSize = 20

            Log.d("HistoryActivity", "🔍 Fetch | type=$eventType | cursor=$cursor | pageSize=$pageSize")

            try {
                val (cCreatedAt, cId) = cursor?.split("|")?.let {
                    if (it.size == 2) it[0] to it[1].toLongOrNull() else null to null
                } ?: (null to null)
                val cameraId = intent.getIntExtra("camera_id", -1)
                // In ra để kiểm tra trong Logcat
//                Log.d("HistoryActivity", "📸 Kiểm tra Camera ID nhận được: $cameraId")
                // RPC params as JsonObject (KHÔNG dùng Map<*, *>)
                val params = buildJsonObject {
                    put("p_type", JsonPrimitive(eventType))
                    cCreatedAt?.let { put("p_cursor_created_at", JsonPrimitive(it)) }
                    cId?.let        { put("p_cursor_id",          JsonPrimitive(it)) }
                    put("p_limit", JsonPrimitive(pageSize))

                    // ✅ PHẢI CÓ DÒNG NÀY ĐỂ TRUYỀN ID XUỐNG HÀM TRÊN
                    if (cameraId != -1) {
                        put("p_camera_id", JsonPrimitive(cameraId))
                    }
                }

                val rows: List<FireEvent> = Supabase.client
                    .postgrest
                    .rpc(function = "fetch_events", parameters = params)
                    .decodeList()

                Log.d("HistoryActivity", "✅ RPC rows = ${rows.size}")

                val baseUrl = "https://bwmqzqgnouisgshuprhh.storage.supabase.co/storage/v1/object/public/fire"
                val newEvents = rows
                    .sortedWith(compareByDescending<FireEvent> { it.createdAt }.thenByDescending { it.id })
                    .onEach { it.publicUrl = "$baseUrl/${it.objectName}" }

                withContext(Dispatchers.Main) {
                    Log.d("HistoryActivity", "📦 Deliver ${newEvents.size} items to UI")

                    if (newEvents.isNotEmpty()) {
                        val last = newEvents.last()
                        val next = "${last.createdAt}|${last.id}"
                        if (currentTab == 0) nextCursorFrames = next else nextCursorVideos = next
                        Log.d("HistoryActivity", "➡️ NextCursor = $next")
                    } else {
                        Log.d("HistoryActivity", "⚠️ No new events")
                    }

                    val merged = if (isInitialLoad) newEvents else historyAdapter.currentList + newEvents
                    historyAdapter.submitList(merged)
                }

            } catch (e: Exception) {
                Log.e("HistoryActivity", "❌ Fetch error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryActivity, "Error fetching: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.swipeRefreshLayout.isRefreshing = isLoading
    }
}
