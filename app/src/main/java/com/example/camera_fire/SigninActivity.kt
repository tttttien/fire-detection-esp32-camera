package com.example.camera_fire

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Credential Manager + Google Identity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// Supabase v3
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class SignInActivity : AppCompatActivity() {

    private val supabase get() = Supabase.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        // Nếu đã có phiên → vào Main luôn
        CoroutineScope(Dispatchers.Main).launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                goToMain(); return@launch
            }
        }

        findViewById<Button>(R.id.signInButton).setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun startGoogleSignIn() {
        val credentialManager = CredentialManager.create(this)

        // 1) Tạo nonce
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // 2) GoogleIdOption với **WEB CLIENT ID**
        val webClientId = getString(R.string.web_client_id)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        // 3) Lấy ID token và đăng nhập Supabase
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@SignInActivity
                )
                val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
                val googleIdToken = cred.idToken

                withContext(Dispatchers.IO) {
                    supabase.auth.signInWith(IDToken) {
                        idToken = googleIdToken
                        provider = Google
                        nonce = rawNonce        // Supabase nhận rawNonce
                    }
                }

                Toast.makeText(this@SignInActivity, "Sign-in thành công", Toast.LENGTH_SHORT).show()
                goToMain()

            } catch (e: GetCredentialException) {
                Toast.makeText(this@SignInActivity, "Credential lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: GoogleIdTokenParsingException) {
                Toast.makeText(this@SignInActivity, "ID token parse lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@SignInActivity, "Lỗi khác: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
