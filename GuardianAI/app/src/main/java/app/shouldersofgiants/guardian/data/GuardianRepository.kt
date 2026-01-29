package app.shouldersofgiants.guardian.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.util.Date

object GuardianRepository {
    
    private val db = Firebase.firestore
    
    fun sendAlert(type: String, callback: (Boolean) -> Unit) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "UNKNOWN"
        val alert = hashMapOf(
            "type" to type, // "PANIC_BUTTON" or "VOICE_TRIGGER"
            "timestamp" to Date(),
            "status" to "ACTIVE",
            "userId" to userId
        )

        db.collection("alerts")
            .add(alert)
            .addOnSuccessListener { documentReference ->
                Log.d("GuardianRepo", "Alert sent with ID: ${documentReference.id}")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.w("GuardianRepo", "Error adding alert", e)
                callback(false)
            }
    }

    fun sendVerificationCode(
        activity: android.app.Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onVerificationFailed: (Exception) -> Unit
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
                     Log.w("GuardianRepo", "onVerificationFailed", e)
                     onVerificationFailed(e)
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

    fun saveContact(contact: Contact, callback: (Boolean) -> Unit) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
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
}
