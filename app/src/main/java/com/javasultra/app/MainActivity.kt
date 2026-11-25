package com.javasultra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                JavasApplicationShell()
            }
        }
    }
}

private enum class JavasDestination(val label: String, val symbol: String) {
    CHAT("Chat", "💬"),
    LEARN("Learn", "🧠"),
    MEMORY("Memory", "📚"),
    PROFILE("Profile", "👤")
}

@Composable
internal fun JavasApplicationShell() {
    var destination by rememberSaveable { mutableStateOf(JavasDestination.CHAT.name) }
    val selected = JavasDestination.valueOf(destination)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                JavasDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { destination = item.name },
                        icon = { Text(item.symbol) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("JAVAS ULTRA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Personal companion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("FOUNDATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            when (selected) {
                JavasDestination.CHAT -> ChatHome()
                JavasDestination.LEARN -> LearningHome()
                JavasDestination.MEMORY -> MemoryHome()
                JavasDestination.PROFILE -> ProfileHome()
            }
        }
    }
}

@Composable
private fun ChatHome() {
    Text("Good day, Creator.", style = MaterialTheme.typography.headlineMedium)
    Text(
        "JAVAS is being assembled. This screen is the conversation entry point; a live AI model is not connected yet.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "Conversation service",
        status = "Not connected",
        detail = "Messages cannot be sent to an AI provider from this build. No message is being silently stored or transmitted."
    )
    StatusCard(
        title = "Identity and sign-in",
        status = "Integration pending",
        detail = "The secure Creator session foundation is being developed separately. This screen does not claim that you are authenticated."
    )
    StatusCard(
        title = "Voice interaction",
        status = "Not enabled",
        detail = "Microphone access and voice processing remain disabled until their permission and privacy flows are implemented."
    )
}

@Composable
private fun LearningHome() {
    Text("Learning workspace", style = MaterialTheme.typography.headlineMedium)
    Text(
        "The planned learning cycle is inspect → identify a gap → plan → research → practise → evaluate → report.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "Learning coordinator",
        status = "Domain foundation in progress",
        detail = "The coordinator and research interfaces have been drafted. A complete live discovery and evidence-extraction provider is not configured."
    )
    StatusCard(
        title = "Autonomy boundary",
        status = "Creator approval required",
        detail = "JAVAS may prepare recommendations, but it cannot grant itself permissions, activate protected memory or perform restricted actions."
    )
    StatusCard(
        title = "Scheduled learning",
        status = "Not running",
        detail = "No background learning worker or schedule is active in this app shell."
    )
}

@Composable
private fun MemoryHome() {
    Text("Memory centre", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Memory must remain inspectable, correctable and under Creator control.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "Encrypted local storage",
        status = "Foundation drafted",
        detail = "Encryption and storage components exist in the project, but they are not yet connected to this screen or a complete memory workflow."
    )
    StatusCard(
        title = "Evidence and sources",
        status = "Policy foundation drafted",
        detail = "Retention rules distinguish verified knowledge from single-source or uncertain material. Evidence must pass its gates before knowledge can be activated."
    )
    StatusCard(
        title = "Your saved memories",
        status = "Not displayed yet",
        detail = "This screen does not read or expose stored memory yet. No sample entries are presented as real personal memories."
    )
}

@Composable
private fun ProfileHome() {
    Text("Creator and settings", style = MaterialTheme.typography.headlineMedium)
    StatusCard(
        title = "Creator account",
        status = "Authentication not connected",
        detail = "Sign-in, account recovery, device registration and session revocation still require a production authentication service."
    )
    StatusCard(
        title = "Registered device",
        status = "Local identity foundation drafted",
        detail = "The current app-scoped device identifier is not hardware attestation and is not proof of a trusted device."
    )
    StatusCard(
        title = "Permissions and capabilities",
        status = "Not connected",
        detail = "No device-action capabilities are enabled from this screen. Protected permissions must be granted by the Creator."
    )
    StatusCard(
        title = "Privacy and safety",
        status = "Core rules in development",
        detail = "No microphone, camera, location or background sensor collection is started by this foundation screen."
    )
}

@Composable
private fun StatusCard(title: String, status: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

