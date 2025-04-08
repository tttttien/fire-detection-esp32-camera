package com.example.camera

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
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
    private val frameCaptureInterval: Long = 2000 // Capture frame every 2 seconds
    private var firebaseDatabase: DatabaseReference? = null // Firebase database reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp32_cam)

        webView = findViewById(R.id.webView)
        fireMaskImageView = findViewById(R.id.fireMaskImageView)

        // Initialize Firebase Realtime Database
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("fire_masks");

        setupWebView()

        webView.loadUrl("http://192.168.51.126/stream") // Update the ESP32 stream URL

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
            Log.d("FrameCapture", "Attempting to capture frame from WebView")
            captureFrameFromWebView()
            handler.postDelayed(this, frameCaptureInterval)
        }
    }

    private fun captureFrameFromWebView() {
        if (webView.width > 0 && webView.height > 0) {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            webView.draw(canvas)
            Log.d("FrameCapture", "Captured frame successfully")
            sendImageToServer(bitmap)
        } else {
            Log.e("FrameCapture", "WebView has invalid dimensions: Width=${webView.width}, Height=${webView.height}")
        }
    }

    private fun sendImageToServer(bitmap: Bitmap) {
        Log.d("FireDetection", "Sending image to server")
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)

        RetrofitClient.apiService.detectFire(filePart).enqueue(object : Callback<FireResponse> {
            override fun onResponse(call: Call<FireResponse>, response: Response<FireResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    Log.d("FireDetection", "Server Response: ${response.body()}")

                    val fireDetected = result?.fireDetected ?: false
                    Log.d("FireDetection", "Fire Detected: $fireDetected")

                    if (result != null && result.fireMask.isNotEmpty()) {
                        Log.d("FireDetection", "Processing fire mask...")
                        val reconstructedMask = reconstructMask(result)
                        val fireMaskBitmap = convertMaskToBitmap(reconstructedMask, webView.width, webView.height)
                        displayFireMask(fireMaskBitmap)
                        if (fireDetected) {
                            saveFireMaskToFirebase(result.fireMask);
                        }
                    } else {
                        Log.d("FireDetection", "No fire detected. Hiding mask.")
                        displayFireMask(null) // Ẩn mask nếu không có lửa
                    }
                } else {
                    Log.e("FireDetection", "Failed response: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<FireResponse>, t: Throwable) {
                Log.e("FireDetection", "Error sending image: ${t.message}")
                runOnUiThread {
                    Toast.makeText(this@Esp32CamActivity, "Error: ${t.message}", Toast.LENGTH_LONG).show()
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
                val pixelValue = if (fireMask[maskY][maskX] == 1) android.graphics.Color.RED else android.graphics.Color.TRANSPARENT
                scaledBitmap.setPixel(x, y, pixelValue)
            }
        }
        return scaledBitmap
    }

    private fun displayFireMask(bitmap: Bitmap?) {
        runOnUiThread {
            if (bitmap != null) {
                Log.d("FireMask", "Updating fire mask. Size: ${webView.width}x${webView.height}")
                fireMaskImageView.layoutParams.width = webView.width
                fireMaskImageView.layoutParams.height = webView.height
                fireMaskImageView.setImageBitmap(bitmap)
                fireMaskImageView.visibility = View.VISIBLE
            } else {
                Log.d("FireMask", "No fire detected. Hiding mask.")
                fireMaskImageView.setImageBitmap(null)
                fireMaskImageView.visibility = View.GONE
            }
        }
    }

    private fun saveFireMaskToFirebase(fireMask: List<Int>) {
        // Create a map to store the fire mask data along with a timestamp
        val fireMaskData: MutableMap<String, Any> = HashMap()
        fireMaskData["fireMask"] = fireMask
        fireMaskData["timestamp"] = System.currentTimeMillis()

        // Generate a unique ID for the fire mask entry
        val maskId = firebaseDatabase!!.push().key
        if (maskId != null) {
            // Save the fire mask data to Firebase under the "fire_masks" node
            firebaseDatabase!!.child(maskId).setValue(fireMaskData)
                .addOnSuccessListener { aVoid: Void? ->
                    // Show a success message
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Fire mask saved to Firebase",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e: Exception ->
                    // Show an error message if saving fails
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Failed to save fire mask: " + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(frameCaptureRunnable)
        Log.d("Esp32CamActivity", "Activity destroyed, stopped frame capturing")
    }
}
