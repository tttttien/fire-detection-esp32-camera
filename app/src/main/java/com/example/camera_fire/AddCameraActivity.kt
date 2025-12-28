package com.example.camera_fire

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private lateinit var btnBack: ImageView

    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_camera)

        etCameraIndex = findViewById(R.id.etCameraIndex)
        etLabel = findViewById(R.id.etLabel)
        btnSaveCamera = findViewById(R.id.btnSaveCamera)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            finish() // Quay về activity trước
        }

        btnSaveCamera.setOnClickListener {
            val cameraIndex = etCameraIndex.text.toString()
            val label = etLabel.text.toString()

            if (cameraIndex.isNotBlank() && label.isNotBlank()) {
                saveCameraToSupabase(cameraIndex, label)
            } else {
                Toast.makeText(this, "Please enter all information", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCameraToSupabase(cameraIndex: String, label: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddCameraActivity, "You are not logged in", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val cameraData = mapOf(
                    "owner_id" to userId,
                    "camera_index" to cameraIndex,
                    "label" to label
                )

                supabase.postgrest["cameras"].insert(cameraData)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddCameraActivity, "Camera added successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddCameraActivity, "Error adding camera: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }
}
