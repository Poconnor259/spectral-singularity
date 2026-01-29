package app.shouldersofgiants.guardian

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.ui.MainScreen
import app.shouldersofgiants.guardian.ui.RoleSelectionScreen
import app.shouldersofgiants.guardian.ui.theme.GuardianAITheme
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel
import app.shouldersofgiants.guardian.data.UserRole

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianAITheme {
                val viewModel: GuardianViewModel = viewModel()
                val userProfile by viewModel.userProfile.collectAsState()
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                var isLoggedIn by remember { mutableStateOf(currentUser != null) }
                
                // Request Permissions (Location added for Map feature)
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) {}
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
                
                val initialScreen = if (intent?.action == "ACTION_TRIGGER_PANIC") "alert" else "main"
                var currentScreen by remember { mutableStateOf(initialScreen) }

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
                    when {
                        userProfile == null -> {
                            // Loading profile
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        userProfile?.role == UserRole.UNDECIDED -> {
                            RoleSelectionScreen(viewModel = viewModel)
                        }
                        else -> {
                            // Role-based navigation
                            when (currentScreen) {
                                "main" -> {
                                    when (userProfile?.role) {
                                        UserRole.MANAGER -> app.shouldersofgiants.guardian.ui.ManagerDashboard(
                                            viewModel = viewModel,
                                            onNavigateToDebug = { currentScreen = "debug" },
                                            onNavigateToMap = { currentScreen = "map" }
                                        )
                                        UserRole.WATCHER -> app.shouldersofgiants.guardian.ui.WatcherDashboard(
                                            viewModel = viewModel,
                                            onNavigateToDebug = { currentScreen = "debug" },
                                            onNavigateToMap = { currentScreen = "map" }
                                        )
                                        UserRole.PROTECTED -> MainScreen(
                                            onNavigateToContacts = { currentScreen = "contacts" },
                                            onPanicTrigger = { currentScreen = "alert" },
                                            onNavigateToDebug = { currentScreen = "debug" },
                                            viewModel = viewModel
                                        )
                                        else -> MainScreen(
                                            onNavigateToContacts = { currentScreen = "contacts" },
                                            onPanicTrigger = { currentScreen = "alert" },
                                            onNavigateToDebug = { currentScreen = "debug" },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                                "contacts" -> app.shouldersofgiants.guardian.ui.ContactsScreen(onBack = { currentScreen = "main" })
                                "alert" -> app.shouldersofgiants.guardian.ui.AlertScreen(onCancel = { currentScreen = "main" }, onAlertSent = {})
                                "debug" -> app.shouldersofgiants.guardian.ui.DebugScreen(onBack = { currentScreen = "main" })
                                "map" -> {
                                    val alerts by viewModel.activeAlerts.collectAsState()
                                    app.shouldersofgiants.guardian.ui.MapScreen(
                                        activeAlerts = alerts,
                                        onBack = { currentScreen = "main" }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    app.shouldersofgiants.guardian.ui.LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                        viewModel.fetchUserProfile()
                    })
                }
            }
        }
    }
}
