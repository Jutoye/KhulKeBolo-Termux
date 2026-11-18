package com.example.moderation

/**
 * Curated list of abusive, profane, and slurring terms in English, Roman Urdu, and Urdu script.
 *
 * NOTE: Maintain and expand this list over time based on community report reviews
 * and evolving transliterations / slang. Keep comparisons case-insensitive.
 */
object BadWords {

    val englishTerms: Set<String> = setOf(
        "fuck", "fucking", "fucker", "motherfucker", "bitch", "bastard",
        "asshole", "dickhead", "slut", "whore", "cunt", "dumbass",
        "moron", "retard", "idiot", "scumbag", "shithead", "bullshit"
    )

    val romanUrduTerms: Set<String> = setOf(
        "bc", "mc", "bhenchod", "behenchod", "madarchod", "chutiya", "chutiye",
        "chutia", "harami", "haraami", "haramzada", "kamina", "kameena", "kamini",
        "kutta", "kutte", "kutti", "kutiya", "kanjar", "kanjari", "gandu", "gaandu",
        "randi", "randy", "bhosdike", "bhosdi", "ullu ka pattha", "saala", "sala",
        "saali", "lanat", "laanat", "dalay", "dalla", "chodu", "loda", "lodu",
        "badtameez", "ghatiya", "zaleel", "jahil", "pagal", "bewakoof"
    )

    val urduScriptTerms: Set<String> = setOf(
        "حرامی", "کمینہ", "کمینی", "کتا", "کتی", "کتیا",
        "کنجر", "چوتیا", "بہن چود", "مادر چود", "رنڈی",
        "بے وقوف", "ذلیل", "گھٹیا", "لعنت", "جاہل"
    )

    val allSingleWords: Set<String> = englishTerms + romanUrduTerms + urduScriptTerms
}
