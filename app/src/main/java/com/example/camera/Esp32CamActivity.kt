package com.example.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.camera.MainActivity
import com.example.camera.R
import com.example.camera.network.RetrofitClient
import com.example.camera.model.LatestAlertResponse
import com.example.camera.utils.NotificationUtils
import kotlinx.coroutines.launch

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fireMaskImageView: ImageView
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnHome: Button
    private lateinit var switchFireDetection: Switch

    private val handler = Handler()
    private var fireDetectionMode = false
    private val fireCheckInterval: Long = 3000 // 3s

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

        webView = findViewById(R.id.webView)
        fireMaskImageView = findViewById(R.id.fireMaskImageView)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        switchFireDetection = findViewById(R.id.switchFireDetection)

        setupWebView()

        btnBack.setOnClickListener {
            onBackPressed()
        }

        btnReload.setOnClickListener {
            webView.reload()
        }

        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        switchFireDetection.setOnCheckedChangeListener { _, isChecked ->
            fireDetectionMode = isChecked
            if (isChecked) {
                handler.post(fireCheckRunnable)
                Toast.makeText(this, "Fire detection ON", Toast.LENGTH_SHORT).show()
            } else {
                handler.removeCallbacks(fireCheckRunnable)
                fireMaskImageView.visibility = View.GONE
                Toast.makeText(this, "Fire detection OFF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("http://192.168.183.126/stream") // Đảm bảo đây là IP chính xác của ESP32
    }

    private fun checkFireStatus() {
        // Use lifecycleScope.launch to launch the coroutine
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getLatestAlert()
                if (response.isSuccessful) {
                    val alert = response.body()
                    if (alert?.fire_detected == true && alert.image_url != null) {
                        // Fire detected, load image and notify user
                        loadSnapshotFromUrl(alert.image_url)
                    } else {
                        // No fire, hide the image
                        fireMaskImageView.visibility = View.GONE
                    }
                } else {
                    Log.e("FireCheck", "Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("FireCheck", "Failed to fetch: ${e.message}")
            }
        }
    }

    private fun loadSnapshotFromUrl(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    fireMaskImageView.setImageBitmap(resource)
                    fireMaskImageView.visibility = View.VISIBLE
                    // Gửi thông báo có cháy
                    NotificationUtils.showFireNotification(this@Esp32CamActivity, url)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    override fun onDestroy() {
        handler.removeCallbacks(fireCheckRunnable)
        super.onDestroy()
    }
}
