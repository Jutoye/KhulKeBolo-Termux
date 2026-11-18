package com.example.moderation

sealed interface ModerationResult {
    data object Allowed : ModerationResult

    data class Blocked(
        val reason: String,
        val category: BlockCategory
    ) : ModerationResult

    data class Flagged(
        val reason: String
    ) : ModerationResult
}

enum class BlockCategory {
    PHONE_NUMBER,
    SOCIAL_OR_URL,
    ABUSIVE_LANGUAGE,
    THREAT,
    FLIRT_OR_MEETING
}
