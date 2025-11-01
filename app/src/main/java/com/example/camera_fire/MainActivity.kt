package com.example.camera_fire

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.camera_fire.databinding.ActivityMainBinding
import io.github.jan.supabase.auth.auth     // ✅ v3: thay cho gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Lấy user hiện tại từ Supabase Auth (v3)
        val user = supabase.auth.currentUserOrNull()

        if (user != null) {
            val userName = user.userMetadata?.get("name")?.jsonPrimitive?.content ?: "User"
            binding.headline.text = "Welcome, $userName"
        } else {
            // Nếu chưa đăng nhập → điều hướng về SignInActivity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        // Nút mở trang hồ sơ
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Nút mở camera ESP32
        binding.btnEsp32Cam.setOnClickListener {
            startActivity(Intent(this, Esp32CamActivity::class.java))
        }

        // Nút đăng xuất (nếu có thêm trong layout)
//        binding.btnLogout?.setOnClickListener {
//            signOutAndStartSignInActivity()
//        }
    }

    private fun signOutAndStartSignInActivity() {
        CoroutineScope(Dispatchers.IO).launch {
            supabase.auth.signOut() // ✅ API v3
            withContext(Dispatchers.Main) {
                val intent = Intent(this@MainActivity, SignInActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}
