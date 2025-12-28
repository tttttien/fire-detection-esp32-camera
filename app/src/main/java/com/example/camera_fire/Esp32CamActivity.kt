package com.example.camera_fire

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.camera_fire.databinding.ActivityEsp32CamBinding
import com.example.camera_fire.network.RetrofitClient
import com.example.camera_fire.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEsp32CamBinding

    private var fireDetectionMode = false
    private val serverUrl = "https://forceless-josette-unluckier.ngrok-free.dev"
    private val handler = Handler(Looper.getMainLooper())
    private val fireCheckInterval: Long = 3000L // 3 seconds

    private var isRecording = false
    private var frameCounter = 0

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

    private val fireCheckRunnable = object : Runnable {
        override fun run() {
            if (fireDetectionMode) {
                checkFireStatus()
                handler.postDelayed(this, fireCheckInterval)
            }
        }
    }
    private var cameraId: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraId = intent.getIntExtra("camera_id", -1)
        if (cameraId == -1) {
            Toast.makeText(this, "Invalid camera", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = ActivityEsp32CamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)
        checkAndRequestPermissions()
        setupWebView()

        binding.btnReload.setOnClickListener { binding.webView.reload() }
        binding.btnRecord.setOnClickListener { toggleVideoRecording() }
        binding.btnHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnHistory.setOnClickListener {
            val moveHistory = Intent(this, HistoryActivity::class.java)
            moveHistory.putExtra("camera_id", cameraId)
            startActivity(moveHistory)
        }

        binding.switchFireDetection.setOnCheckedChangeListener { _, isChecked ->
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
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.loadWithOverviewMode = true
        binding.webView.settings.useWideViewPort = true
        binding.webView.settings.builtInZoomControls = true // Enable zoom controls
        binding.webView.settings.displayZoomControls = false // Hide zoom controls
        binding.webView.webViewClient = WebViewClient()
        binding.webView.loadUrl("$serverUrl/video_feed/$cameraId")
    }

    private fun toggleFireDetection(enable: Boolean) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.toggleDetection(enable)
                if (!response.isSuccessful) {
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

    private fun captureWebViewSnapshot(callback: (Bitmap?) -> Unit) {
        binding.webView.post {
            try {
                val bitmap = Bitmap.createBitmap(
                    binding.webView.width.coerceAtLeast(1),
                    binding.webView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                binding.webView.draw(canvas)
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
        }
    }

    private fun toggleVideoRecording() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Some permissions are missing. Requesting permissions...", Toast.LENGTH_LONG).show()
            checkAndRequestPermissions()
            return
        }

        if (isRecording) {
            stopVideoRecording()
        } else {
            startVideoRecording()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "Missing permission: $permission")
                return false
            }
        }
        return true
    }

    private fun startVideoRecording() {
        try {
            frameCounter = 0
            startWebViewRecording()
            isRecording = true
            binding.btnRecord.text = "Stop Recording"
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error starting recording: ${e.message}")
            resetRecordingState()
        }
    }

    private fun startWebViewRecording() {
        val recordingHandler = Handler(Looper.getMainLooper())
        val recordingRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    captureWebViewFrame()
                    recordingHandler.postDelayed(this, 100) // ~10 FPS
                }
            }
        }
        recordingHandler.post(recordingRunnable)
    }

    private fun captureWebViewFrame() {
        binding.webView.post {
            try {
                val bitmap = Bitmap.createBitmap(
                    binding.webView.width.coerceAtLeast(1),
                    binding.webView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                binding.webView.draw(canvas)
                saveFrameToFile(bitmap)
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e("VideoRecording", "Error capturing frame: ${e.message}")
            }
        }
    }

    private fun saveFrameToFile(bitmap: Bitmap) {
        try {
            val frameFile = File(cacheDir, "frame_${frameCounter}.jpg")
            FileOutputStream(frameFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }
            frameCounter++
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error saving frame: ${e.message}")
        }
    }

    private fun stopVideoRecording() {
        try {
            isRecording = false
            createVideoFromFrames()
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error stopping recording: ${e.message}")
        } finally {
            resetRecordingState()
        }
    }

    private fun createVideoFromFrames() {
        lifecycleScope.launch {
            val frameFiles = withContext(Dispatchers.IO) {
                cacheDir.listFiles { file ->
                    file.name.startsWith("frame_") && file.name.endsWith(".jpg")
                }?.sortedBy { it.name } ?: emptyList()
            }

            if (frameFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Esp32CamActivity, "No frames captured", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoFileName = "ESP32_VIDEO_$timeStamp.mp4"

            // Prepare output: Q+ uses MediaStore + OutputStream, <Q uses File
            val output: Pair<File?, Uri?> = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/ESP32_Camera"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                    )
                    Pair(null, uri)
                } else {
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val esp32Dir = File(moviesDir, "ESP32_Camera")
                    if (!esp32Dir.exists()) esp32Dir.mkdirs()
                    Pair(File(esp32Dir, videoFileName), null)
                }
            }

            val outputFile = output.first
            val outputUri = output.second

            // TODO: [START] Implement actual video encoding here. This is a placeholder.
            // You need to encode the captured frames into an MP4 file using MediaCodec and MediaMuxer,
            // or integrate a third-party library for video creation from image sequences.
            // For now, we'll set success to true to allow the saving logic to proceed,
            // but the resulting video file will be empty or unplayable without proper encoding.
            val success = withContext(Dispatchers.IO) {
                // Placeholder for actual video encoding logic
                Log.d("VideoRecording", "Attempting to create a placeholder video file.")
                true // Temporarily setting to true to allow MediaStore/MediaScanner to try saving
            }
            // TODO: [END] Implement actual video encoding here.

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Complete MediaStore entry
                    outputUri?.let { uri ->
                        // Remove IS_PENDING flag for MediaStore to display
                        val cv = ContentValues().apply {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                        }
                        contentResolver.update(uri, cv, null, null)
                        if (success) {
                            Toast.makeText(this@Esp32CamActivity, "Video saved to MediaStore", Toast.LENGTH_LONG).show()
                        } else {
                            // If encoding fails, delete the empty record
                            contentResolver.delete(uri, null, null)
                            Toast.makeText(this@Esp32CamActivity, "Failed to create video.", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Toast.makeText(this@Esp32CamActivity, "Failed to create video file.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (success && outputFile != null) {
                        Toast.makeText(this@Esp32CamActivity, "Video saved to ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                        // ✅ Instead of ACTION_MEDIA_SCANNER_SCAN_FILE (deprecated), use MediaScannerConnection:
                        MediaScannerConnection.scanFile(
                            this@Esp32CamActivity,
                            arrayOf(outputFile.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    } else {
                        Toast.makeText(this@Esp32CamActivity, "Failed to create video.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun resetRecordingState() {
        isRecording = false
        frameCounter = 0
        // Clean up captured frames
        lifecycleScope.launch(Dispatchers.IO) {
            cacheDir.listFiles { file ->
                file.name.startsWith("frame_") && file.name.endsWith(".jpg")
            }?.forEach { it.delete() }
        }
        // Update UI on the main thread
        runOnUiThread {
            binding.btnRecord.text = "Record"
        }
    }
}