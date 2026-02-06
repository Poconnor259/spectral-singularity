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
    onNavigateToMap: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val family by viewModel.family.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showEditTriggerDialog by remember { mutableStateOf(false) }
    var selectedTrigger by remember { mutableStateOf<app.shouldersofgiants.guardian.data.TriggerPhrase?>(null) }
    
    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Manager", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
                actions = {
                    IconButton(onClick = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        // The MainActivity will detect the sign out and show login screen
                    }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Logout, 
                            contentDescription = "Logout", 
                            tint = Color.LightGray
                        )
                    }
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
                    onClick = { showAddMemberDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                ) {
                    androidx.compose.material3.Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Member")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trigger Phrases",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                IconButton(onClick = { 
                    selectedTrigger = null
                    showEditTriggerDialog = true 
                }) {
                    androidx.compose.material3.Icon(imageVector = Icons.Default.Add, contentDescription = "Add Phrase", tint = Color(0xFF34A853))
                }
            }

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
                                IconButton(onClick = { 
                                    selectedTrigger = trigger
                                    showEditTriggerDialog = true 
                                }) {
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
    
    // Add Member Dialog
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Family Member") },
            text = { Text("Share the invite code: ${family?.inviteCode ?: "---"}\n\nFamily members can join by entering this code in the app.") },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Edit/Add Trigger Dialog
    if (showEditTriggerDialog) {
        var phrase by remember { mutableStateOf(selectedTrigger?.phrase ?: "") }
        var severity by remember { mutableStateOf(selectedTrigger?.severity ?: app.shouldersofgiants.guardian.data.TriggerSeverity.CRITICAL) }

        AlertDialog(
            onDismissRequest = { 
                showEditTriggerDialog = false
                selectedTrigger = null
            },
            title = { Text(if (selectedTrigger == null) "Add Trigger Phrase" else "Edit Trigger Phrase") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        label = { Text("Phrase (e.g. Help)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Severity:", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        app.shouldersofgiants.guardian.data.TriggerSeverity.values().forEach { s ->
                            FilterChip(
                                selected = severity == s,
                                onClick = { severity = s },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        val currentPhrases = family?.triggerPhrases?.toMutableList() ?: mutableListOf()
                        if (selectedTrigger != null) {
                            val index = currentPhrases.indexOfFirst { it.phrase == selectedTrigger?.phrase }
                            if (index != -1) {
                                currentPhrases[index] = app.shouldersofgiants.guardian.data.TriggerPhrase(phrase, severity)
                            }
                        } else {
                            currentPhrases.add(app.shouldersofgiants.guardian.data.TriggerPhrase(phrase, severity))
                        }
                        viewModel.updateTriggerPhrases(currentPhrases)
                        showEditTriggerDialog = false
                        selectedTrigger = null
                    },
                    enabled = phrase.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                if (selectedTrigger != null) {
                    TextButton(onClick = {
                        val currentPhrases = family?.triggerPhrases?.filter { it.phrase != selectedTrigger?.phrase } ?: emptyList()
                        viewModel.updateTriggerPhrases(currentPhrases)
                        showEditTriggerDialog = false
                        selectedTrigger = null
                    }) {
                        Text("Delete", color = Color.Red)
                    }
                }
            }
        )
    }
}
