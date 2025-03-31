package com.example.camera

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.ComponentActivity

class Esp32CamActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp32_cam)

        val webView = findViewById<WebView>(R.id.webView)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnReload = findViewById<Button>(R.id.btnReload)
        val btnHome = findViewById<Button>(R.id.btnHome)

        val esp32CamUrl = "http://192.168.141.126/stream" // Thay IP của ESP32-CAM

        webView.webViewClient = WebViewClient()
        webView.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // Cho phép nội dung HTTP trong WebView
        }


        webView.loadUrl(esp32CamUrl)

        // Xử lý nút BACK
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()  // Quay về MainActivity
            }
        }


        // Xử lý nút RELOAD
        btnReload.setOnClickListener {
            webView.reload()
        }

        // Xử lý nút HOME (Quay về trang stream của ESP32)
        btnHome.setOnClickListener {
            webView.loadUrl(esp32CamUrl)
        }
    }
}
