package app.shouldersofgiants.guardian.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import app.shouldersofgiants.guardian.data.UserRole
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementScreen(
    viewModel: GuardianViewModel = viewModel(),
    onBack: () -> Unit
) {
    val familyMembers by viewModel.familyMembers.collectAsState()
    val context = LocalContext.current
    
    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Family", color = Color.White) },
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
                .padding(16.dp)
        ) {
            Text(
                text = "Family Members",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                LazyColumn(Modifier.padding(8.dp)) {
                    items(familyMembers) { member ->
                        MemberItem(
                            member = member,
                            onUpdateRole = { newRole ->
                                viewModel.updateUserRole(member.id, newRole) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Role updated for ${member.displayName}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to update role", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun MemberItem(
    member: app.shouldersofgiants.guardian.data.UserProfile,
    onUpdateRole: (UserRole) -> Unit
) {
    var showRoleMenu by remember { mutableStateOf(false) }

    val displayName = if (member.displayName.isNotBlank()) member.displayName else member.email
    val initials = if (member.displayName.isNotBlank()) {
        member.displayName.take(1).uppercase()
    } else if (member.email.isNotBlank()) {
        member.email.take(1).uppercase()
    } else "?"

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4285F4)),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        headlineContent = { Text(displayName, color = Color.White, fontWeight = FontWeight.Bold) },
        supportingContent = { 
            Column {
                if (member.displayName.isNotBlank() && member.email.isNotBlank()) {
                    Text(member.email, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "Role: ${member.role.name}", 
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showRoleMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Change Role", tint = Color.LightGray)
                }
                DropdownMenu(
                    expanded = showRoleMenu,
                    onDismissRequest = { showRoleMenu = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    UserRole.values().forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name, color = Color.White) },
                            onClick = {
                                onUpdateRole(role)
                                showRoleMenu = false
                            }
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
