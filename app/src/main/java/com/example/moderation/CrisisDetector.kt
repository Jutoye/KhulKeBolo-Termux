package com.example.moderation

import java.util.Locale

object CrisisDetector {

    private val crisisPhrases: List<String> = listOf(
        // Roman Urdu
        "khudkushi",
        "khud kushi",
        "marna chahta hoon",
        "marna chahti hoon",
        "marna chahta hun",
        "marna chahti hun",
        "mar jana chahta",
        "mar jana chahti",
        "apne aap ko nuksan",
        "khud ko nuksan",
        "khud ko maar",
        "ab jeena nahi",
        "jeena nahi chahta",
        "jeena nahi chahti",
        "zindagi khatam",
        "jaan dena chahta",
        "jaan dena chahti",
        // English
        "suicide",
        "suicidal",
        "kill myself",
        "end my life",
        "ending my life",
        "want to die",
        "wanna die",
        "hurt myself",
        "harm myself",
        "self harm",
        "self-harm",
        "no reason to live",
        // Urdu script
        "خودکشی",
        "خود کشی",
        "مرنا چاہتا ہوں",
        "مرنا چاہتی ہوں",
        "اب جینا نہیں",
        "جینا نہیں چاہتا",
        "جینا نہیں چاہتی",
        "اپنے آپ کو نقصان",
        "خود کو نقصان"
    )

    fun containsCrisisSignal(text: String): Boolean {
        val lowered = text.trim().lowercase(Locale.ROOT)
        if (lowered.isEmpty()) return false
        return crisisPhrases.any { phrase -> lowered.contains(phrase) }
    }
}
