package com.example.data.model

enum class UserRole(val romanUrduLabel: String, val englishLabel: String) {
    SPEAKER("Baat Karna Hai", "I want to speak"),
    LISTENER("Sun Na Hai", "I want to listen");

    fun opposite(): UserRole = when (this) {
        SPEAKER -> LISTENER
        LISTENER -> SPEAKER
    }

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: SPEAKER
    }
}

enum class RoomStatus {
    ACTIVE,
    ENDED;

    companion object {
        fun fromRaw(raw: String?): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class MoodChoice(val romanUrduLabel: String, val englishHint: String) {
    HALKA("Halka", "Lighter"),
    THODA_BEHTAR("Thoda Behtar", "A little better"),
    WAISA_HI("Waisa hi", "About the same"),
    BHARI("Bhari", "Still heavy");

    companion object {
        fun fromRaw(raw: String?): MoodChoice =
            entries.firstOrNull { it.name == raw } ?: WAISA_HI
    }
}
