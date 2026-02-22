package app.shouldersofgiants.guardian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.auth.FirebaseAuth
import app.shouldersofgiants.guardian.data.GuardianRepository
import app.shouldersofgiants.guardian.data.LogRepository

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            val errCode = geofencingEvent?.errorCode ?: -1
            Log.e("GeofenceReceiver", "GeofencingEvent error: ${GeofenceStatusCodes.getStatusCodeString(errCode)}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            if (triggeringGeofences.isNullOrEmpty()) return
            
            val zoneName = triggeringGeofences[0].requestId
            val action = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) "Arrived at" else "Left"
            val statusMessage = "$action $zoneName"
            
            Log.i("GeofenceReceiver", statusMessage)
            LogRepository.addLog("Geofence Trigger: $statusMessage")
            
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                // We update the user's locationTrackingMode or create a specific field for this
                // For simplicity, we just send a "NOTICE" severity alert to notify managers silently 
                // Alternatively, we can just update a "status" field on the user profile
                GuardianRepository.updateMemberSetting(userId, "locationTrackingMode", statusMessage)
            }
        }
    }
}
