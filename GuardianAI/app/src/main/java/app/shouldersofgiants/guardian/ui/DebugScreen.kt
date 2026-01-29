package app.shouldersofgiants.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    val logs by viewModel.voiceLogs.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    var hasAudioPermission by remember { mutableStateOf(false) }
    
    // Permission Launcher
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { result ->
        hasAudioPermission = result
        viewModel.addTestLog("Permission Request Result: $result")
    }

    LaunchedEffect(Unit) {
        hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        viewModel.addTestLog("Diagnostics Check:")
        viewModel.addTestLog("- Audio Permission: $hasAudioPermission")
        viewModel.addTestLog("- Speech Available: ${android.speech.SpeechRecognizer.isRecognitionAvailable(context)}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Debugger") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.addTestLog("Ping!") }) {
                        Text("Ping")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Diagnostics Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Diagnostics", color = Color.White, minLines = 1)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Mic Permission: ${if (hasAudioPermission) "GRANTED ✅" else "DENIED ❌"}",
                        color = if (hasAudioPermission) Color.Green else Color.Red
                    )
                    if (!hasAudioPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Request Mic Permission")
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
                }
            }
        }
    }
}
