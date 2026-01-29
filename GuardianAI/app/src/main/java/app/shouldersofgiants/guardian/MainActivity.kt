package app.shouldersofgiants.guardian

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.shouldersofgiants.guardian.ui.MainScreen
import app.shouldersofgiants.guardian.ui.theme.GuardianAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianAITheme {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                var isLoggedIn by remember { mutableStateOf(currentUser != null) }
                
                // Request Permissions
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Check results if needed
                }
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(
                            android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ))
                    } else {
                        permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                    }
                }
                
                // Check if launched from Panic Trigger
                val initialScreen = if (intent?.action == "ACTION_TRIGGER_PANIC") "alert" else "main"
                var currentScreen by remember { mutableStateOf(initialScreen) }

                // Update screen if new intent arrives while active
                DisposableEffect(Unit) {
                    val listener = androidx.core.util.Consumer<Intent> { newIntent ->
                        if (newIntent.action == "ACTION_TRIGGER_PANIC") {
                            currentScreen = "alert"
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                if (isLoggedIn) {
                    when (currentScreen) {
                        "main" -> MainScreen(
                            onNavigateToContacts = { currentScreen = "contacts" },
                            onPanicTrigger = { currentScreen = "alert" },
                            onNavigateToDebug = { currentScreen = "debug" }
                        )
                        "contacts" -> app.shouldersofgiants.guardian.ui.ContactsScreen(onBack = { currentScreen = "main" })
                        "alert" -> app.shouldersofgiants.guardian.ui.AlertScreen(
                            onCancel = { currentScreen = "main" },
                            onAlertSent = {
                                // Keep them on the AlertScreen but in "SENT" state, 
                                // or effectively the AlertScreen handles its own "SENT" UI, 
                                // and the "Return to Safety" button there calls onCancel (which goes to main)
                            }
                        )
                        "debug" -> app.shouldersofgiants.guardian.ui.DebugScreen(onBack = { currentScreen = "main" })
                    }
                } else {
                    app.shouldersofgiants.guardian.ui.LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                    })
                }
            }
        }
    }
}
