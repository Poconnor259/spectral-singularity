package app.shouldersofgiants.guardian.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: GuardianViewModel = viewModel(),
    onBack: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    
    var displayName by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(brush)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF4285F4), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initials = if (displayName.isNotBlank()) displayName.take(1).uppercase() else "?"
                Text(initials, color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = userProfile?.email ?: "",
                onValueChange = {},
                label = { Text("Email (Sync'd from login)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Gray,
                    unfocusedTextColor = Color.Gray,
                    disabledTextColor = Color.Gray
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            // Background Monitor Section
            val isProtected = userProfile?.role == app.shouldersofgiants.guardian.data.UserRole.PROTECTED
            val isListening by viewModel.isListening.collectAsState()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Background Monitor",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isProtected) "Controlled by Family Manager" else "Auto-detect cries for help",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isListening,
                    onCheckedChange = { if (!isProtected) viewModel.toggleListeningMode(it) },
                    enabled = !isProtected
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Trigger Words",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold
            )
            Text(
                "Words that activate the system",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val phrases = userProfile?.triggerPhrases ?: emptyList()
                if (phrases.isEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Standard Defaults") },
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.LightGray)
                    )
                } else {
                    phrases.forEach { trigger ->
                        AssistChip(
                            onClick = {},
                            label = { Text(trigger.phrase) },
                            leadingIcon = {
                                if (trigger.severity == app.shouldersofgiants.guardian.data.TriggerSeverity.CRITICAL) {
                                    Icon(Icons.Default.Warning, "Critical", modifier = Modifier.size(14.dp), tint = Color.Red)
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = Color.White,
                                containerColor = Color.DarkGray
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Location Tracking",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    enabled = !isProtected,
                    selected = userProfile?.locationTrackingMode == "ALERT_ONLY",
                    onClick = { viewModel.updateLocationTrackingMode("ALERT_ONLY") },
                    label = { Text("Alert Only") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.LightGray,
                        selectedLabelColor = Color.White,
                        selectedContainerColor = Color(0xFF4285F4),
                        disabledLabelColor = Color.DarkGray
                    )
                )
                FilterChip(
                    enabled = !isProtected,
                    selected = userProfile?.locationTrackingMode == "ALWAYS",
                    onClick = { viewModel.updateLocationTrackingMode("ALWAYS") },
                    label = { Text("Always Track") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.LightGray,
                        selectedLabelColor = Color.White,
                        selectedContainerColor = Color(0xFF4285F4),
                        disabledLabelColor = Color.DarkGray
                    )
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    isSaving = true
                    viewModel.updateUserProfile(displayName) { success ->
                        isSaving = false
                        if (success) {
                            Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isSaving && displayName.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
