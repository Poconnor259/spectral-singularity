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
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatcherDashboard(
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
                title = { Text("Family Watcher", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
                actions = {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = "Debug", tint = Color.LightGray)
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
                        text = family?.name ?: "Loading Family...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Viewing role: Watcher",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Map Card
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Google Maps Integration Ready", color = Color.Gray)
                        Button(onClick = onNavigateToMap, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Open Map View")
                        }
                    }
                }
            }

            Text(
                text = "Recent Alerts",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Alert History placeholder
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No recent alerts found", color = Color.Gray)
                }
            }
        }
    }
}
