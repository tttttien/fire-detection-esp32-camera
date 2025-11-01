package com.example.camera_fire

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.ktor.client.plugins.logging.* // để bật log

object Supabase {
    @OptIn(SupabaseInternal::class)
    val client = createSupabaseClient(
        supabaseUrl = "https://bwmqzqgnouisgshuprhh.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ3bXF6cWdub3Vpc2dzaHVwcmhoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE4NDcxMTksImV4cCI6MjA3NzQyMzExOX0.mBHJz_2-MkyIMCx3L-5j8hb17FXnt6FWt-bDzyoUvz0"
    ) {
        install(Auth)
        httpConfig {
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
}
