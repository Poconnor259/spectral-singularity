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

    private val _familyMembers = MutableStateFlow<List<app.shouldersofgiants.guardian.data.UserProfile>>(emptyList())
    val familyMembers: StateFlow<List<app.shouldersofgiants.guardian.data.UserProfile>> = _familyMembers.asStateFlow()

    private val _family = MutableStateFlow<app.shouldersofgiants.guardian.data.Family?>(null)
    val family: StateFlow<app.shouldersofgiants.guardian.data.Family?> = _family.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(false)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    init {
        fetchUserProfile()
        checkBatteryOptimization()
        syncListeningToService()
    }

    private fun syncListeningToService() {
        val context = getApplication<Application>()
        val wasEnabledLocally = app.shouldersofgiants.guardian.data.GuardianRepository.getLocalListeningEnabled(context)
        if (wasEnabledLocally) {
            _isListening.value = true
            // Start the service immediately on startup if it was left ON
            val intent = Intent(context, SafetyService::class.java).apply {
                action = SafetyService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun checkBatteryOptimization() {
        val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        _isBatteryOptimized.value = !pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun requestIgnoreBatteryOptimization() {
        val context = getApplication<Application>()
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
                
                // Sync listening state from profile if roles are matched
                profile?.let { p ->
                    if (p.listeningEnabled != _isListening.value) {
                        toggleListeningMode(p.listeningEnabled, updateFirestore = false)
                    }
                }

                // If they have a family, load family details and contacts
                profile?.familyId?.let { fid ->
                    loadFamily(fid)
                    loadContacts()
                }
                registerFcmToken()
            }
        }
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            _userProfile.value?.id?.let { uid ->
                app.shouldersofgiants.guardian.data.GuardianRepository.updateFcmToken(uid, token)
            }
        }
    }

    fun resolveAlerts() {
        val familyId = _family.value?.id ?: return
        app.shouldersofgiants.guardian.data.GuardianRepository.resolveAllAlertsForFamily(familyId) { success ->
            if (success) {
                // alerts list will auto-update via listener
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
            // Load members
            loadFamilyMembers(familyId)
        }
    }

    private fun loadFamilyMembers(familyId: String) {
        app.shouldersofgiants.guardian.data.GuardianRepository.getFamilyMembers(familyId) { members ->
            _familyMembers.value = members
        }
    }

    fun updateUserRole(userId: String, newRole: app.shouldersofgiants.guardian.data.UserRole, onResult: (Boolean) -> Unit = {}) {
        app.shouldersofgiants.guardian.data.GuardianRepository.updateUserRole(userId, newRole) { success ->
            onResult(success)
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

    fun toggleListeningMode(enabled: Boolean, updateFirestore: Boolean = true) {
        if (_isListening.value == enabled) return
        _isListening.value = enabled
        
        val context = getApplication<Application>()
        // Save locally for offline start next time
        app.shouldersofgiants.guardian.data.GuardianRepository.setLocalListeningEnabled(context, enabled)
        
        // Update Firestore if requested (and if we're not a PROTECTED user being remotely toggled)
        if (updateFirestore && _userProfile.value?.role != UserRole.PROTECTED) {
            _userProfile.value?.id?.let { uid ->
                app.shouldersofgiants.guardian.data.GuardianRepository.updateMemberSetting(uid, "listeningEnabled", enabled)
            }
        }

        // Direct log to debug UI
        val state = if (enabled) "ON" else "OFF"
        app.shouldersofgiants.guardian.data.LogRepository.addLog("Toggle switched: $state")

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

    fun updateMemberSetting(userId: String, field: String, value: Any) {
        app.shouldersofgiants.guardian.data.GuardianRepository.updateMemberSetting(userId, field, value) { success ->
            if (success && userId == _userProfile.value?.id) {
                fetchUserProfile()
            }
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

    fun fetchFamilyMembers(familyId: String) {
        app.shouldersofgiants.guardian.data.GuardianRepository.getFamilyMembers(familyId) { members ->
            _familyMembers.value = members
        }
    }

    fun updateLocationTrackingMode(mode: String) {
        val userId = _userProfile.value?.id ?: return
        app.shouldersofgiants.guardian.data.GuardianRepository.updateLocationTrackingMode(userId, mode) { success ->
            if (success) fetchUserProfile()
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        app.shouldersofgiants.guardian.data.GuardianRepository.sendPasswordResetEmail(email, onResult)
    }

    fun updateUserProfile(name: String, onResult: (Boolean) -> Unit = {}) {
        val userId = _userProfile.value?.id ?: return
        app.shouldersofgiants.guardian.data.GuardianRepository.updateDisplayName(userId, name) { success ->
            if (success) fetchUserProfile()
            onResult(success)
        }
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
