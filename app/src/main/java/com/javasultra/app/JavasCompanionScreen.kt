package com.javasultra.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CREATOR_SETTINGS = "javas_creator_settings"
private const val VOICE_SAMPLE_URI = "voice_sample_uri"
private const val PERSONAL_IMAGE_URI = "personal_image_uri"
private const val VOICE_MODE = "voice_mode"

@Composable
internal fun JavasCompanionScreen(activity: ComponentActivity) {
    val preferences = remember(activity) {
        activity.getSharedPreferences(CREATOR_SETTINGS, Context.MODE_PRIVATE)
    }
    var expanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var voiceSample by remember { mutableStateOf(preferences.getString(VOICE_SAMPLE_URI, null)) }
    var personalImage by remember { mutableStateOf(preferences.getString(PERSONAL_IMAGE_URI, null)) }
    var voiceMode by remember { mutableStateOf(preferences.getString(VOICE_MODE, "JAVAS") ?: "JAVAS") }

    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainReadPermission(activity, uri)
            voiceSample = uri.toString()
            preferences.edit().putString(VOICE_SAMPLE_URI, voiceSample).apply()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainReadPermission(activity, uri)
            personalImage = uri.toString()
            preferences.edit().putString(PERSONAL_IMAGE_URI, personalImage).apply()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050B14)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF071522), Color(0xFF050B14), Color(0xFF02060C))
                    )
                )
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "JAVAS ULTRA",
                        color = Color(0xFFE8F8FF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text("Your personal companion", color = Color(0xFF8BA8B9), fontSize = 12.sp)
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Text("⚙", color = Color(0xFF8DEBFF), fontSize = 25.sp)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val globeSize by animateFloatAsState(
                    targetValue = if (expanded) 218f else 158f,
                    label = "companion-globe-size"
                )
                Canvas(
                    modifier = Modifier
                        .size(globeSize.dp)
                        .clip(CircleShape)
                        .clickable { expanded = !expanded }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.37f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF56E9FF), Color(0xFF087AAB), Color(0xFF061C38)),
                            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.3f),
                            radius = radius * 2.3f
                        ),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF9AF5FF).copy(alpha = 0.65f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawOval(
                        color = Color(0xFF9AF5FF).copy(alpha = 0.7f),
                        topLeft = Offset(center.x - radius * 1.28f, center.y - radius * 0.34f),
                        size = Size(radius * 2.56f, radius * 0.68f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    drawOval(
                        color = Color(0xFF5ACDEB).copy(alpha = 0.55f),
                        topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 1.18f),
                        size = Size(radius * 1.1f, radius * 2.36f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.65f),
                        startAngle = 205f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius * 0.76f, center.y - radius * 0.76f),
                        size = Size(radius * 1.52f, radius * 1.52f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius * 0.045f,
                        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.36f)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    if (expanded) "JAVAS is here" else "Tap the globe to connect",
                    color = Color(0xFFE4F8FF),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (expanded) "Companion view • interaction foundation" else "Your companion stays close, never in the way",
                    color = Color(0xFF8BA8B9),
                    fontSize = 12.sp
                )
                if (expanded) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFF102A3A).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ready for your direction", color = Color(0xFFE7FAFF), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Conversation, live model connection and device actions are not connected yet.",
                                color = Color(0xFF9CB7C5),
                                fontSize = 12.sp
                            )
                            TextButton(onClick = { expanded = false }) { Text("Close companion view") }
                        }
                    }
                }
            }

            Text(
                "PRIVATE • CREATOR CONTROLLED",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                color = Color(0xFF587789),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )

            if (showSettings) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    color = Color(0xFF102333),
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 18.dp
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Companion settings",
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE8F8FF),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { showSettings = false }) { Text("Close") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("SPEAKING VOICE", color = Color(0xFF79DFF2), fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = voiceMode == "JAVAS",
                                onClick = {
                                    voiceMode = "JAVAS"
                                    preferences.edit().putString(VOICE_MODE, voiceMode).apply()
                                }
                            )
                            Text("JAVAS voice", color = Color(0xFFE5F5FA), fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = voiceMode == "CREATOR",
                                onClick = {
                                    voiceMode = "CREATOR"
                                    preferences.edit().putString(VOICE_MODE, voiceMode).apply()
                                }
                            )
                            Text("My real voice", color = Color(0xFFE5F5FA), fontSize = 14.sp)
                        }
                        TextButton(
                            onClick = { voicePicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (voiceSample == null) "Choose my voice recording" else "Change my voice recording")
                        }
                        Text(
                            if (voiceSample == null) "No voice sample selected."
                            else "Voice sample selected and referenced on this device.",
                            color = Color(0xFF9CB7C5),
                            fontSize = 11.sp
                        )
                        Text(
                            "Voice cloning and speech generation are not connected yet; selecting a recording does not activate a voice clone.",
                            color = Color(0xFFFFD18B),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("PERSONAL MATERIAL", color = Color(0xFF79DFF2), fontSize = 10.sp, letterSpacing = 1.2.sp)
                        TextButton(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (personalImage == null) "Choose a personal image" else "Change personal image")
                        }
                        Text(
                            if (personalImage == null) "No personal image selected."
                            else "Image reference selected on this device.",
                            color = Color(0xFF9CB7C5),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Files remain on this device in this foundation build. No upload to a server occurs.",
                            color = Color(0xFF7E9AA9),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun retainReadPermission(activity: ComponentActivity, uri: Uri) {
    try {
        activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Some document providers do not offer persistable grants; keep the current session usable.
    }
}
