package com.example.moderation

import java.util.Locale

object ModerationFilter {

    private val urduDigitMap = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val numberWords: Set<String> = setOf(
        // English
        "zero", "oh", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        // Roman Urdu
        "sifar", "sifer", "ek", "aik", "ik", "do", "doo", "teen", "tin", "chaar", "char",
        "paanch", "panch", "che", "chay", "chhe", "saat", "sat", "aath", "ath", "nau", "no",
        // Urdu script
        "صفر", "ایک", "دو", "تین", "چار", "پانچ", "چھ", "سات", "آٹھ", "نو"
    )

    private val pakistaniPhoneRegex = Regex(
        """(?:\+?92|0092|0)[\s.\-()]*3[\d\s.\-()]{8,13}"""
    )

    // Matches any sequence of 7 or more digits even with spaces, dashes, or dots between them
    private val sevenOrMoreDigitsRegex = Regex(
        """(?<!\d)(?:\d[\s.\-]*){7,}"""
    )

    private val handleRegex = Regex("""(?:^|\s)@[A-Za-z0-9._]{2,}""")

    private val urlRegex = Regex(
        """(?:https?://|www\.)\S+|\b[a-z0-9\-]+\.(?:com|pk|org|net|io|me|co|info|xyz|ly)(?:/\S*)?\b""",
        RegexOption.IGNORE_CASE
    )

    private val socialKeywords: Set<String> = setOf(
        "whatsapp", "whatsap", "watsapp", "watsap", "wa.me",
        "instagram", "insta", "facebook", "fb", "snapchat",
        "telegram", "t.me", "email", "e-mail", "gmail",
        "yahoo", "hotmail", "outlook", "discord", "tiktok"
    )

    private val threatPhrases: List<String> = listOf(
        "main tujhe maar dunga",
        "main tujhe maar doonga",
        "tujhe maar dunga",
        "tujhe jaan se maar",
        "jaan se maar dunga",
        "tera ghar dekh lunga",
        "ghar aa ke marunga",
        "tujhe dekh lunga",
        "tujhe nahi chodunga",
        "tujhe nahi chhorunga",
        "acid phenk",
        "i will kill you",
        "going to kill you",
        "gonna kill you",
        "i know where you live",
        "i will hurt you",
        "i will find you",
        "beat you up",
        "تمہیں جان سے مار",
        "تمہارا گھر دیکھ لوں گا",
        "تجھے مار دوں گا"
    )

    private val flirtAndMeetingPhrases: List<String> = listOf(
        "meri girlfriend banogi",
        "mera boyfriend banoge",
        "girlfriend banogi",
        "boyfriend banoge",
        "kya tum mujhse milogi",
        "kya tum mujhse miloge",
        "mujhse milogi",
        "mujhse miloge",
        "kal milte hain",
        "aaj milte hain",
        "mulaqat karte hain",
        "mulaqat karni hai",
        "address do",
        "apna address bhejo",
        "location bhejo",
        "apni location do",
        "date pe chalen",
        "date par chalen",
        "coffee pe milte",
        "tasveer bhejo",
        "apni pic bhejo",
        "send me your pic",
        "send your photo",
        "let's meet up",
        "lets meet up",
        "meet me in person",
        "wanna date",
        "be my girlfriend",
        "be my boyfriend",
        "send nudes",
        "ملاقات کرتے ہیں",
        "کل ملتے ہیں",
        "اپنا ایڈریس دو",
        "لوکیشن بھیجو"
    )

    private val flaggedCuriosityPhrases: List<String> = listOf(
        "tumhara naam kya hai",
        "apna naam batao",
        "what is your real name",
        "kahan rehte ho",
        "kahan rehti ho",
        "kis shehar mein",
        "which city are you from",
        "where do you live",
        "tumhari umar kya hai",
        "how old are you"
    )

    fun check(text: String): ModerationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ModerationResult.Allowed

        val normalizedDigits = normalizeUrduDigits(trimmed)
        val lowered = normalizedDigits.lowercase(Locale.ROOT)

        // 1. Check phone numbers (Pakistani formats, 7+ spaced/dashed digits, or digit words)
        if (containsPhoneNumber(normalizedDigits, lowered)) {
            return ModerationResult.Blocked(
                reason = "Phone number detected",
                category = BlockCategory.PHONE_NUMBER
            )
        }

        // 2. Check URLs, @handles, and social/contact platforms
        if (containsSocialOrUrl(lowered)) {
            return ModerationResult.Blocked(
                reason = "Social media handle, email, or link detected",
                category = BlockCategory.SOCIAL_OR_URL
            )
        }

        // 3. Check threat phrases
        if (threatPhrases.any { phrase -> lowered.contains(phrase) }) {
            return ModerationResult.Blocked(
                reason = "Threatening language detected",
                category = BlockCategory.THREAT
            )
        }

        // 4. Check flirt and in-person meeting requests
        if (flirtAndMeetingPhrases.any { phrase -> lowered.contains(phrase) }) {
            return ModerationResult.Blocked(
                reason = "Flirting or personal meeting request detected",
                category = BlockCategory.FLIRT_OR_MEETING
            )
        }

        // 5. Check abusive / bad words across English, Roman Urdu, and Urdu script
        if (containsBadWord(lowered)) {
            return ModerationResult.Blocked(
                reason = "Abusive or disrespectful word detected",
                category = BlockCategory.ABUSIVE_LANGUAGE
            )
        }

        // 6. Flag borderline personal curiosity for report review without blocking chat
        if (flaggedCuriosityPhrases.any { phrase -> lowered.contains(phrase) }) {
            return ModerationResult.Flagged(
                reason = "Personal identity curiosity detected"
            )
        }

        return ModerationResult.Allowed
    }

    private fun normalizeUrduDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(urduDigitMap[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun containsPhoneNumber(normalizedDigits: String, lowered: String): Boolean {
        if (pakistaniPhoneRegex.containsMatchIn(normalizedDigits)) return true
        if (sevenOrMoreDigitsRegex.containsMatchIn(normalizedDigits)) return true

        // Check sequences of digits written as words (e.g., "zero teen zero do paanch chhe saat")
        val tokens = lowered
            .replace(Regex("""[,\-./|]+"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        var consecutiveDigitWords = 0
        for (token in tokens) {
            if (token in numberWords) {
                consecutiveDigitWords++
                if (consecutiveDigitWords >= 5) return true
            } else {
                consecutiveDigitWords = 0
            }
        }
        return false
    }

    private fun containsSocialOrUrl(lowered: String): Boolean {
        if (handleRegex.containsMatchIn(lowered)) return true
        if (urlRegex.containsMatchIn(lowered)) return true

        val words = lowered
            .replace(Regex("""[^\p{L}\p{N}._]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        for (word in words) {
            val cleaned = word.trim('.', '_')
            if (cleaned in socialKeywords) return true
        }
        return false
    }

    private fun containsBadWord(lowered: String): Boolean {
        // Check multi-word terms directly
        for (term in BadWords.allSingleWords) {
            if (' ' in term && lowered.contains(term)) {
                return true
            }
        }

        val tokens = lowered
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        for (token in tokens) {
            if (token in BadWords.allSingleWords) return true
            val collapsed = collapseRepeatedChars(token)
            if (collapsed in BadWords.allSingleWords) return true
        }
        return false
    }

    private fun collapseRepeatedChars(word: String): String {
        if (word.length <= 2) return word
        val sb = StringBuilder()
        var prev = '\u0000'
        for (ch in word) {
            if (ch != prev) {
                sb.append(ch)
                prev = ch
            }
        }
        return sb.toString()
    }
}
