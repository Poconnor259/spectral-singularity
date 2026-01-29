package app.shouldersofgiants.guardian.data

data class TriggerPhrase(
    val phrase: String = "",
    val severity: TriggerSeverity = TriggerSeverity.CRITICAL
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
    val triggerPhrases: List<TriggerPhrase> = listOf(
        TriggerPhrase("Help", TriggerSeverity.CRITICAL),
        TriggerPhrase("Emergency", TriggerSeverity.CRITICAL)
    )
)
