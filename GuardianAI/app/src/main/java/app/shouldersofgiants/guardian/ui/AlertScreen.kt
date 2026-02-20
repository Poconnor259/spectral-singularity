package app.shouldersofgiants.guardian.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel
import kotlinx.coroutines.delay

@Composable
fun AlertScreen(
    onCancel: () -> Unit,
    onAlertSent: () -> Unit,
    triggerType: String = "PANIC_BUTTON",
    triggerPhrase: String? = null,
    viewModel: GuardianViewModel = viewModel()
) {
    var ticks by remember { mutableStateOf(5) }
    var isSent by remember { mutableStateOf(false) }

    // Flashing Red Background
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val bgColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFB71C1C),
        targetValue = Color(0xFFD32F2F),
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgColor"
    )

    LaunchedEffect(key1 = true) {
        // Countdown
        while (ticks > 0) {
            delay(1000)
            ticks--
        }
        // Send actual alert
        if (!isSent) {
            isSent = true // Prevent double firing
            viewModel.sendPanicAlertNow(triggerType, triggerPhrase) {
                // Stay on screen but change state to SENT
                onAlertSent()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSent) Color.Black else bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isSent) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "SENDING ALERT IN",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$ticks",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "CANCEL ALERT",
                        color = Color.Red,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "ALERT SENT",
                    color = Color.Red,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Notifying emergency contacts...",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onCancel, // Go back home
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Return to Safety")
                }
            }
        }
    }
}
