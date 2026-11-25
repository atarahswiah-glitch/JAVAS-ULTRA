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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JavasApplicationShell()
                }
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
                    Text("Your personal companion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var draft by rememberSaveable { mutableStateOf("") }

    Text("Good day, Creator.", style = MaterialTheme.typography.headlineMedium)
    Text(
        "This is where your conversations with JAVAS will happen. The secure conversation engine is being connected to the app.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "AI connection",
        status = "Not connected",
        detail = "No live AI provider is configured. JAVAS will not pretend to answer or send your private messages to an unapproved service."
    )
    StatusCard(
        title = "Creator protection",
        status = "Connection required",
        detail = "A verified Creator session must be active before private memory or a live conversation can be used."
    )

    Text("Message composer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= 12_000) draft = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = true,
        minLines = 3,
        maxLines = 7,
        label = { Text("Write a message") },
        placeholder = { Text("Your message draft stays on this screen; sending is not available yet.") },
        supportingText = { Text("${draft.length} / 12,000 characters · Not sent or stored as chat history") }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { /* Deliberately unavailable until authenticated model routing is wired. */ }, enabled = false) {
            Text("Send")
        }
        TextButton(onClick = { draft = "" }, enabled = draft.isNotEmpty()) {
            Text("Clear draft")
        }
    }
    Text(
        "Voice, attachments, conversation history and message sending will be enabled only after their privacy and security integrations are complete.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LearningHome() {
    Text("Learning workspace", style = MaterialTheme.typography.headlineMedium)
    Text(
        "JAVAS is being designed to improve through a controlled learning cycle.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "Learning cycle",
        status = "Architecture in progress",
        detail = "Inspect → identify a gap → plan → research → practise → evaluate → report. Each stage must keep evidence and its completion status."
    )
    StatusCard(
        title = "Research sources",
        status = "Not connected",
        detail = "The secure public-research transport is a foundation only. Live search discovery and evidence extraction still need approved providers."
    )
    StatusCard(
        title = "Autonomy boundary",
        status = "Creator remains in control",
        detail = "JAVAS may prepare plans and recommendations. It cannot grant itself permissions, activate protected memory or perform restricted actions."
    )
    StatusCard(
        title = "Scheduled learning",
        status = "Not running",
        detail = "No background learning worker or schedule is active in this app."
    )
}

@Composable
private fun MemoryHome() {
    var searchDraft by rememberSaveable { mutableStateOf("") }

    Text("Memory centre", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Your memory should be accurate, private, searchable and under your control.",
        style = MaterialTheme.typography.bodyLarge
    )
    StatusCard(
        title = "Encrypted local memory",
        status = "Storage foundation exists · screen not connected",
        detail = "The encrypted repository and access rules are present in the project. This screen does not yet read, create, edit or delete records."
    )
    OutlinedTextField(
        value = searchDraft,
        onValueChange = { if (it.length <= 300) searchDraft = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        singleLine = true,
        label = { Text("Search your memories") },
        placeholder = { Text("Available after Creator sign-in") }
    )
    StatusCard(
        title = "Your saved memories",
        status = "Not loaded",
        detail = "No entries are shown because the app has no verified Creator session connected to the memory screen. No sample memories are being presented as yours."
    )
    StatusCard(
        title = "Memory controls",
        status = "Not connected",
        detail = "Review, correct, confirm, expire, export and delete controls will be connected with owner checks and step-up authentication for highly sensitive records."
    )
    StatusCard(
        title = "Evidence quality",
        status = "Policy foundation exists",
        detail = "Unverified, single-source, corroborated, Creator-confirmed and disputed information must remain clearly distinguished."
    )
}

@Composable
private fun ProfileHome() {
    Text("Creator and settings", style = MaterialTheme.typography.headlineMedium)
    StatusCard(
        title = "Creator account",
        status = "Authentication provider not connected",
        detail = "The local session coordinator is drafted. A trusted account service, sign-in UI, recovery and server-side verification are still required."
    )
    StatusCard(
        title = "Registered device",
        status = "Local identity foundation",
        detail = "The app-scoped device identifier is not hardware attestation and does not independently prove that a device is trusted."
    )
    StatusCard(
        title = "Permissions and capabilities",
        status = "No capabilities enabled",
        detail = "JAVAS cannot install or grant itself tools. Each protected capability must have a clear purpose, permission, scope and revocation path."
    )
    StatusCard(
        title = "Privacy",
        status = "Sensors inactive",
        detail = "This foundation screen does not start microphone, camera, location or background sensor collection."
    )
    StatusCard(
        title = "Build status",
        status = "Development build",
        detail = "The current app is not a production-ready companion. Build, automated tests and real-device verification are still required for the latest changes."
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
