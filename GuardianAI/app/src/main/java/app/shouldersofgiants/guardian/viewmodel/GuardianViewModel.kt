package app.shouldersofgiants.guardian.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import app.shouldersofgiants.guardian.service.SafetyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.location.Location
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.shouldersofgiants.guardian.data.UserRole

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _alertStatus = MutableStateFlow("Safe")
    val alertStatus: StateFlow<String> = _alertStatus.asStateFlow()

    private val _contacts = MutableStateFlow<List<app.shouldersofgiants.guardian.data.Contact>>(emptyList())
    val contacts: StateFlow<List<app.shouldersofgiants.guardian.data.Contact>> = _contacts.asStateFlow()

    val voiceLogs: StateFlow<List<String>> = app.shouldersofgiants.guardian.data.LogRepository.logs

    private val _userProfile = MutableStateFlow<app.shouldersofgiants.guardian.data.UserProfile?>(null)
    val userProfile: StateFlow<app.shouldersofgiants.guardian.data.UserProfile?> = _userProfile.asStateFlow()

    private val _family = MutableStateFlow<app.shouldersofgiants.guardian.data.Family?>(null)
    val family: StateFlow<app.shouldersofgiants.guardian.data.Family?> = _family.asStateFlow()

    init {
        fetchUserProfile()
    }

    fun fetchUserProfile() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            app.shouldersofgiants.guardian.data.GuardianRepository.getUserProfile(user.uid) { profile ->
                _userProfile.value = profile ?: app.shouldersofgiants.guardian.data.UserProfile(
                    id = user.uid,
                    email = user.email ?: "",
                    role = UserRole.UNDECIDED  // Explicitly set to UNDECIDED for new users
                )
                
                // If they have a family, load family details and contacts
                profile?.familyId?.let { fid ->
                    loadFamily(fid)
                    loadContacts()
                }
            }
        }
    }

    private val _activeAlerts = MutableStateFlow<List<app.shouldersofgiants.guardian.data.Alert>>(emptyList())
    val activeAlerts: StateFlow<List<app.shouldersofgiants.guardian.data.Alert>> = _activeAlerts.asStateFlow()

    private fun loadFamily(familyId: String) {
        app.shouldersofgiants.guardian.data.GuardianRepository.getFamily(familyId) { family ->
            _family.value = family
            // Start listening for alerts in this family
            app.shouldersofgiants.guardian.data.GuardianRepository.getActiveAlertsForFamily(familyId) { alerts ->
                _activeAlerts.value = alerts
            }
        }
    }

    private fun loadContacts() {
        app.shouldersofgiants.guardian.data.GuardianRepository.getContacts { list ->
            _contacts.value = list
        }
    }

    fun createFamily(name: String, onError: (String) -> Unit = {}) {
        val userId = _userProfile.value?.id ?: return
        
        var completed = false
        viewModelScope.launch {
            delay(15000) // 15 second timeout
            if (!completed) {
                onError("Operation timed out. Please check your internet connection and Firebase configuration.")
            }
        }

        app.shouldersofgiants.guardian.data.GuardianRepository.createFamily(name, userId) { fid ->
            completed = true
            if (fid != null) fetchUserProfile()
            else onError("Failed to create family")
        }
    }

    fun joinFamily(inviteCode: String, role: UserRole, onError: (String) -> Unit = {}) {
        val userId = _userProfile.value?.id ?: return
        
        var completed = false
        viewModelScope.launch {
            delay(15000) // 15 second timeout
            if (!completed) {
                onError("Operation timed out. Please check your internet connection.")
            }
        }

        app.shouldersofgiants.guardian.data.GuardianRepository.joinFamily(inviteCode, userId, role) { success ->
            completed = true
            if (success) fetchUserProfile()
            else onError("Family not found or join failed")
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

    fun sendVerificationCode(activity: android.app.Activity, phoneNumber: String, onResult: (String?, String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.sendVerificationCode(
            activity, 
            phoneNumber,
            onCodeSent = { vid -> onResult(vid, null) },
            onFailure = { error -> onResult(null, error) }
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

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.sendPasswordResetEmail(email, onResult)
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
        
        // Try to get last location before sending
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                val lat = loc?.latitude
                val lng = loc?.longitude
                app.shouldersofgiants.guardian.data.GuardianRepository.sendAlert("PANIC_BUTTON", lat, lng) { alertId ->
                    if (alertId != null) {
                        _alertStatus.value = "ALERT SENT!"
                    } else {
                        _alertStatus.value = "FAILED TO SEND"
                    }
                    onCompleted()
                }
            }
        } catch (e: SecurityException) {
            app.shouldersofgiants.guardian.data.GuardianRepository.sendAlert("PANIC_BUTTON") { alertId ->
                onCompleted()
            }
        }
    }

    fun updateTriggerPhrases(phrases: List<app.shouldersofgiants.guardian.data.TriggerPhrase>) {
        val familyId = _family.value?.id ?: return
        app.shouldersofgiants.guardian.data.GuardianRepository.updateTriggerPhrases(familyId, phrases) { success ->
            if (success) {
                loadFamily(familyId)
            }
        }
    }

    fun clearState() {
        _userProfile.value = null
        _family.value = null
        _activeAlerts.value = emptyList()
        _contacts.value = emptyList()
    }
}
