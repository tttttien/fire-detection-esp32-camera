package com.example.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream

class Esp32CamActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var fireMaskImageView: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val frameCaptureInterval: Long = 5000

    private val NOTIF_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp32_cam)

        webView = findViewById(R.id.webView)
        fireMaskImageView = findViewById(R.id.fireMaskImageView)

        setupWebView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_CODE
                )
            }
        }

        NotificationHelper.createNotificationChannel(this)

        webView.loadUrl("http://192.168.51.126/stream")
        handler.postDelayed(frameCaptureRunnable, frameCaptureInterval)
    }

    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebView", "Page loaded: $url, Width=${view?.width}, Height=${view?.height}")
            }
        }

        webView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }
    }

    private val frameCaptureRunnable = object : Runnable {
        override fun run() {
            captureFrameFromWebView()
            handler.postDelayed(this, frameCaptureInterval)
        }
    }

    private fun captureFrameFromWebView() {
        if (webView.width > 0 && webView.height > 0) {
            val bitmapOriginal = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmapOriginal)
            webView.draw(canvas)

            sendImageToServer(bitmapOriginal)
        } else {
            Log.e("FrameCapture", "Invalid WebView size")
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun sendImageToServer(bitmapOriginal: Bitmap) {
        val start = System.currentTimeMillis()

        val resizedBitmap = resizeBitmap(bitmapOriginal, 240, 240)
        val stream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)

        RetrofitClient.apiService.detectFire(filePart).enqueue(object : Callback<FireResponse> {
            override fun onResponse(call: Call<FireResponse>, response: Response<FireResponse>) {
                Log.d("FireDetection", "Request took ${System.currentTimeMillis() - start} ms")
                if (response.isSuccessful) {
                    val result = response.body()
                    val fireDetected = result?.fireDetected ?: false

                    if (fireDetected) {
                        runOnUiThread {
                            Toast.makeText(this@Esp32CamActivity, "🔥 Fire detected!", Toast.LENGTH_LONG).show()
                        }
                        NotificationHelper.showFireDetectedNotification(this@Esp32CamActivity, bitmapOriginal)
                    }

                    if (result != null && result.fireMask.isNotEmpty()) {
                        val reconstructedMask = reconstructMask(result)
                        val fireMaskBitmap = convertMaskToBitmap(reconstructedMask, webView.width, webView.height)
                        displayFireMask(fireMaskBitmap)
                    } else {
                        displayFireMask(null)
                    }
                } else {
                    Log.e("FireDetection", "Server error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<FireResponse>, t: Throwable) {
                Log.e("FireDetection", "Failed: ${t.localizedMessage}")
                runOnUiThread {
                    Toast.makeText(this@Esp32CamActivity, "Error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun reconstructMask(fireResponse: FireResponse): List<List<Int>> {
        return List(fireResponse.height) { i ->
            fireResponse.fireMask.subList(i * fireResponse.width, (i + 1) * fireResponse.width)
        }
    }

    private fun convertMaskToBitmap(fireMask: List<List<Int>>, webViewWidth: Int, webViewHeight: Int): Bitmap {
        val maskHeight = fireMask.size
        val maskWidth = fireMask[0].size
        val scaledBitmap = Bitmap.createBitmap(webViewWidth, webViewHeight, Bitmap.Config.ARGB_8888)

        for (y in 0 until webViewHeight) {
            for (x in 0 until webViewWidth) {
                val maskX = (x * maskWidth) / webViewWidth
                val maskY = (y * maskHeight) / webViewHeight
                val pixelValue = if (fireMask[maskY][maskX] == 1) Color.RED else Color.TRANSPARENT
                scaledBitmap.setPixel(x, y, pixelValue)
            }
        }
        return scaledBitmap
    }

    private fun displayFireMask(bitmap: Bitmap?) {
        runOnUiThread {
            if (bitmap != null) {
                fireMaskImageView.layoutParams.width = webView.width
                fireMaskImageView.layoutParams.height = webView.height
                fireMaskImageView.setImageBitmap(bitmap)
                fireMaskImageView.visibility = View.VISIBLE
            } else {
                fireMaskImageView.setImageBitmap(null)
                fireMaskImageView.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(frameCaptureRunnable)
    }
}