package app.shouldersofgiants.guardian.data

import java.util.UUID

data class SafeZone(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radiusMeters: Float = 100f
)

data class TriggerPhrase(
    val phrase: String = "",
    val severity: TriggerSeverity = TriggerSeverity.CRITICAL,
    val sensitivity: Float = 0.8f
)

enum class TriggerSeverity {
    NOTICE,   // "Oh sugar" - Just a notice
    CRITICAL  // "Help!" - Full emergency
}

data class Family(
    val id: String = "",
    val name: String = "",
    val managerId: String = "",
    val inviteCode: String = "",
    val safeZones: List<SafeZone> = emptyList(),
    val triggerPhrases: List<TriggerPhrase> = listOf(
        TriggerPhrase("Help", TriggerSeverity.CRITICAL),
        TriggerPhrase("Emergency", TriggerSeverity.CRITICAL)
    )
)
