package app.shouldersofgiants.guardian.data

data class UserProfile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.UNDECIDED,
    val familyId: String? = null,
    val fcmToken: String? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastLocationUpdate: Long? = null,
    val locationTrackingMode: String = "ALERT_ONLY",
    val listeningEnabled: Boolean = false
)
