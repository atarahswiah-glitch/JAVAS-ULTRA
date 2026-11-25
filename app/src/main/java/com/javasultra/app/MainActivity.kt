package com.javasultra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.javasultra.app.core.ai.JavasGatewayConversationEngine
import com.javasultra.app.core.ai.SupabaseAuthSessionClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val supabaseUrl = BuildConfig.JAVAS_SUPABASE_URL
        val publishableKey = BuildConfig.JAVAS_SUPABASE_PUBLISHABLE_KEY
        val authClient = if (supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()) {
            SupabaseAuthSessionClient(
                context = applicationContext,
                projectUrl = supabaseUrl,
                publishableKey = publishableKey
            )
        } else {
            null
        }
        val conversationEngine = if (authClient != null) {
            JavasGatewayConversationEngine(
                functionUrl = "${supabaseUrl.trimEnd('/')}/functions/v1/javas-gateway",
                publishableKey = publishableKey,
                accessTokenProvider = authClient
            )
        } else {
            UnavailableConversationEngine
        }

        setContent {
            MaterialTheme {
                JavasCompanionScreen(
                    activity = this,
                    conversationEngine = conversationEngine,
                    authClient = authClient
                )
            }
        }
    }
}
