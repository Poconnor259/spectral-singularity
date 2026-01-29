package app.shouldersofgiants.guardian.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import app.shouldersofgiants.guardian.data.Alert

@Composable
fun MapScreen(
    activeAlerts: List<Alert>,
    onBack: () -> Unit
) {
    val singapore = LatLng(1.35, 103.87) // Default fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    // Attempt to center on the first active alert
    LaunchedEffect(activeAlerts) {
        if (activeAlerts.isNotEmpty()) {
            val alert = activeAlerts.first()
            if (alert.lat != null && alert.lng != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(alert.lat, alert.lng), 15f
                )
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        activeAlerts.forEach { alert ->
            if (alert.lat != null && alert.lng != null) {
                Marker(
                    state = MarkerState(position = LatLng(alert.lat, alert.lng)),
                    title = "ALERT: ${alert.type}",
                    snippet = "Triggered: ${alert.triggerPhrase ?: "Manual"}",
                    onClick = { false }
                )
            }
        }
    }
}
