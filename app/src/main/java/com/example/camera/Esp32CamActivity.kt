package com.example.camera

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.camera.network.RetrofitClient
import kotlinx.coroutines.launch

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnHome: Button
    private lateinit var switchFireDetection: Switch
    private lateinit var fireMaskImageView: ImageView

    private var fireDetectionMode = false
    private val serverUrl = "http://192.168.72.40:8000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp32_cam)

        // Ánh xạ UI
        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        switchFireDetection = findViewById(R.id.switchFireDetection)
        fireMaskImageView = findViewById(R.id.fireMaskImageView)

        setupWebView()

        // Nút quay lại
        btnBack.setOnClickListener {
            onBackPressed()
        }

        // Nút reload
        btnReload.setOnClickListener {
            webView.reload()
        }

        // Nút về trang chủ
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Switch bật/tắt phát hiện cháy
        switchFireDetection.setOnCheckedChangeListener { _, isChecked ->
            fireDetectionMode = isChecked
            toggleFireDetection(fireDetectionMode)
            Toast.makeText(
                this,
                if (isChecked) "Fire detection ON" else "Fire detection OFF",
                Toast.LENGTH_SHORT
            ).show()
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
}
