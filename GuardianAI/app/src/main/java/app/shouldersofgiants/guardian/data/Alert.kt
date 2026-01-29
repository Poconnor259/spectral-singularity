package app.shouldersofgiants.guardian.data

import java.util.Date

data class Alert(
    val id: String = "",
    val userId: String = "",
    val familyId: String? = null,
    val type: String = "PANIC_BUTTON", // "PANIC_BUTTON", "VOICE_TRIGGER", "NOTICE"
    val timestamp: Date = Date(),
    val status: String = "ACTIVE", // "ACTIVE", "RESOLVED"
    val lat: Double? = null,
    val lng: Double? = null,
    val triggerPhrase: String? = null
)
