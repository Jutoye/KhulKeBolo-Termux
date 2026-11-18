package com.example.config

/**
 * Helpline and emergency contact constants for Pakistan.
 *
 * IMPORTANT: Verify official active helpline numbers before releasing to production
 * and replace the placeholder numbers below.
 */
object HelplineConfig {
    // Primary mental health / crisis support helpline (placeholder — verify before release)
    const val HELPLINE_NAME = "Umang Pakistan Helpline (Verify Number)"
    const val HELPLINE_NUMBER = "0311-7786264"

    // National emergency services (placeholder — verify local emergency routing)
    const val EMERGENCY_NAME = "Rescue / Emergency"
    const val EMERGENCY_NUMBER = "1122"

    const val DEFAULT_SESSION_MINUTES = 20L
    const val EXTENSION_MINUTES = 10L
    const val MESSAGE_TTL_EXTRA_MINUTES = 60L
    const val MATCH_TIMEOUT_SECONDS = 60
    const val MAX_MESSAGE_LENGTH = 500
}
