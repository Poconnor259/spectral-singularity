package app.shouldersofgiants.guardian.data

import android.util.Log
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.SetOptions
import java.util.Date

object GuardianRepository {
    
    private val db = Firebase.firestore
    
    fun sendAlert(type: String, lat: Double? = null, lng: Double? = null, triggerPhrase: String? = null, callback: (String?) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: "UNKNOWN"
        
        getUserProfile(userId) { profile ->
            val alert = hashMapOf(
                "type" to type,
                "timestamp" to Date(),
                "status" to "ACTIVE",
                "userId" to userId,
                "familyId" to profile?.familyId,
                "lat" to lat,
                "lng" to lng,
                "triggerPhrase" to triggerPhrase
            )

            db.collection("alerts")
                .add(alert)
                .addOnSuccessListener { ref ->
                    Log.d("GuardianRepo", "Alert sent with ID: ${ref.id}")
                    callback(ref.id)
                }
                .addOnFailureListener { e ->
                    Log.w("GuardianRepo", "Error adding alert", e)
                    callback(null)
                }
        }
    }

    fun updateAlertLocation(alertId: String, lat: Double, lng: Double) {
        db.collection("alerts").document(alertId)
            .update("lat", lat, "lng", lng)
    }

    fun getActiveAlertsForFamily(familyId: String, callback: (List<Alert>) -> Unit) {
        db.collection("alerts")
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, _ ->
                val alerts = snapshot?.documents?.map { doc ->
                    Alert(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        familyId = doc.getString("familyId"),
                        type = doc.getString("type") ?: "PANIC_BUTTON",
                        timestamp = doc.getDate("timestamp") ?: Date(),
                        status = doc.getString("status") ?: "ACTIVE",
                        lat = doc.getDouble("lat"),
                        lng = doc.getDouble("lng"),
                        triggerPhrase = doc.getString("triggerPhrase")
                    )
                } ?: emptyList()
                callback(alerts)
            }
    }

    fun sendVerificationCode(
        activity: android.app.Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(com.google.firebase.auth.FirebaseAuth.getInstance())
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential) { success ->
                         Log.d("GuardianRepo", "Auto-verification completed")
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                     Log.w("GuardianRepo", "onVerificationFailed: ${e.message}", e)
                     onFailure(e.message ?: "Verification failed")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d("GuardianRepo", "onCodeSent:$verificationId")
                    onCodeSent(verificationId)
                }
            })
            .build()
        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun signInWithPhoneAuthCredential(credential: com.google.firebase.auth.PhoneAuthCredential, onResult: (Boolean) -> Unit) {
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true)
                } else {
                    Log.w("GuardianRepo", "signInWithCredential:failure", task.exception)
                    onResult(false)
                }
            }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true)
                } else {
                    Log.w("GuardianRepo", "signInWithGoogle:failure", task.exception)
                    onResult(false)
                }
            }
    }

    fun signInWithEmail(email: String, out_password: String, onResult: (Boolean, String?) -> Unit) {
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(email, out_password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    Log.w("GuardianRepo", "signInWithEmail:failure", task.exception)
                    onResult(false, task.exception?.message ?: "Unknown error")
                }
            }
    }

    fun signUpWithEmail(email: String, out_password: String, onResult: (Boolean, String?) -> Unit) {
        com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, out_password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    Log.w("GuardianRepo", "signUpWithEmail:failure", task.exception)
                    onResult(false, task.exception?.message ?: "Unknown error")
                }
            }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    Log.w("GuardianRepo", "sendPasswordResetEmail:failure", task.exception)
                    onResult(false, task.exception?.message ?: "Failed to send reset email")
                }
            }
    }

    fun getUserProfile(userId: String?, callback: (UserProfile?) -> Unit) {
        if (userId == null) {
            callback(null)
            return
        }
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    var email = doc.getString("email") ?: ""
                    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    
                    // Auto-sync email from Auth if empty in Firestore
                    if (email.isBlank() && firebaseUser?.uid == userId) {
                        email = firebaseUser.email ?: ""
                        if (email.isNotBlank()) {
                            db.collection("users").document(userId).update("email", email)
                        }
                    }

                    val profile = UserProfile(
                        id = doc.id,
                        email = email,
                        displayName = doc.getString("displayName") ?: "",
                        role = UserRole.valueOf(doc.getString("role") ?: UserRole.UNDECIDED.name),
                        familyId = doc.getString("familyId"),
                        fcmToken = doc.getString("fcmToken")
                    )
                    callback(profile)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { callback(null) }
    }

    fun saveUserProfile(profile: UserProfile, callback: (Boolean) -> Unit) {
        val userMap = hashMapOf(
            "email" to profile.email,
            "displayName" to profile.displayName,
            "role" to profile.role.name,
            "familyId" to profile.familyId,
            "fcmToken" to profile.fcmToken
        )
        db.collection("users").document(profile.id).set(userMap)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun createFamily(name: String, managerId: String, callback: (String?) -> Unit) {
        val inviteCode = (100000..999999).random().toString() // Simple 6rd digit code
        val familyId = db.collection("families").document().id
        val family = Family(
            id = familyId,
            name = name,
            managerId = managerId,
            inviteCode = inviteCode
        )

        db.collection("families").document(familyId).set(family)
            .addOnSuccessListener {
                // Update manager's profile - using SET with merge because document might not exist yet
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val updates = hashMapOf(
                    "familyId" to familyId,
                    "role" to UserRole.MANAGER.name,
                    "email" to (user?.email ?: ""),
                    "displayName" to (user?.displayName ?: "")
                )
                db.collection("users").document(managerId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener { callback(familyId) }
                    .addOnFailureListener { e ->
                        Log.e("GuardianRepo", "Error updating user with familyId", e)
                        callback(null)
                    }
            }
            .addOnFailureListener { callback(null) }
    }

    fun joinFamily(inviteCode: String, userId: String, role: UserRole, callback: (Boolean) -> Unit) {
        db.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { snapshot ->
                val familyDoc = snapshot.documents.firstOrNull()
                if (familyDoc != null) {
                    val familyId = familyDoc.id
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val updates = hashMapOf(
                        "familyId" to familyId,
                        "role" to role.name,
                        "email" to (user?.email ?: ""),
                        "displayName" to (user?.displayName ?: "")
                    )
                    db.collection("users").document(userId)
                        .set(updates, SetOptions.merge())
                        .addOnSuccessListener { callback(true) }
                        .addOnFailureListener { callback(false) }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    fun getFamily(familyId: String, callback: (Family?) -> Unit) {
        db.collection("families").document(familyId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val phrasesRaw = doc.get("triggerPhrases") as? List<Map<String, Any>>
                    val phrases = phrasesRaw?.map {
                        TriggerPhrase(
                            phrase = it["phrase"] as? String ?: "",
                            severity = TriggerSeverity.valueOf(it["severity"] as? String ?: TriggerSeverity.CRITICAL.name)
                        )
                    } ?: listOf(
                        TriggerPhrase("Help", TriggerSeverity.CRITICAL),
                        TriggerPhrase("Emergency", TriggerSeverity.CRITICAL)
                    )

                    val family = Family(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        managerId = doc.getString("managerId") ?: "",
                        inviteCode = doc.getString("inviteCode") ?: "",
                        triggerPhrases = phrases
                    )
                    callback(family)
                } else callback(null)
            }
            .addOnFailureListener { e ->
                Log.e("GuardianRepo", "Error getting family", e)
                callback(null)
            }
    }

    fun updateTriggerPhrases(familyId: String, phrases: List<TriggerPhrase>, callback: (Boolean) -> Unit) {
        db.collection("families").document(familyId)
            .update("triggerPhrases", phrases)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.e("GuardianRepo", "Error updating trigger phrases", e)
                callback(false)
            }
    }

    fun saveContact(contact: Contact, callback: (Boolean) -> Unit) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        // We will later change this to save contacts to the family instead of user if needed
        val contactMap = hashMapOf(
            "name" to contact.name,
            "phoneNumber" to contact.phoneNumber,
            "email" to contact.email
        )

        db.collection("users").document(userId).collection("contacts")
            .add(contactMap)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getContacts(callback: (List<Contact>) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("GuardianRepo", "getContacts: User is NULL, cannot sync.")
            return
        }
        val userId = user.uid
        Log.d("GuardianRepo", "getContacts: Syncing for user $userId")
        
        db.collection("users").document(userId).collection("contacts")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    callback(emptyList())
                    return@addSnapshotListener
                }
                val contacts = value?.documents?.map { doc ->
                    Contact(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        phoneNumber = doc.getString("phoneNumber") ?: "",
                        email = doc.getString("email") ?: ""
                    )
                } ?: emptyList()
                callback(contacts)
            }
    }

    fun deleteContact(contactId: String, callback: (Boolean) -> Unit) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).collection("contacts").document(contactId)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    // Local Storage for Offline Trigger Phrases
    private const val PREFS_NAME = "guardian_prefs"
    private const val KEY_TRIGGERS = "trigger_phrases"

    fun saveLocalTriggerPhrases(context: Context, phrases: List<TriggerPhrase>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = phrases.joinToString(",") { "${it.phrase}|${it.severity.name}" }
        prefs.edit().putString(KEY_TRIGGERS, serialized).apply()
        Log.d("GuardianRepo", "Saved ${phrases.size} triggers locally")
    }

    fun getLocalTriggerPhrases(context: Context): List<TriggerPhrase> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_TRIGGERS, null) ?: return emptyList()
        if (serialized.isBlank()) return emptyList()
        
        return try {
            serialized.split(",").map {
                val parts = it.split("|")
                TriggerPhrase(
                    phrase = parts[0],
                    severity = TriggerSeverity.valueOf(parts[1])
                )
            }
        } catch (e: Exception) {
            Log.e("GuardianRepo", "Error parsing local triggers", e)
            emptyList()
        }
    }

    fun getFamilyMembers(familyId: String, callback: (List<UserProfile>) -> Unit) {
        db.collection("users")
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GuardianRepo", "Error fetching family members", error)
                    callback(emptyList())
                    return@addSnapshotListener
                }
                
                val members = snapshot?.documents?.map { doc ->
                    UserProfile(
                        id = doc.id,
                        email = doc.getString("email") ?: "",
                        displayName = doc.getString("displayName") ?: "",
                        role = UserRole.valueOf(doc.getString("role") ?: UserRole.UNDECIDED.name),
                        familyId = doc.getString("familyId"),
                        fcmToken = doc.getString("fcmToken")
                    )
                } ?: emptyList()
                callback(members)
            }
    }

    fun updateUserRole(userId: String, newRole: UserRole, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId)
            .update("role", newRole.name)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.e("GuardianRepo", "Error updating user role", e)
                callback(false)
            }
    }

    fun updateDisplayName(userId: String, name: String, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId)
            .update("displayName", name)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.e("GuardianRepo", "Error updating display name", e)
                callback(false)
            }
    }
}
