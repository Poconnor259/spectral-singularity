package app.shouldersofgiants.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.data.UserRole
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@Composable
fun RoleSelectionScreen(
    viewModel: GuardianViewModel = viewModel()
) {
    var step by remember { mutableStateOf("choices") }
    var familyName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    val userProfile by viewModel.userProfile.collectAsState()
    
    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF4285F4))
                Spacer(Modifier.height(16.dp))
                Text("Processing...", color = Color.White)
            } else {
                Text(
                    text = "Welcome to Guardian AI",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "How will you be using the app?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                when (step) {
                    "choices" -> {
                        RoleChoices(
                            onCreateFamily = { step = "create" },
                            onJoinFamily = { step = "join_code" }
                        )
                    }
                    "create" -> {
                        CreateFamilyUI(
                            name = familyName,
                            onNameChange = { familyName = it },
                            onConfirm = { 
                                isLoading = true
                                viewModel.createFamily(familyName, onError = { msg ->
                                    isLoading = false
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }) 
                            },
                            onBack = { step = "choices" }
                        )
                    }
                    "join_code" -> {
                        JoinFamilyUI(
                            code = inviteCode,
                            onCodeChange = { inviteCode = it },
                            onJoin = { role -> 
                                isLoading = true
                                viewModel.joinFamily(inviteCode, role, onError = { msg ->
                                    isLoading = false
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                })
                            },
                            onBack = { step = "choices" }
                        )
                    }
                }
            }
        }
    }
    
    // Reset loading if profile changes (indicating success)
    LaunchedEffect(userProfile) {
        if (userProfile?.role != UserRole.UNDECIDED) {
            isLoading = false
        }
    }
}

@Composable
fun RoleChoices(onCreateFamily: () -> Unit, onJoinFamily: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = onCreateFamily,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
        ) {
            Text("I'm the Family Manager\n(Start a new family)", textAlign = TextAlign.Center)
        }

        OutlinedButton(
            onClick = onJoinFamily,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("I'm Joining a Family\n(I have an invite code)", textAlign = TextAlign.Center, color = Color.White)
        }
    }
}

@Composable
fun CreateFamilyUI(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Family Name (e.g. The Smiths)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Button(
            onClick = onConfirm,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Family")
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back", color = Color.LightGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFamilyUI(
    code: String,
    onCodeChange: (String) -> Unit,
    onJoin: (UserRole) -> Unit,
    onBack: () -> Unit
) {
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Enter 6-digit Invite Code") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        
        Text("I am joining as:", color = Color.LightGray)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedRole == UserRole.WATCHER,
                onClick = { selectedRole = UserRole.WATCHER },
                label = { Text("Watcher") }
            )
            FilterChip(
                selected = selectedRole == UserRole.PROTECTED,
                onClick = { selectedRole = UserRole.PROTECTED },
                label = { Text("Being Watched") }
            )
        }

        Button(
            onClick = { selectedRole?.let { onJoin(it) } },
            enabled = code.length == 6 && selectedRole != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join Family")
        }
        
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back", color = Color.LightGray)
        }
    }
}
