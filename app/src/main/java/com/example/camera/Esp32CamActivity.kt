package com.example.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnHome: Button
    private lateinit var switchFireDetection: Switch

    private var fireDetectionMode = false
    private val serverUrl = "https://0bd0-113-161-91-223.ngrok-free.app"
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

        NotificationHelper.createNotificationChannel(this)

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

        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        switchFireDetection = findViewById(R.id.switchFireDetection)

        setupWebView()

        btnBack.setOnClickListener { onBackPressed() }
        btnReload.setOnClickListener { webView.reload() }
        btnHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

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
        captureWebViewSnapshot { bitmap ->
            if (bitmap == null) {
                Log.e("FireCheck", "Failed to capture snapshot")
                return@captureWebViewSnapshot
            }

            lifecycleScope.launch {
                try {
                    val filePart = bitmapToMultipart(bitmap, "frame.jpg")
                    val response = RetrofitClient.apiService.uploadFrame(filePart)
                    if (response.isSuccessful) {
                        val alert = response.body()
                        if (alert?.fire_detected == true && alert.image_url != null) {
                            val fullUrl = if (alert.image_url.startsWith("http")) {
                                alert.image_url
                            } else {
                                "$serverUrl/${alert.image_url}"
                            }
                            Log.d("FireCheck", "Fire detected! Image URL: $fullUrl")
                            fetchSnapshotAndNotify(fullUrl)
                        } else {
                            Log.d("FireCheck", "No fire detected")
                        }
                    } else {
                        Log.e("FireCheck", "Server error: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("FireCheck", "Error checking fire status: ${e.message}")
                }
            }
        }
    }

    // Capture snapshot từ WebView (chụp ảnh màn hình webview)
    private fun captureWebViewSnapshot(callback: (Bitmap?) -> Unit) {
        webView.post {
            try {
                val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                webView.draw(canvas)
                callback(bitmap)
            } catch (e: Exception) {
                Log.e("FireCheck", "Error capturing snapshot: ${e.message}")
                callback(null)
            }
        }
    }

    private fun bitmapToMultipart(bitmap: Bitmap, name: String): MultipartBody.Part {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        val bitmapData = bos.toByteArray()
        val requestFile = bitmapData.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", name, requestFile)
    }

    private fun fetchSnapshotAndNotify(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    NotificationHelper.showFireDetectedNotification(this@Esp32CamActivity, resource)
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    override fun onDestroy() {
        handler.removeCallbacks(fireCheckRunnable)
        super.onDestroy()
    }
}
