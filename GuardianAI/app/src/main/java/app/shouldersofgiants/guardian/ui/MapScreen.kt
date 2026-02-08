package app.shouldersofgiants.guardian.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import app.shouldersofgiants.guardian.data.Alert

@Composable
fun MapScreen(
    viewModel: app.shouldersofgiants.guardian.viewmodel.GuardianViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val defaultPos = LatLng(37.7749, -122.4194) // SF fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 12f)
    }

    // Auto-center logic
    LaunchedEffect(userProfile, activeAlerts) {
        if (activeAlerts.isNotEmpty()) {
            val alert = activeAlerts.first()
            if (alert.lat != null && alert.lng != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(alert.lat, alert.lng), 15f
                    )
                )
            }
        } else {
            // Priority: Hardware Location > Profile > Default
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(location.latitude, location.longitude), 15f
                                )
                            )
                        }
                    } else if (userProfile?.lastLat != null && userProfile?.lastLng != null) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(userProfile!!.lastLat!!, userProfile!!.lastLng!!), 12f
                                )
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                if (userProfile?.lastLat != null && userProfile?.lastLng != null) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(userProfile!!.lastLat!!, userProfile!!.lastLng!!), 12f
                    )
                }
            }
        }
    }

    val markerStates = remember { mutableStateMapOf<String, MarkerState>() }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = false),
            properties = MapProperties(isMyLocationEnabled = false)
        ) {
            // Alerts markers
            activeAlerts.forEach { alert ->
                if (alert.lat != null && alert.lng != null) {
                    MarkerComposable(
                        state = MarkerState(position = LatLng(alert.lat, alert.lng)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Alert Label
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = alert.type.replace("_", " ").uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Red Dot
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color = Color.Red, shape = CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            // Family members markers
            familyMembers.forEach { member ->
                if (member.lastLat != null && member.lastLng != null) {
                    val pos = LatLng(member.lastLat, member.lastLng)
                    val markerState = markerStates.getOrPut(member.id) { MarkerState(position = pos) }
                    markerState.position = pos

                    MarkerComposable(
                        state = markerState,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Name Bubble
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = member.displayName.ifBlank { "Unknown" }.substringBefore("@"),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Dot/Marker
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = if (member.id == userProfile?.id) Color(0xFF4285F4) else Color(0xFF673AB7),
                                        shape = CircleShape
                                    )
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
        }
        
        // Back button
        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
            containerColor = Color.White
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
        }

        // Center on Me button (Moved to Left Side)
        FloatingActionButton(
            onClick = {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(location.latitude, location.longitude), 15f
                                    )
                                )
                            }
                        } else {
                            userProfile?.let { profile ->
                                if (profile.lastLat != null && profile.lastLng != null) {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(profile.lastLat, profile.lastLng), 15f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {}
            },
            modifier = Modifier
                .align(Alignment.BottomStart) // Moved to Left
                .padding(bottom = 32.dp, start = 16.dp), // Extra padding to avoid Google logo
            containerColor = Color.White
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Center on Me", tint = Color(0xFF4285F4))
        }

        // Family Member Selector
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Focus Member", color = Color.Black)
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    familyMembers.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayName.ifBlank { member.email }, color = Color.Black) },
                            onClick = {
                                expanded = false
                                if (member.lastLat != null && member.lastLng != null) {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(member.lastLat, member.lastLng), 15f
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Clear Alerts Button
        if (activeAlerts.isNotEmpty()) {
            Button(
                onClick = { viewModel.resolveAlerts() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 80.dp, end = 16.dp), // Position below the dropdown
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Resolve Alerts", color = Color.White)
                }
            }
        }
    }
}
