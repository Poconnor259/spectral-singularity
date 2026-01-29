package app.shouldersofgiants.guardian.data

data class UserProfile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.UNDECIDED,
    val familyId: String? = null,
    val fcmToken: String? = null
)
