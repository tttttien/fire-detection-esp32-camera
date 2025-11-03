package com.example.camera_fire

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.camera_fire.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUrl = intent.getStringExtra("VIDEO_URL")
        Log.d("VideoPlayerActivity", "Received video URL: $videoUrl")

        if (videoUrl != null) {
            playVideo(videoUrl)
        } else {
            Toast.makeText(this, "Video URL not found", Toast.LENGTH_SHORT).show()
            Log.e("VideoPlayerActivity", "Video URL was null in Intent extras.")
            finish()
        }
    }

    private fun playVideo(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        val uri = Uri.parse(url)
        binding.videoView.setVideoURI(uri)

        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        binding.videoView.setOnPreparedListener { mp ->
            binding.progressBar.visibility = View.GONE
            mp.start()
            Log.d("VideoPlayerActivity", "Video prepared and started.")
        }

        binding.videoView.setOnErrorListener { mp, what, extra ->
            binding.progressBar.visibility = View.GONE
            val errorMsg = "Error playing video: what=$what, extra=$extra"
            Log.e("VideoPlayerActivity", errorMsg)
            Toast.makeText(this, "Error playing video: $what", Toast.LENGTH_LONG).show()
            true // Indicate that the error has been handled
        }
    }
}