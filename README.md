# Khul Ke Bolo (کھل کے بولو)

**Tagline:** *Aap bolein, hum sunte hain*

Khul Ke Bolo is an anonymous, text-based peer-listening Android app built with Kotlin, Jetpack Compose (Material 3), Navigation Compose, and Firebase. Two peers are paired randomly as a **Speaker** (*Baat Karna Hai*) and a **Listener** (*Sun Na Hai*) for calm, non-judgmental 20-minute conversations in Roman Urdu, Urdu script, or English.

---

## 1. Key Features Implemented
- **Splash & 18+ Age Gate**: Confirms 18+ status storing only a boolean `ageConfirmed` (no date of birth). Politely blocks and exits if under 18.
- **Community Rules Onboarding**: Displays all 7 Roman Urdu guidelines and records acceptance timestamp (`Main Samajh Gaya`).
- **Privacy-First Authentication**: Uses Jetpack Credential Manager Google Sign-In for backend abuse prevention & zero-trust Firestore rules while keeping peer chats 100% anonymous (`Dost #XXXX` nicknames, zero PII stored in Firestore).
- **Atomic Matchmaking (`queue` -> `rooms`)**: Uses Firestore transactions to pair a waiting Speaker and Listener, excluding blocked UIDs and enforcing a 24-hour suspension if a user has >= 3 reports in 24 hours. Includes a 60-second countdown and cancel/timeout handling.
- **Client-Side Moderation (`ModerationFilter.kt` & `BadWords.kt`)**: Blocks Pakistani/international phone numbers, 7+ spaced/Urdu digits, spoken digit words, social handles/URLs, abusive terms (English, Roman Urdu, Urdu script), threats, and flirting/meeting requests while keeping text in the input field so the user can edit it. Flags borderline identity curiosity for review.
- **Crisis Detection (`CrisisDetector.kt`)**: Detects self-harm/suicide phrases without auto-ending the chat, displays a calm helpline card (`HelplineConfig.kt`), and logs anonymous events (`roomId` + timestamp only).
- **20-Minute Session Timer & Mutual +10m Extension**: Live countdown from Firestore `endsAt`; both participants must agree for +10m extension.
- **Ephemeral Message Cleanup & Data Deletion**: Messages are deleted when the session ends (plus a 1-hour `deleteAt` TTL). Users can permanently delete their data via *"Mera data delete karein"* in Settings.

---

## 2. Helpline Verification Reminder
Before releasing to production, verify active official helpline numbers for Pakistan and update:
- `app/src/main/java/com/example/config/HelplineConfig.kt` (`HELPLINE_NAME`, `HELPLINE_NUMBER`, `EMERGENCY_NUMBER`)
- Privacy Policy copy in `app/src/main/res/values/strings.xml` (`privacy_notice_english` / `privacy_notice_roman_urdu`)

---

## 3. Build & Test Instructions

### Build & Run Unit Tests
```bash
# Run ModerationFilterTest (21 cases) & CrisisDetectorTest
gradle :app:testDebugUnitTest

# Compile & assemble Debug APK
gradle :app:assembleDebug
```

### Run Firestore Security Rules Tests Locally
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Testing Matching with Two Devices / Emulators
1. Sign in with **Google Account A** on Device/Emulator 1, complete 18+ Age Gate and Rules, and tap **Baat Karna Hai** (Speaker).
2. Sign in with **Google Account B** on Device/Emulator 2, complete 18+ Age Gate and Rules, and tap **Sun Na Hai** (Listener).
3. Both devices match atomically into a `rooms/{roomId}` document and see only random `Dost #XXXX` nicknames.
4. Test sending allowed messages in English, Roman Urdu, and Urdu script, blocked phone/social/profanity messages, crisis phrases, +10m mutual extension, reporting/blocking, and *"Khatam Karein"*.

---

## 4. Manual Test Checklist (Section 15)
- [ ] **Age Gate**: Selecting *"Nahi, main 18 se kam hoon"* shows polite block message and Exit button.
- [ ] **Rules Gate**: Home screen cannot be accessed until *"Main Samajh Gaya"* is tapped.
- [ ] **Two-Device Matching**: One Speaker + one Listener match within seconds into a shared chat room with `Dost #XXXX` nicknames; cancelling or waiting 60s without partner works cleanly.
- [ ] **Moderation Filter**: Phone numbers (`0300-1234567`, `+92...`, spaced digits, digit words), social handles (`whatsapp`, `@user`, `.pk`), bad words, threats, and flirt/meeting messages show the gentle warning and keep text editable in input.
- [ ] **Crisis Card**: Self-harm phrases trigger the calm helpline card above the input without closing the chat, and also show the helpline card on Session End.
- [ ] **Timer & Mutual Extension**: 20-minute timer counts down; requesting `+10m` applies only after partner accepts; hitting `00:00` ends session and moves to Session End Screen.
- [ ] **Report & Block**: Reporting saves to `reports` (with last 5 messages only when checkbox checked); blocking ends chat and prevents re-matching.
- [ ] **Message Cleanup**: Messages are removed from `rooms/{roomId}/messages` when the session ends.
- [ ] **Data Deletion**: Tapping *"Mera data delete karein"* in Settings deletes queue entry, user reports, user profile, local DataStore flags, and signs out/deletes auth account.
