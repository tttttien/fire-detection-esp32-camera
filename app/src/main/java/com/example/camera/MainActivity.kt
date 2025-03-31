package com.example.camera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        setupButton(R.id.btnMainScreen) { /* Load màn hình chính */ }
//        setupButton(R.id.btnEsp32Cam, Esp32CamActivity::class.java)
        setupButton(R.id.btnPhoneCam, PhoneCameraActivity::class.java)
//        setupButton(R.id.btnFireSafety, FireSafetyActivity::class.java)
    }

    private fun setupButton(buttonId: Int, activityClass: Class<*>? = null) {
        findViewById<Button>(buttonId).setOnClickListener {
            activityClass?.let { startActivity(Intent(this, it)) }
        }
    }
}
