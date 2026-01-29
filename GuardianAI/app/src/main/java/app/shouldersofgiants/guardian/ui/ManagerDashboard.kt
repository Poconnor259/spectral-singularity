package app.shouldersofgiants.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.data.UserRole
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerDashboard(
    viewModel: GuardianViewModel = viewModel(),
    onNavigateToDebug: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val family by viewModel.family.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    
    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Manager", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
                actions = {
                    IconButton(onClick = onNavigateToDebug) {
                        androidx.compose.material3.Icon(imageVector = Icons.Default.Build, contentDescription = "Debug", tint = Color.LightGray)
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(brush)
                .padding(16.dp)
        ) {
            // Family Summary Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = family?.name ?: "Loading...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Invite Code: ${family?.inviteCode ?: "---"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4285F4),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Share this code with family members to join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToMap,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
                ) {
                    androidx.compose.material3.Icon(imageVector = Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Map")
                }
                
                Button(
                    onClick = { /* Add Manual Member */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                ) {
                    androidx.compose.material3.Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Member")
                }
            }

            Text(
                text = "Trigger Phrases",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Trigger Phrases List
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                LazyColumn(Modifier.padding(8.dp)) {
                    val triggers = family?.triggerPhrases ?: emptyList()
                    items(triggers) { trigger ->
                        ListItem(
                            headlineContent = { Text(trigger.phrase, color = Color.White) },
                            supportingContent = { Text("Severity: ${trigger.severity}", color = Color.Gray) },
                            trailingContent = {
                                IconButton(onClick = {}) {
                                    androidx.compose.material3.Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.LightGray)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
