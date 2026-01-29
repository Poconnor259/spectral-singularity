package app.shouldersofgiants.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

    companion object {
        const val CHANNEL_ID = "GuardianServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        if (isListening) return
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian AI Active")
            .setContentText("Listening for safety triggers...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        
        // Load Family and Triggers
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = Firebase.firestore
            db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                familyId = userDoc.getString("familyId")
                familyId?.let { fid ->
                    listenerRegistration = db.collection("families").document(fid)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                val triggerData = snapshot.get("triggerPhrases") as? List<Map<String, Any>>
                                triggerPhrases.clear()
                                if (triggerData != null) {
                                    for (map in triggerData) {
                                        triggerPhrases.add(TriggerPhrase(
                                            phrase = map["phrase"] as? String ?: "",
                                            severity = TriggerSeverity.valueOf(
                                                map["severity"] as? String ?: "CRITICAL"
                                            )
                                        ))
                                    }
                                } else {
                                    // Default fallbacks
                                    triggerPhrases.add(TriggerPhrase("help", TriggerSeverity.CRITICAL))
                                    triggerPhrases.add(TriggerPhrase("emergency", TriggerSeverity.CRITICAL))
                                }
                                broadcastLog("Loaded ${triggerPhrases.size} trigger phrases")
                            }
                        }
                }
            }
        }
        
        initSpeechRecognizer()
        isListening = true
        broadcastLog("Service Started")
    }

    private fun stopForegroundService() {
        isListening = false
        listenerRegistration?.remove()
        speechRecognizer?.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastLog("Service Stopped")
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        
        try {
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
    override fun onEndOfSpeech() { if (isListening) startListening() }

    override fun onError(error: Int) {
        if (isListening) {
             android.os.Handler(mainLooper).postDelayed({
                 startListening()
             }, 1000)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches)
        if (isListening) startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches)
    }

    private fun processMatches(matches: ArrayList<String>?) {
        matches?.let {
            if (it.isNotEmpty()) {
                val bestMatch = it[0]
                if (bestMatch.length > 2) {
                    broadcastLog("Heard: \"$bestMatch\"")
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
            "familyId" to familyId
        )
        Firebase.firestore.collection("alerts").add(notice)
            .addOnSuccessListener { 
                broadcastLog("Notice sent to family: $phrase")
                updateNotification("Notice sent: $phrase")
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
