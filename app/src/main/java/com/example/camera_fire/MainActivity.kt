package com.example.camera_fire

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.camera_fire.databinding.ActivityMainBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            val userName = user.userMetadata?.get("name")?.jsonPrimitive?.content ?: "User"
            binding.headline.text = "Welcome, $userName"
            loadCameras()
        } else {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnAddCamera.setOnClickListener {
            startActivity(Intent(this, AddCameraActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            loadCameras()
        }
    }

    private fun loadCameras() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                val response = supabase.postgrest["cameras"]
                    .select { filter { eq("owner_id", userId) } }

                val cameras = response.decodeList<Camera>()

                withContext(Dispatchers.Main) {
                    binding.camerasContainer.removeAllViews()

                    if (cameras.isEmpty()) {
                        val noCameraText = TextView(this@MainActivity).apply {
                            text = "No cameras have been added yet. Please add a new camera!"
                            textSize = 16f
                            setTextColor(resources.getColor(R.color.red, theme))
                            setPadding(32, 32, 32, 32)
                        }
                        binding.camerasContainer.addView(noCameraText)
                    } else {
                        cameras.forEach { camera ->
                            val cameraView = LayoutInflater.from(this@MainActivity)
                                .inflate(R.layout.item_camera, binding.camerasContainer, false) as LinearLayout
                            cameraView.findViewById<TextView>(R.id.cameraLabel).text = camera.label

                            // Click to open camera
                            cameraView.setOnClickListener {
                                val intent = Intent(this@MainActivity, Esp32CamActivity::class.java)
                                intent.putExtra("camera_id", camera.camera_id)
                                intent.putExtra("camera_label", camera.label)
                                startActivity(intent)
                            }

                            // Add delete button logic (assume there's an ImageView with id deleteIcon)
                            cameraView.findViewById<ImageView>(R.id.deleteIcon)?.setOnClickListener {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Delete Camera")
                                    .setMessage("Are you sure you want to delete this camera?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        deleteCamera(camera.camera_id)
                                    }
                                    .setNegativeButton("No", null)
                                    .show()
                            }

                            binding.camerasContainer.addView(cameraView)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error when loading cameras: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun deleteCamera(cameraId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = supabase.postgrest["cameras"]
                    .delete { filter { eq("camera_id", cameraId) } }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Camera deleted", Toast.LENGTH_SHORT).show()
                    loadCameras()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to delete camera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signOutAndStartSignInActivity() {
        CoroutineScope(Dispatchers.IO).launch {
            supabase.auth.signOut()
            withContext(Dispatchers.Main) {
                val intent = Intent(this@MainActivity, SignInActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}

@Serializable
data class Camera(
    val camera_id: Int,
    val owner_id: String,
    val camera_index: Int,
    val label: String,
    val created_at: String? = null
)
