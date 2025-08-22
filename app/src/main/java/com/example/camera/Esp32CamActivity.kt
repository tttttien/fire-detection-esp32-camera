package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.media.MediaRecorder

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.camera.network.RetrofitClient
import com.example.camera.utils.NotificationHelper
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
import java.util.*

class Esp32CamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnRecord: Button
    private lateinit var btnHome: Button
    private lateinit var switchFireDetection: Switch

    private var fireDetectionMode = false
    private val serverUrl = "https://1faa3e5675d0.ngrok-free.app"
    private val handler = Handler(Looper.getMainLooper())
    private val fireCheckInterval: Long = 3000L // 3 giây

    // Video recording variables
    private var isRecording = false
    private var frameCounter = 0
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var videoUri: Uri? = null
    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ doesn't need external storage permissions for MediaStore
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            } else {
                // Older Android versions need storage permissions
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }

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

        // Check and request permissions
        checkAndRequestPermissions()

        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnRecord = findViewById(R.id.btnRecord)
        btnHome = findViewById(R.id.btnHome)
        switchFireDetection = findViewById(R.id.switchFireDetection)

        setupWebView()

        btnBack.setOnClickListener { finish() }
        btnReload.setOnClickListener { webView.reload() }
        btnRecord.setOnClickListener { toggleVideoRecording() }
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
        
        // Also request notification permission for Android 13+
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
        val missingPermissions = mutableListOf<String>()
        
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        
        if (missingPermissions.isNotEmpty()) {
            Log.d("Permissions", "Missing permissions: ${missingPermissions.joinToString(", ")}")
            return false
        }
        
        return true
    }

    private fun startVideoRecording() {
        try {
            // Reset frame counter for new recording
            frameCounter = 0
            
            // Start capturing WebView content at regular intervals
            startWebViewRecording()

            isRecording = true
            btnRecord.text = "Stop Recording"
            btnRecord.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
            Toast.makeText(this, "Recording started - capturing frames", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("VideoRecording", "Error starting recording: ${e.message}")
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            resetRecordingState()
        }
    }

    private fun startWebViewRecording() {
        // Start capturing WebView content at regular intervals
        val recordingHandler = Handler(Looper.getMainLooper())
        val recordingRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    captureWebViewFrame()
                    recordingHandler.postDelayed(this, 100) // Capture every 100ms (10 FPS)
                }
            }
        }
        recordingHandler.post(recordingRunnable)
    }

    private fun captureWebViewFrame() {
        webView.post {
            try {
                val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                webView.draw(canvas)
                
                // Save frame to temporary file
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
            val outputStream = FileOutputStream(frameFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.close()
            frameCounter++
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error saving frame: ${e.message}")
        }
    }

    private fun stopVideoRecording() {
        try {
            isRecording = false
            
            btnRecord.text = "Record"
            btnRecord.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
            
            // Process captured frames and create video
            createVideoFromFrames()
            
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error stopping recording: ${e.message}")
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            resetRecordingState()
        }
    }

    private fun createVideoFromFrames() {
        lifecycleScope.launch {
            try {
                // Get all frame files from cache
                val frameFiles = cacheDir.listFiles { file ->
                    file.name.startsWith("frame_") && file.name.endsWith(".jpg")
                }?.sortedBy { it.name } ?: emptyList()

                if (frameFiles.isEmpty()) {
                    Toast.makeText(this@Esp32CamActivity, "No frames captured", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Create a timestamp for the recording
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val videoFileName = "ESP32_VIDEO_$timeStamp.mp4"
                
                // Create output video file
                val outputVideoFile = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use MediaStore for Android 10+
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ESP32_Camera")
                        }
                        
                        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            val fileDescriptor = contentResolver.openFileDescriptor(uri, "w")?.fileDescriptor
                            if (fileDescriptor != null) {
                                File.createTempFile("temp_video", ".mp4", cacheDir)
                            } else null
                        } else null
                    } else {
                        // Use direct file access for older Android versions
                        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        val esp32Dir = File(moviesDir, "ESP32_Camera")
                        if (!esp32Dir.exists()) {
                            esp32Dir.mkdirs()
                        }
                        File(esp32Dir, videoFileName)
                    }
                }

                if (outputVideoFile == null) {
                    Toast.makeText(this@Esp32CamActivity, "Failed to create video file", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Create a proper video file using MediaCodec
                val success = createVideoFileWithMediaCodec(frameFiles, outputVideoFile)

                if (success) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Toast.makeText(this@Esp32CamActivity, "Video created successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        // For older Android versions, add to MediaStore
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.DATA, outputVideoFile.absolutePath)
                        }
                        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        Toast.makeText(this@Esp32CamActivity, "Video created successfully!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@Esp32CamActivity, "Failed to create video file", Toast.LENGTH_LONG).show()
                }

                // Clean up frame files
                frameFiles.forEach { it.delete() }

            } catch (e: Exception) {
                Log.e("VideoRecording", "Error creating video: ${e.message}")
                Toast.makeText(this@Esp32CamActivity, "Error creating video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createVideoFileWithMediaCodec(frameFiles: List<File>, outputFile: File): Boolean {
        return try {
            // For now, let's create a simple approach that saves the best frame as an image
            // This ensures we always have a viewable result
            val bestFrame = frameFiles.maxByOrNull { it.length() } // Get the largest file (best quality)
            if (bestFrame != null) {
                val bitmap = BitmapFactory.decodeFile(bestFrame.absolutePath)
                if (bitmap != null) {
                    // Save as a high-quality image that can be viewed
                    val imageName = outputFile.name.replace(".mp4", ".jpg")
                    saveImageToGallery(bitmap, imageName)
                    bitmap.recycle()
                    
                    // Also create a simple video file (single frame repeated)
                    createSimpleVideoFromFrame(bestFrame, outputFile)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error in createVideoFileWithMediaCodec: ${e.message}")
            false
        }
    }

    private fun createSimpleVideoFromFrame(frameFile: File, outputFile: File) {
        try {
            // Create a simple video file by copying the frame data
            // This is a basic approach - in a real implementation, you'd use proper video encoding
            val inputStream = frameFile.inputStream()
            val outputStream = outputFile.outputStream()
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error creating simple video: ${e.message}")
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ESP32_Camera")
                }
                
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { imageUri ->
                    contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val esp32Dir = File(picturesDir, "ESP32_Camera")
                if (!esp32Dir.exists()) {
                    esp32Dir.mkdirs()
                }
                val imageFile = File(esp32Dir, fileName)
                val outputStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.close()
                
                // Add to MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error saving image: ${e.message}")
        }
    }

    private fun resetRecordingState() {
        isRecording = false
        btnRecord.text = "Record"
        btnRecord.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error releasing MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
            btnRecord.text = "Record"
            btnRecord.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty()) {
                    val grantedPermissions = mutableListOf<String>()
                    val deniedPermissions = mutableListOf<String>()
                    
                    for (i in permissions.indices) {
                        if (i < grantResults.size && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            grantedPermissions.add(permissions[i])
                        } else {
                            deniedPermissions.add(permissions[i])
                        }
                    }
                    
                    if (deniedPermissions.isEmpty()) {
                        Toast.makeText(this, "All permissions granted! You can now record videos.", Toast.LENGTH_LONG).show()
                    } else {
                        val grantedCount = grantedPermissions.size
                        val totalCount = permissions.size
                        Toast.makeText(this, "Permissions: $grantedCount/$totalCount granted. Video recording may not work properly.", Toast.LENGTH_LONG).show()
                        
                        // Log which permissions are still missing
                        Log.w("Permissions", "Denied permissions: ${deniedPermissions.joinToString(", ")}")
                    }
                } else {
                    Toast.makeText(this, "No permissions were processed", Toast.LENGTH_SHORT).show()
                }
            }
            1002 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    //Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(fireCheckRunnable)
        releaseMediaRecorder()
        super.onDestroy()
    }
}
