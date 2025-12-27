package com.example.camera_fire

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.camera_fire.databinding.ActivityMainBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable

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
            loadCameras()
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

        // Nút thêm camera mới
        binding.btnAddCamera.setOnClickListener {
            startActivity(Intent(this, AddCameraActivity::class.java))
        }

        // Nút đăng xuất (nếu có thêm trong layout)
//        binding.btnLogout?.setOnClickListener {
//            signOutAndStartSignInActivity()
//        }
    }

    override fun onResume() {
        super.onResume()
        // Tải lại danh sách camera khi quay lại MainActivity
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            loadCameras()
        }
    }

    private fun loadCameras() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        // Toast.makeText(this@MainActivity, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val response = supabase.postgrest["cameras"]
                    .select { filter { eq("owner_id", userId) } } // Lấy các camera có user_id khớp

                val cameras = response.decodeList<Camera>() // Decode sang danh sách Camera

                withContext(Dispatchers.Main) {
                    binding.camerasContainer.removeAllViews() // Xóa các view cũ trước khi thêm mới

                    if (cameras.isEmpty()) {
                        // Hiển thị thông báo nếu không có camera nào
                        val noCameraText = TextView(this@MainActivity).apply {
                            text = "Chưa có camera nào được thêm. Hãy thêm một camera mới!"
                            textSize = 16f
                            setTextColor(resources.getColor(R.color.red, theme))
                            setPadding(32, 32, 32, 32)
                        }
                        binding.camerasContainer.addView(noCameraText)
                    } else {
                        cameras.forEach { camera ->
                            val cameraView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_camera, binding.camerasContainer, false) as LinearLayout
                            cameraView.findViewById<TextView>(R.id.cameraLabel).text = camera.label

                            cameraView.setOnClickListener {
                                val intent = Intent(this@MainActivity, Esp32CamActivity::class.java)
                                intent.putExtra("camera_index", camera.camera_index)
                                intent.putExtra("camera_label", camera.label)
                                startActivity(intent)
                            }
                            binding.camerasContainer.addView(cameraView)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lỗi khi tải camera: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
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

// Data class để dễ dàng decode dữ liệu từ Supabase
// Đảm bảo tên thuộc tính khớp với tên cột trong bảng 'cameras' của bạn
@Serializable
data class Camera(
    val owner_id: String,
    val camera_index: Int,
    val label: String,
    val created_at: String? = null
)