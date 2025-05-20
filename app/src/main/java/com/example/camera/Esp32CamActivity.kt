package com.example.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.camera.network.RetrofitClient
import com.example.camera.utils.NotificationHelper
import kotlinx.coroutines.launch

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnHome: Button
    private lateinit var switchFireDetection: Switch

    private var fireDetectionMode = false
    private val serverUrl = "http://192.168.72.40:8000"
    private val handler = Handler()
    private val fireCheckInterval: Long = 3000L // 3 giây

    private val fireCheckRunnable = object : Runnable {
        override fun run() {
            if (fireDetectionMode) {
                checkFireStatus()
                handler.postDelayed(this, fireCheckInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp32_cam)

        // Tạo Notification Channel
        NotificationHelper.createNotificationChannel(this)

        // Xin quyền POST_NOTIFICATIONS nếu Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        // Ánh xạ view
        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        switchFireDetection = findViewById(R.id.switchFireDetection)

        setupWebView()

        // Nút Back
        btnBack.setOnClickListener { onBackPressed() }

        // Nút Reload WebView
        btnReload.setOnClickListener { webView.reload() }

        // Nút Home
        btnHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Bật/Tắt Fire Detection
        switchFireDetection.setOnCheckedChangeListener { _, isChecked ->
            fireDetectionMode = isChecked
            toggleFireDetection(isChecked)
            if (isChecked) {
                handler.post(fireCheckRunnable)
                Toast.makeText(this, "Fire detection ON", Toast.LENGTH_SHORT).show()
            } else {
                handler.removeCallbacks(fireCheckRunnable)
                Toast.makeText(this, "Fire detection OFF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("$serverUrl/video_feed")
    }

    private fun toggleFireDetection(enable: Boolean) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.toggleDetection(enable)
                if (response.isSuccessful) {
                    Log.d("Esp32Cam", "Detection toggled: $enable")
                } else {
                    Log.e("Esp32Cam", "Toggle failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("Esp32Cam", "Error toggling detection: ${e.message}")
            }
        }
    }

    private fun checkFireStatus() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getLatestAlert()
                if (response.isSuccessful) {
                    val alert = response.body()
                    if (alert?.fire_detected == true && alert.image_url != null) {
                        fetchSnapshotAndNotify(alert.image_url)
                    }
                } else {
                    Log.e("FireCheck", "Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("FireCheck", "Failed to fetch fire status: ${e.message}")
            }
        }
    }

    private fun fetchSnapshotAndNotify(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    NotificationHelper.showFireDetectedNotification(
                        this@Esp32CamActivity,
                        resource
                    )
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // No-op
                }
            })
    }

    override fun onDestroy() {
        handler.removeCallbacks(fireCheckRunnable)
        super.onDestroy()
    }
}
