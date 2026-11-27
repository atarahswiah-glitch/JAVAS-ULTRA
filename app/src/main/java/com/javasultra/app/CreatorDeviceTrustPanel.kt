package com.javasultra.app

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javasultra.app.core.ai.SupabaseAuthSessionClient
import com.javasultra.app.core.security.DeviceKeyManager
import com.javasultra.app.core.security.JavasDeviceEnrollmentClient
import java.util.concurrent.Executors

@Composable
internal fun CreatorDeviceTrustPanel(
    activity: ComponentActivity,
    authClient: SupabaseAuthSessionClient?,
    onClose: () -> Unit
) {
    var creatorUserId by remember { mutableStateOf<String?>(null) }
    var targetDeviceId by remember { mutableStateOf("") }
    var currentDeviceId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking your signed-in account…") }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(worker) {
        onDispose { worker.shutdownNow() }
    }

    LaunchedEffect(authClient) {
        if (authClient == null) {
            status = "Sign in first. Supabase is not configured in this build."
        } else {
            worker.execute {
                val result = runCatching { authClient.verifyCurrentUser() }
                mainHandler.post {
                    creatorUserId = result.getOrNull()?.id
                    status = when {
                        result.isFailure -> "Unable to verify your saved account. Reconnect and sign in again."
                        creatorUserId == null -> "No active signed-in account was found."
                        else -> "Account identity loaded. The server will separately verify Creator authority and recent multi-factor authentication."
                    }
                }
            }
        }
    }

    val userId = creatorUserId
    val keyManager = remember(userId) {
        userId?.let { DeviceKeyManager(activity.applicationContext, it) }
    }
    val enrollmentClient = remember(userId, authClient) {
        if (userId != null && authClient != null &&
            BuildConfig.JAVAS_SUPABASE_URL.startsWith("https://") &&
            BuildConfig.JAVAS_SUPABASE_PUBLISHABLE_KEY.isNotBlank()
        ) {
            JavasDeviceEnrollmentClient(
                functionUrl = "${BuildConfig.JAVAS_SUPABASE_URL.trimEnd('/')}/functions/v1/javas-device-enrollment",
                publishableKey = BuildConfig.JAVAS_SUPABASE_PUBLISHABLE_KEY,
                accessTokenProvider = authClient,
                deviceKeyManager = requireNotNull(keyManager)
            )
        } else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("TRUSTED DEVICE MANAGEMENT", color = Color(0xFF8DEBFF), fontSize = 11.sp, letterSpacing = 1.2.sp)
                Text("Your devices", color = Color(0xFFE8F8FF), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text(
            "A new phone must enrol first and remain pending. An already verified phone must explicitly sign its approval challenge before the new phone becomes trusted.",
            color = Color(0xFF9CB7C5), fontSize = 12.sp
        )
        if (currentDeviceId != null) {
            Text("This installation: $currentDeviceId", color = Color(0xFF9BE8C5), fontSize = 11.sp)
        }
        Button(
            onClick = {
                val client = enrollmentClient
                if (client == null) {
                    status = "Device service is not configured or you are not signed in."
                } else {
                    busy = true
                    status = "Requesting a fresh server challenge and signing it in Android Keystore…"
                    worker.execute {
                        val result = runCatching { client.enrollThisDevice() }
                        mainHandler.post {
                            busy = false
                            result.onSuccess {
                                currentDeviceId = it.deviceId
                                status = "Enrolment proof accepted. This device is PENDING_APPROVAL, not yet trusted. Use this device ID on your already trusted phone to approve it."
                            }.onFailure {
                                status = it.message?.takeIf(String::isNotBlank)
                                    ?: "Device enrolment failed. Check connectivity and server configuration."
                            }
                        }
                    }
                }
            },
            enabled = !busy && enrollmentClient != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait…" else "Enrol this phone")
        }
        OutlinedTextField(
            value = targetDeviceId,
            onValueChange = { targetDeviceId = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pending device ID to approve") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        Button(
            onClick = {
                val client = enrollmentClient
                if (client == null) {
                    status = "Device service is not configured or you are not signed in."
                } else {
                    busy = true
                    status = "Requesting an approval challenge bound to this trusted device and the target…"
                    worker.execute {
                        val result = runCatching { client.approvePendingDevice(targetDeviceId) }
                        mainHandler.post {
                            busy = false
                            result.onSuccess {
                                status = "Server accepted the approval. Device ${it.deviceId} is now ${it.state}."
                                targetDeviceId = ""
                            }.onFailure {
                                status = it.message?.takeIf(String::isNotBlank)
                                    ?: "Device approval failed. Check the target ID and trusted-device status."
                            }
                        }
                    }
                }
            },
            enabled = !busy && enrollmentClient != null && targetDeviceId.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Approve pending device")
        }
        if (status.isNotBlank()) {
            Text(status, color = if (status.contains("PENDING_APPROVAL") || status.contains("accepted")) Color(0xFF9BE8C5) else Color(0xFFFFD18B), fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Important: approval requires a fresh server-verified MFA step-up. This screen does not treat a password, local biometric prompt or a client-side claim as Creator approval. The Edge Function must be deployed and the Creator MFA session configured before these actions can succeed.",
            color = Color(0xFF8BA8B9), fontSize = 10.sp
        )
    }
}
