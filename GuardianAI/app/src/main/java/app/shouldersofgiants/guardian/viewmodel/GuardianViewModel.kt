package app.shouldersofgiants.guardian.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import app.shouldersofgiants.guardian.service.SafetyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _alertStatus = MutableStateFlow("Safe")
    val alertStatus: StateFlow<String> = _alertStatus.asStateFlow()

    private val _contacts = MutableStateFlow<List<app.shouldersofgiants.guardian.data.Contact>>(emptyList())
    val contacts: StateFlow<List<app.shouldersofgiants.guardian.data.Contact>> = _contacts.asStateFlow()

    val voiceLogs: StateFlow<List<String>> = app.shouldersofgiants.guardian.data.LogRepository.logs

    init {
        loadContacts()
    }

    private fun loadContacts() {
        app.shouldersofgiants.guardian.data.GuardianRepository.getContacts { list ->
            _contacts.value = list
        }
    }

    fun toggleListeningMode(enabled: Boolean) {
        _isListening.value = enabled
        
        // Direct log to debug UI
        val state = if (enabled) "ON" else "OFF"
        app.shouldersofgiants.guardian.data.LogRepository.addLog("Toggle switched: $state")

        val context = getApplication<Application>()
        val intent = Intent(context, SafetyService::class.java).apply {
            action = if (enabled) SafetyService.ACTION_START else SafetyService.ACTION_STOP
        }
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent) 
        }
    }

    fun addTestLog(msg: String) {
        app.shouldersofgiants.guardian.data.LogRepository.addLog(msg)
    }

    fun sendVerificationCode(activity: android.app.Activity, phoneNumber: String, onCodeSent: (String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.sendVerificationCode(
            activity, 
            phoneNumber,
            onCodeSent = { vid -> onCodeSent(vid) },
            onVerificationFailed = { onCodeSent(null) }
        )
    }

    fun verifyCode(verificationId: String, code: String, onResult: (Boolean) -> Unit) {
        val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, code)
        app.shouldersofgiants.guardian.data.GuardianRepository.signInWithPhoneAuthCredential(credential, onResult)
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.signInWithGoogle(idToken, onResult)
    }

    fun signInWithEmail(email: String, out_password: String, onResult: (Boolean, String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.signInWithEmail(email, out_password, onResult)
    }

    fun signUpWithEmail(email: String, out_password: String, onResult: (Boolean, String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.signUpWithEmail(email, out_password, onResult)
    }

    fun addContact(name: String, phoneNumber: String, email: String) {
        val contact = app.shouldersofgiants.guardian.data.Contact(name = name, phoneNumber = phoneNumber, email = email)
        app.shouldersofgiants.guardian.data.GuardianRepository.saveContact(contact) { success ->
            // loadContacts() is handled by snapshot listener
        }
    }

    fun removeContact(contactId: String) {
        app.shouldersofgiants.guardian.data.GuardianRepository.deleteContact(contactId) { success ->
            // loadContacts() is handled by snapshot listener
        }
    }

    fun triggerPanicAlert() {
       // This will now be handled by the UI to navigate to AlertScreen
    }

    fun sendPanicAlertNow(onCompleted: () -> Unit) {
        _alertStatus.value = "SENDING ALERT..."
        app.shouldersofgiants.guardian.data.GuardianRepository.sendAlert("PANIC_BUTTON") { success ->
            if (success) {
                _alertStatus.value = "ALERT SENT!"
            } else {
                _alertStatus.value = "FAILED TO SEND"
            }
            onCompleted()
        }
    }
}
