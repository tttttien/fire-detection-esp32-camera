package com.example.camera_fire

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddCameraActivity : AppCompatActivity() {

    private lateinit var etCameraIndex: EditText
    private lateinit var etLabel: EditText
    private lateinit var btnSaveCamera: Button

    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_camera)

        etCameraIndex = findViewById(R.id.etCameraIndex)
        etLabel = findViewById(R.id.etLabel)
        btnSaveCamera = findViewById(R.id.btnSaveCamera)

        btnSaveCamera.setOnClickListener {
            val cameraIndex = etCameraIndex.text.toString()
            val label = etLabel.text.toString()

            if (cameraIndex.isNotBlank() && label.isNotBlank()) {
                saveCameraToSupabase(cameraIndex, label)
            } else {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCameraToSupabase(cameraIndex: String, label: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddCameraActivity, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Dữ liệu camera cần insert vào bảng 'cameras'
                val cameraData = mapOf(
                    "owner_id" to userId, // Đã đổi từ "user_id" sang "owner_id"
                    "camera_index" to cameraIndex,
                    "label" to label
                )

                supabase.postgrest["cameras"].insert(cameraData) // Tên bảng là "cameras" như trong lỗi

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddCameraActivity, "Thêm camera thành công!", Toast.LENGTH_SHORT).show()
                    finish() // Đóng Activity sau khi lưu thành công
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddCameraActivity, "Lỗi khi thêm camera: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }
}