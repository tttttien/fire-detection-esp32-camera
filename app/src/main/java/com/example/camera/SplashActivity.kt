package com.example.camera

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * SplashActivity serves as the initial screen displayed when the app starts.
 * It shows a splash screen for a few seconds before transitioning to MainActivity.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the layout for the splash screen
        setContentView(R.layout.activity_splash)

        // Delay execution for 4 seconds before launching the main activity
        Handler(Looper.getMainLooper()).postDelayed({
            // Start MainActivity after the delay
            startActivity(Intent(this, MainActivity::class.java))

            // Close SplashActivity to prevent users from navigating back to it
            finish()
        }, 4000) // Display time: 4 seconds
    }
}
