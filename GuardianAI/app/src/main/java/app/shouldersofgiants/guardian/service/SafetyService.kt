package app.shouldersofgiants.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import app.shouldersofgiants.guardian.MainActivity
import app.shouldersofgiants.guardian.R
import app.shouldersofgiants.guardian.data.TriggerPhrase
import app.shouldersofgiants.guardian.data.TriggerSeverity
import com.google.android.gms.location.*
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

class SafetyService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private lateinit var speechIntent: Intent
    private var familyId: String? = null
    private var triggerPhrases = mutableListOf<TriggerPhrase>()
    private var listenerRegistration: ListenerRegistration? = null
    private var alertListenerRegistration: ListenerRegistration? = null
    private var lastLoggedText: String = ""
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var trackingMode: String = "ALERT_ONLY"
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var isCrisisMode: Boolean = false
    private var hasSentLowBatteryNotice: Boolean = false
    private var consecutiveSpeechErrors: Int = 0
    private var backoffDelayMs: Long = 1000L
    private var isStationary: Boolean = false
    private var activityPendingIntent: PendingIntent? = null

    private lateinit var geofencingClient: GeofencingClient
    private var geofencePendingIntent: PendingIntent? = null
    private var familyListenerRegistration: ListenerRegistration? = null
    private var currentSafeZones = listOf<app.shouldersofgiants.guardian.data.SafeZone>()

    companion object {
        const val CHANNEL_ID = "GuardianServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ACTIVITY_TRANSITION = "ACTION_ACTIVITY_TRANSITION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        updateLocationRequest()
    }

    private fun updateLocationRequest() {
        if (!this::locationCallback.isInitialized) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        lastLat = location.latitude
                        lastLng = location.longitude
                        
                        if (trackingMode == "ALWAYS" || isCrisisMode) {
                            val userId = FirebaseAuth.getInstance().currentUser?.uid
                            userId?.let { uid ->
                                app.shouldersofgiants.guardian.data.GuardianRepository.updateUserLocation(uid, location.latitude, location.longitude)
                            }
                        }
                        updateNotification("Listening & Tracking: ${location.latitude.format(3)}, ${location.longitude.format(3)}${if (isCrisisMode) " (CRISIS MODE)" else ""}")
                    }
                }
            }
        }

        val wasRequesting = isListening
        if (wasRequesting) stopLocationUpdates()

        val builder = if (isCrisisMode) {
            val batteryPct = getBatteryPercentage()
            if (batteryPct > 20f) {
                hasSentLowBatteryNotice = false
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMinUpdateIntervalMillis(1000)
            } else {
                if (!hasSentLowBatteryNotice) {
                    sendNotice("Low Battery (<20%) - Location tracking slowed down.")
                    hasSentLowBatteryNotice = true
                }
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(5000)
            }
        } else {
            hasSentLowBatteryNotice = false
            if (isStationary) {
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000) // 5 minutes
                    .setMinUpdateIntervalMillis(300000)
            } else {
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000)
                    .setMinUpdateIntervalMillis(30000)
                    .setMinUpdateDistanceMeters(50f)
            }
        }
        
        locationRequest = builder.build()
        if (wasRequesting) startLocationUpdates()
    }

    private fun getBatteryPercentage(): Float {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (scale == -1) 100f else level * 100 / scale.toFloat()
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
            ACTION_ACTIVITY_TRANSITION -> {
                if (ActivityTransitionResult.hasResult(intent)) {
                    val result = ActivityTransitionResult.extractResult(intent)
                    for (event in result?.transitionEvents ?: emptyList()) {
                        if (event.activityType == DetectedActivity.STILL) {
                            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                if (!isStationary) {
                                    isStationary = true
                                    broadcastLog("Activity: User is STILL. GPS polling throttled to 5 mins.")
                                    updateLocationRequest()
                                }
                            } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                                if (isStationary) {
                                    isStationary = false
                                    broadcastLog("Activity: User is MOVING. Resuming normal GPS polling.")
                                    updateLocationRequest()
                                }
                            }
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        broadcastLog("App UI swiped away - Service continuing in background")
    }

    private fun startForegroundService() {
        if (isListening) return
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian AI Active")
            .setContentText("Listening & Tracking Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
        
        startLocationUpdates()
        setupActivityRecognition()
        
        // Load initial triggers from local storage (Offline support)
        val localTriggers = app.shouldersofgiants.guardian.data.GuardianRepository.getLocalTriggerPhrases(this)
        if (localTriggers.isNotEmpty()) {
            triggerPhrases.clear()
            triggerPhrases.addAll(localTriggers)
            broadcastLog("Loaded ${triggerPhrases.size} trigger phrases from LOCAL storage")
        }

        // Load Family and Triggers from Firestore
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = Firebase.firestore
            listenerRegistration = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val oldFamilyId = this@SafetyService.familyId
                        familyId = snapshot.getString("familyId")
                        if (oldFamilyId != familyId && familyId != null) {
                            listenForFamilyAlerts(familyId!!)
                            listenForFamilyDetails(familyId!!)
                        }
                        
                        trackingMode = snapshot.getString("locationTrackingMode") ?: "ALERT_ONLY"
                        val triggerData = snapshot.get("triggerPhrases") as? List<Map<String, Any>>
                        
                        val newPhrases = mutableListOf<TriggerPhrase>()
                        if (triggerData != null && triggerData.isNotEmpty()) {
                            for (map in triggerData) {
                                newPhrases.add(TriggerPhrase(
                                    phrase = map["phrase"] as? String ?: "",
                                    severity = app.shouldersofgiants.guardian.data.TriggerSeverity.valueOf(
                                        map["severity"] as? String ?: "CRITICAL"
                                    ),
                                    sensitivity = (map["sensitivity"] as? Double)?.toFloat() ?: 0.8f
                                ))
                            }
                        } else {
                            // Default fallbacks if no triggers set for user
                            newPhrases.add(TriggerPhrase("help", app.shouldersofgiants.guardian.data.TriggerSeverity.CRITICAL))
                            newPhrases.add(TriggerPhrase("emergency", app.shouldersofgiants.guardian.data.TriggerSeverity.CRITICAL))
                        }
                        
                        triggerPhrases.clear()
                        triggerPhrases.addAll(newPhrases)
                        
                        // Save to local storage for next time
                        app.shouldersofgiants.guardian.data.GuardianRepository.saveLocalTriggerPhrases(this, triggerPhrases)
                        
                        broadcastLog("Loaded ${triggerPhrases.size} trigger phrases from USER profile (Synced)")
                    }
                }
        }
        
        initSpeechRecognizer()
        isListening = true
        broadcastLog("Service Started")
    }

    private fun listenForFamilyAlerts(famId: String) {
        alertListenerRegistration?.remove()
        alertListenerRegistration = Firebase.firestore.collection("alerts")
            .whereEqualTo("familyId", famId)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, _ ->
                val hasAlerts = snapshot?.documents?.isNotEmpty() == true
                if (isCrisisMode != hasAlerts) {
                    isCrisisMode = hasAlerts
                    broadcastLog("Crisis Mode changed to: $isCrisisMode")
                    updateLocationRequest()
                }
            }
    }

    private fun listenForFamilyDetails(famId: String) {
        familyListenerRegistration?.remove()
        familyListenerRegistration = Firebase.firestore.collection("families").document(famId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val zonesRaw = snapshot.get("safeZones") as? List<Map<String, Any>>
                    val newZones = zonesRaw?.map {
                        app.shouldersofgiants.guardian.data.SafeZone(
                            id = it["id"] as? String ?: "",
                            name = it["name"] as? String ?: "",
                            lat = (it["lat"] as? Double) ?: 0.0,
                            lng = (it["lng"] as? Double) ?: 0.0,
                            radiusMeters = (it["radiusMeters"] as? Double)?.toFloat() ?: 100f
                        )
                    } ?: emptyList()
                    updateGeofences(newZones)
                }
            }
    }

    private fun updateGeofences(zones: List<app.shouldersofgiants.guardian.data.SafeZone>) {
        if (zones == currentSafeZones) return
        currentSafeZones = zones
        
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        geofencePendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            geofencingClient.removeGeofences(geofencePendingIntent!!).addOnCompleteListener {
                if (zones.isEmpty()) return@addOnCompleteListener
                
                val geofenceList = zones.map { zone ->
                    Geofence.Builder()
                        .setRequestId(zone.name)
                        .setCircularRegion(zone.lat, zone.lng, zone.radiusMeters)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build()
                }
                
                val req = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(geofenceList)
                    .build()
                    
                try {
                    geofencingClient.addGeofences(req, geofencePendingIntent!!)
                        .addOnSuccessListener { broadcastLog("Registered ${zones.size} Geofences successfully.") }
                        .addOnFailureListener { e -> Log.e("SafetyService", "Failed to add geofences", e) }
                } catch (e: SecurityException) {
                    Log.e("SafetyService", "Missing location permissions for Geofence", e)
                }
            }
        } catch (e: SecurityException) { }
    }

    private fun stopForegroundService() {
        isListening = false
        stopLocationUpdates()
        
        activityPendingIntent?.let {
            try {
                ActivityRecognition.getClient(this).removeActivityTransitionUpdates(it)
            } catch (e: SecurityException) {
                Log.e("SafetyService", "Failed to remove activity updates", e)
            }
        }
        
        listenerRegistration?.remove()
        alertListenerRegistration?.remove()
        familyListenerRegistration?.remove()
        geofencePendingIntent?.let {
            try { geofencingClient.removeGeofences(it) } catch (e: SecurityException) {}
        }
        speechRecognizer?.let {
            it.stopListening()
            it.cancel()
            it.destroy()
        }
        speechRecognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastLog("Service Stopped")
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("SafetyService", "Location permission missing", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun setupActivityRecognition() {
        val transitions = mutableListOf<ActivityTransition>()
        transitions.add(ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build())
        transitions.add(ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build())
            
        val request = ActivityTransitionRequest(transitions)
        
        val intent = Intent(this, SafetyService::class.java).apply {
            action = ACTION_ACTIVITY_TRANSITION
        }
        // Use FLAG_UPDATE_CURRENT and FLAG_MUTABLE as required by newer Android versions for pending intents that get data.
        activityPendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        
        try {
            ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(request, activityPendingIntent!!)
                .addOnSuccessListener {
                    Log.d("SafetyService", "Activity transitions registered success")
                }
                .addOnFailureListener {
                    Log.e("SafetyService", "Activity transitions registered fail", it)
                }
        } catch (e: SecurityException) {
            Log.e("SafetyService", "Activity permission missing", e)
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        
        try {
            speechRecognizer?.destroy()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                }
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            
            speechRecognizer?.setRecognitionListener(this)
            
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            android.os.Handler(mainLooper).postDelayed({
                startListening()
            }, 500)
            
        } catch (e: Exception) {
            Log.e("SafetyService", "initSpeechRecognizer error", e)
        }
    }

    private fun startListening() {
        if (!isListening || speechRecognizer == null) return
        try {
            android.os.Handler(mainLooper).post {
                speechRecognizer?.startListening(speechIntent)
            }
        } catch (e: Exception) {
            Log.e("SafetyService", "startListening error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { 
        // We will restart in onResults or onError to avoid racing with the current session
    }

    override fun onError(error: Int) {
        if (isListening) {
             consecutiveSpeechErrors++
             backoffDelayMs = (backoffDelayMs * 1.5).toLong().coerceAtMost(30000L) // Max 30 seconds
             android.os.Handler(mainLooper).postDelayed({
                 startListening()
             }, backoffDelayMs)
        }
    }

    override fun onResults(results: Bundle?) {
        consecutiveSpeechErrors = 0
        backoffDelayMs = 1000L
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches, isPartial = false)
        if (isListening) startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches, isPartial = true)
    }

    private fun processMatches(matches: ArrayList<String>?, isPartial: Boolean) {
        matches?.let {
            if (it.isNotEmpty()) {
                val bestMatch = it[0]
                
                // Only log if it's longer than 2 chars AND different from the last logged partial
                // If it's a final result, we always log it but reset the partial tracker
                if (bestMatch.length > 2 && bestMatch != lastLoggedText) {
                    val prefix = if (isPartial) "(Partial) " else ""
                    broadcastLog("$prefix\"$bestMatch\"")
                    lastLoggedText = bestMatch
                }
                
                if (!isPartial) {
                    lastLoggedText = "" // Reset for next sentence
                }
                
                for (match in it) {
                    val lowerMatch = match.lowercase()
                    for (trigger in triggerPhrases) {
                        if (isFuzzyMatch(lowerMatch, trigger.phrase.lowercase(), trigger.sensitivity)) {
                            broadcastLog("MATCH FOUND: \"$match\" triggered \"${trigger.phrase}\" (${trigger.severity}, req. sens: ${trigger.sensitivity})")
                            handleTrigger(trigger)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun isFuzzyMatch(transcript: String, trigger: String, sensitivity: Float): Boolean {
        if (transcript.contains(trigger)) return true
        if (sensitivity >= 1.0f) return false
        
        val triggerWords = trigger.split(" ")
        val transcriptWords = transcript.split(" ")
        if (transcriptWords.size < triggerWords.size) return false
        
        for (i in 0 .. transcriptWords.size - triggerWords.size) {
            val window = transcriptWords.subList(i, i + triggerWords.size).joinToString(" ")
            val maxLen = Math.max(window.length, trigger.length)
            if (maxLen == 0) continue
            val dist = levenshteinDistance(window, trigger)
            val sim = 1.0f - (dist.toFloat() / maxLen.toFloat())
            if (sim >= sensitivity) return true
        }
        return false
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val len0 = lhs.length + 1
        val len1 = rhs.length + 1
        var cost = IntArray(len0)
        var newcost = IntArray(len0)
        for (i in 0 until len0) cost[i] = i
        for (j in 1 until len1) {
            newcost[0] = j
            for (i in 1 until len0) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = cost[i - 1] + match
                val costInsert = cost[i] + 1
                val costDelete = newcost[i - 1] + 1
                newcost[i] = Math.min(Math.min(costInsert, costDelete), costReplace)
            }
            val swap = cost; cost = newcost; newcost = swap
        }
        return cost[len0 - 1]
    }

    private fun handleTrigger(trigger: TriggerPhrase) {
        if (trigger.severity == TriggerSeverity.CRITICAL) {
            triggerAlert(trigger.phrase, "VOICE_TRIGGER")
        } else {
            sendNotice(trigger.phrase)
        }
    }

    private fun sendNotice(phrase: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "UNKNOWN"
        val notice = hashMapOf(
            "type" to "NOTICE",
            "trigger" to phrase,
            "status" to "ACTIVE",
            "timestamp" to java.util.Date(),
            "userId" to userId,
            "familyId" to familyId,
            "lat" to lastLat,
            "lng" to lastLng
        )
        Firebase.firestore.collection("alerts").add(notice)
            .addOnSuccessListener { 
                broadcastLog("Notice sent to family: $phrase")
                updateNotification("Notice sent: $phrase")
            }
        
        // Force location update on alert even if in ALERT_ONLY mode
        if (lastLat != null && lastLng != null) {
            app.shouldersofgiants.guardian.data.GuardianRepository.updateUserLocation(userId, lastLat!!, lastLng!!)
        }
    }

    private fun broadcastLog(message: String) {
        app.shouldersofgiants.guardian.data.LogRepository.addLog(message)
    }
    
    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian AI Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            ))
            .setOnlyAlertOnce(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun triggerAlert(phrase: String? = null, type: String = "PANIC_BUTTON") {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "ACTION_TRIGGER_PANIC"
            putExtra("EXTRA_TRIGGER_TYPE", type)
            putExtra("EXTRA_TRIGGER_PHRASE", phrase)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Guardian AI Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
