package app.shouldersofgiants.guardian

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.ui.MainScreen
import app.shouldersofgiants.guardian.ui.RoleSelectionScreen
import app.shouldersofgiants.guardian.ui.theme.GuardianAITheme
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel
import app.shouldersofgiants.guardian.data.UserRole
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianAITheme {
                val viewModel: GuardianViewModel = viewModel()
                val userProfile by viewModel.userProfile.collectAsState()
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                
                DisposableEffect(auth) {
                    val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                        if (!isLoggedIn) {
                            viewModel.clearState()
                        }
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }
                
                // Request Permissions
                val backgroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) {}

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val allGranted = results.values.all { it }
                    if (allGranted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
                
                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.SEND_SMS
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        permissions.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
                
                val initialScreen = if (intent?.action == "ACTION_TRIGGER_PANIC") "alert" else "main"
                var currentScreen by remember { mutableStateOf(initialScreen) }
                var triggerType by remember { mutableStateOf(intent?.getStringExtra("EXTRA_TRIGGER_TYPE") ?: "PANIC_BUTTON") }
                var triggerPhrase by remember { mutableStateOf(intent?.getStringExtra("EXTRA_TRIGGER_PHRASE")) }

                DisposableEffect(Unit) {
                    val listener = androidx.core.util.Consumer<Intent> { newIntent ->
                        if (newIntent.action == "ACTION_TRIGGER_PANIC") {
                            triggerType = newIntent.getStringExtra("EXTRA_TRIGGER_TYPE") ?: "PANIC_BUTTON"
                            triggerPhrase = newIntent.getStringExtra("EXTRA_TRIGGER_PHRASE")
                            currentScreen = "alert"
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                if (isLoggedIn) {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = currentScreen != "map",
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF1A1A1A),
                                drawerContentColor = Color.White
                            ) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Guardian AI",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider(color = Color.DarkGray)
                                NavigationDrawerItem(
                                    label = { Text("Dashboard") },
                                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                                    selected = currentScreen == "main",
                                    onClick = {
                                        currentScreen = "main"
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Profile") },
                                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    selected = currentScreen == "profile",
                                    onClick = {
                                        currentScreen = "profile"
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Map") },
                                    icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                                    selected = currentScreen == "map",
                                    onClick = {
                                        currentScreen = "map"
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                if (userProfile?.role == UserRole.MANAGER) {
                                    NavigationDrawerItem(
                                        label = { Text("Manage Family") },
                                        icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                                        selected = currentScreen == "family_management",
                                        onClick = {
                                            currentScreen = "family_management"
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                    )
                                }
                                NavigationDrawerItem(
                                    label = { Text("Contacts") },
                                    icon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                                    selected = currentScreen == "contacts",
                                    onClick = {
                                        currentScreen = "contacts"
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Debug") },
                                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                                    selected = currentScreen == "debug",
                                    onClick = {
                                        currentScreen = "debug"
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                Spacer(Modifier.weight(1f))
                                NavigationDrawerItem(
                                    label = { Text("Logout") },
                                    icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                                    selected = false,
                                    onClick = {
                                        auth.signOut()
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.LightGray, unselectedIconColor = Color.LightGray)
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    ) {
                        val activeAlerts by viewModel.activeAlerts.collectAsState()
                        var dismissedAlerts by remember { mutableStateOf(setOf<String>()) }
                        val snackbarHostState = remember { SnackbarHostState() }

                        val currentAlert = activeAlerts.firstOrNull { 
                            it.id !in dismissedAlerts && 
                            it.userId != userProfile?.id && 
                            userProfile?.role != UserRole.PROTECTED 
                        }

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = Color.Black
                        ) { padding ->
                            Surface(color = Color.Black, modifier = Modifier.padding(padding)) {
                                when {
                                    userProfile == null -> {
                                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                    userProfile?.role == UserRole.UNDECIDED -> {
                                        RoleSelectionScreen(viewModel = viewModel)
                                    }
                                    else -> {
                                        when (currentScreen) {
                                            "profile" -> app.shouldersofgiants.guardian.ui.ProfileScreen(
                                                viewModel = viewModel,
                                                onBack = { currentScreen = "main" }
                                            )
                                            "main" -> {
                                                when (userProfile?.role) {
                                                    UserRole.MANAGER -> app.shouldersofgiants.guardian.ui.ManagerDashboard(
                                                        viewModel = viewModel,
                                                        onNavigateToDebug = { currentScreen = "debug" },
                                                        onNavigateToMap = { currentScreen = "map" },
                                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                                    )
                                                    UserRole.WATCHER -> app.shouldersofgiants.guardian.ui.WatcherDashboard(
                                                        viewModel = viewModel,
                                                        onNavigateToDebug = { currentScreen = "debug" },
                                                        onNavigateToMap = { currentScreen = "map" },
                                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                                    )
                                                    else -> MainScreen(
                                                        onNavigateToContacts = { currentScreen = "contacts" },
                                                        onPanicTrigger = { currentScreen = "alert" },
                                                        onNavigateToDebug = { currentScreen = "debug" },
                                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                                        viewModel = viewModel
                                                    )
                                                }
                                            }
                                            "contacts" -> app.shouldersofgiants.guardian.ui.ContactsScreen(onBack = { currentScreen = "main" })
                                            "alert" -> app.shouldersofgiants.guardian.ui.AlertScreen(
                                                onCancel = { currentScreen = "main" },
                                                onAlertSent = {},
                                                triggerType = triggerType,
                                                triggerPhrase = triggerPhrase
                                            )
                                            "debug" -> app.shouldersofgiants.guardian.ui.DebugScreen(onBack = { currentScreen = "main" })
                                            "map" -> app.shouldersofgiants.guardian.ui.MapScreen(
                                                viewModel = viewModel,
                                                onBack = { currentScreen = "main" }
                                            )
                                            "family_management" -> app.shouldersofgiants.guardian.ui.FamilyManagementScreen(
                                                viewModel = viewModel,
                                                onBack = { currentScreen = "main" }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Emergency Overlay
                        currentAlert?.let { alert ->
                            if (currentScreen != "map") {
                                app.shouldersofgiants.guardian.ui.EmergencyOverlay(
                                    alert = alert,
                                    onViewMap = {
                                        currentScreen = "map"
                                    },
                                    onDismiss = {
                                        dismissedAlerts = dismissedAlerts + alert.id
                                    }
                                )
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
