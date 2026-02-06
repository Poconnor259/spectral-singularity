package app.shouldersofgiants.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
    private var lastLoggedText: String = ""
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var trackingMode: String = "ALERT_ONLY"
    private var lastLat: Double? = null
    private var lastLng: Double? = null

    companion object {
        const val CHANNEL_ID = "GuardianServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()
    }

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10s
            .setMinUpdateIntervalMillis(5000) // 5s
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLat = location.latitude
                    lastLng = location.longitude
                    
                    if (trackingMode == "ALWAYS") {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        userId?.let { uid ->
                            app.shouldersofgiants.guardian.data.GuardianRepository.updateUserLocation(uid, location.latitude, location.longitude)
                        }
                    }
                    updateNotification("Listening & Tracking: ${location.latitude.format(3)}, ${location.longitude.format(3)}")
                }
            }
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
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
                        trackingMode = snapshot.getString("locationTrackingMode") ?: "ALERT_ONLY"
                        val triggerData = snapshot.get("triggerPhrases") as? List<Map<String, Any>>
                        
                        val newPhrases = mutableListOf<TriggerPhrase>()
                        if (triggerData != null && triggerData.isNotEmpty()) {
                            for (map in triggerData) {
                                newPhrases.add(TriggerPhrase(
                                    phrase = map["phrase"] as? String ?: "",
                                    severity = app.shouldersofgiants.guardian.data.TriggerSeverity.valueOf(
                                        map["severity"] as? String ?: "CRITICAL"
                                    )
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

    private fun stopForegroundService() {
        isListening = false
        stopLocationUpdates()
        listenerRegistration?.remove()
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
             android.os.Handler(mainLooper).postDelayed({
                 startListening()
             }, 1000)
        }
    }

    override fun onResults(results: Bundle?) {
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
                        if (lowerMatch.contains(trigger.phrase.lowercase())) {
                            broadcastLog("MATCH FOUND: \"$match\" triggered \"${trigger.phrase}\" (${trigger.severity})")
                            handleTrigger(trigger)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun handleTrigger(trigger: TriggerPhrase) {
        if (trigger.severity == TriggerSeverity.CRITICAL) {
            triggerAlert()
        } else {
            sendNotice(trigger.phrase)
        }
    }

    private fun sendNotice(phrase: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "UNKNOWN"
        val notice = hashMapOf(
            "type" to "NOTICE",
            "trigger" to phrase,
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

    private fun triggerAlert() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "ACTION_TRIGGER_PANIC"
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
