package com.example

import com.example.moderation.CrisisDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrisisDetectorTest {

    @Test
    fun `detects Roman Urdu self-harm phrases`() {
        assertTrue(CrisisDetector.containsCrisisSignal("Ab jeena nahi chahta hoon"))
        assertTrue(CrisisDetector.containsCrisisSignal("Kabhi kabhi khudkushi ka khayal aata hai"))
        assertTrue(CrisisDetector.containsCrisisSignal("Apne aap ko nuksan pohanchane ka dil karta hai"))
    }

    @Test
    fun `detects English self-harm phrases`() {
        assertTrue(CrisisDetector.containsCrisisSignal("Sometimes I want to kill myself"))
        assertTrue(CrisisDetector.containsCrisisSignal("Thinking about suicide tonight"))
    }

    @Test
    fun `detects Urdu script self-harm phrases`() {
        assertTrue(CrisisDetector.containsCrisisSignal("مجھے لگتا ہے اب جینا نہیں ہے"))
        assertTrue(CrisisDetector.containsCrisisSignal("خودکشی کے خیالات آ رہے ہیں"))
    }

    @Test
    fun `does not trigger on normal sadness or stress`() {
        assertFalse(CrisisDetector.containsCrisisSignal("Aaj ka din bohat thaka dene wala tha"))
        assertFalse(CrisisDetector.containsCrisisSignal("I am stressed about my exams"))
        assertFalse(CrisisDetector.containsCrisisSignal("آج دل بہت بھاری ہے"))
    }
}
