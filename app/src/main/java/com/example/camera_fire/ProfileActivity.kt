package com.example.camera_fire

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import io.github.jan.supabase.auth.auth      // ✅ auth của v3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

class ProfileActivity : AppCompatActivity() {

    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // ✅ Lấy user từ Supabase v3
        val user = supabase.auth.currentUserOrNull()

        user?.let {
            findViewById<TextView>(R.id.userName).text =
                "Name: ${it.userMetadata?.get("name")?.jsonPrimitive?.content ?: "Unknown"}"
            findViewById<TextView>(R.id.userEmail).text =
                "Email: ${it.email ?: "Unknown"}"
        }

        // Nút logout
        findViewById<Button>(R.id.logout_button).setOnClickListener {
            signOutAndStartSignInActivity()
        }
    }

    private fun signOutAndStartSignInActivity() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Unregister FCM token before logout to prevent notification leaks
                val token = Tasks.await(com.google.firebase.messaging.FirebaseMessaging.getInstance().token)
                val apiService = com.example.camera_fire.network.RetrofitClient.apiService
                apiService.unregisterToken(mapOf("token" to token))
                android.util.Log.d("ProfileActivity", "Token unregistered on logout")
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "Failed to unregister token", e)
            }
            
            supabase.auth.signOut() // ✅ API v3
            val intent = Intent(this@ProfileActivity, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
