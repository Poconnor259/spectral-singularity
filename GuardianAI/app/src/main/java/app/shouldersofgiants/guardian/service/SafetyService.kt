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
import java.util.Locale

class SafetyService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private lateinit var speechIntent: Intent

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
            .setContentText("Listening for 'Help'...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        initSpeechRecognizer()
        isListening = true
        broadcastLog("Service Started")
    }

    private fun stopForegroundService() {
        isListening = false
        speechRecognizer?.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastLog("Service Stopped")
    }

    private fun initSpeechRecognizer() {
        broadcastLog("Checking speech recognition availability...")
        
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            broadcastLog("ERROR: Speech Recognition NOT available on device")
            return
        }
        
        broadcastLog("Speech Recognition Available: Initializing...")
        
        try {
            // Try on-device recognizer first (Android 13+, more reliable in foreground services)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    broadcastLog("Using ON-DEVICE recognizer (Android 13+)")
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    broadcastLog("On-device not available, using cloud recognizer")
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                }
            } else {
                broadcastLog("Using cloud recognizer (Android < 13)")
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            
            if (speechRecognizer == null) {
                broadcastLog("ERROR: SpeechRecognizer is NULL after creation!")
                return
            }
            
            speechRecognizer?.setRecognitionListener(this)
            broadcastLog("RecognitionListener attached")
            
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            broadcastLog("SpeechIntent configured")
            
            // Small delay to let the recognizer settle
            android.os.Handler(mainLooper).postDelayed({
                startListening()
            }, 500)
            
        } catch (e: Exception) {
            broadcastLog("Init EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("SafetyService", "initSpeechRecognizer error", e)
        }
    }

    private fun startListening() {
        if (!isListening) {
            broadcastLog("startListening() skipped: isListening=false")
            return
        }
        
        if (speechRecognizer == null) {
            broadcastLog("startListening() ERROR: speechRecognizer is NULL!")
            return
        }
        
        try {
            android.os.Handler(mainLooper).post {
                try {
                    broadcastLog("Calling speechRecognizer.startListening()...")
                    speechRecognizer?.startListening(speechIntent)
                    broadcastLog("startListening() called successfully - awaiting callback")
                } catch (e: Exception) {
                    broadcastLog("startListening INNER ERROR: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SafetyService", "startListening error", e)
            broadcastLog("startListening OUTER ERROR: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        broadcastLog("Status: Ready for speech (Mic Open)")
        Log.d("SafetyService", "onReadyForSpeech")
    }
    
    override fun onBeginningOfSpeech() {
        broadcastLog("Status: Speech detected...")
        Log.d("SafetyService", "onBeginningOfSpeech")
    }
    
    override fun onRmsChanged(rmsdB: Float) {
        // Too noisy to broadcast, but confirms audio input
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    
    override fun onEndOfSpeech() {
        if (isListening) {
             startListening()
        }
    }

    override fun onError(error: Int) {
        val errorMessage = when(error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No Perms"
            SpeechRecognizer.ERROR_CLIENT -> "Client Error"
            SpeechRecognizer.ERROR_NETWORK -> "Network Error"
            else -> "Error $error"
        }
        
        Log.d("SafetyService", "Speech Error: $errorMessage")
        broadcastLog("Error: $errorMessage")
        
        // Only update notification for critical errors to avoid flicker
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
             updateNotification("Retry: $errorMessage")
        }

        // Restart on error (with backoff if needed, but simple restart for now)
        if (isListening) {
             android.os.Handler(mainLooper).postDelayed({
                 startListening()
             }, 1000) // 1s delay to prevent tight loop on persistent errors
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches)
        if (isListening) {
            startListening()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        processMatches(matches)
    }

    private fun processMatches(matches: ArrayList<String>?) {
        matches?.let {
            if (it.isNotEmpty()) {
                val phrase = it[0]
                // Only broadcast if it's substantial
                if (phrase.length > 2) {
                    updateNotification("Heard: \"$phrase\"")
                    broadcastLog("Heard: \"$phrase\"")
                }
                
                for (match in it) {
                    if (match.contains("help", ignoreCase = true) || 
                        match.contains("emergency", ignoreCase = true)) {
                        broadcastLog("MATCH FOUND: \"$match\" -> TRIGGERING ALERT")
                        triggerAlert()
                        break
                    }
                }
            }
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
        Log.d("SafetyService", "ALERT TRIGGERED!")
        
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
