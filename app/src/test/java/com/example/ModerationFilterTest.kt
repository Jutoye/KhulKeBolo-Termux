package com.example

import com.example.moderation.BlockCategory
import com.example.moderation.ModerationFilter
import com.example.moderation.ModerationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationFilterTest {

    @Test
    fun `01 allows empathetic Roman Urdu message`() {
        val result = ModerationFilter.check("Aaj dil bohat udaas hai, ghar walon se baat nahi ho paa rahi.")
        assertEquals(ModerationResult.Allowed, result)
    }

    @Test
    fun `02 allows empathetic English message`() {
        val result = ModerationFilter.check("I feel exhausted from work and just needed someone to listen.")
        assertEquals(ModerationResult.Allowed, result)
    }

    @Test
    fun `03 allows empathetic Urdu script message`() {
        val result = ModerationFilter.check("آج میرا دل بہت اداس ہے اور بس بات کرنا چاہتا ہوں۔")
        assertEquals(ModerationResult.Allowed, result)
    }

    @Test
    fun `04 allows listener acknowledgement phrases`() {
        val result = ModerationFilter.check("Main sun raha hoon. Yeh sun kar bohat mushkil laga hoga.")
        assertEquals(ModerationResult.Allowed, result)
    }

    @Test
    fun `05 blocks standard Pakistani phone number with dash`() {
        val result = ModerationFilter.check("Mera number 0300-1234567 hai call karo")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.PHONE_NUMBER, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `06 blocks international +92 phone number`() {
        val result = ModerationFilter.check("Message me at +923219876543")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.PHONE_NUMBER, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `07 blocks spaced out 7 or more digits`() {
        val result = ModerationFilter.check("Note karo 0 3 4 5 9 8 7 6")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.PHONE_NUMBER, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `08 blocks Urdu digit characters`() {
        val result = ModerationFilter.check("میرا نمبر ۰۳۰۰۱۲۳۴۵۶۷ ہے")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.PHONE_NUMBER, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `09 blocks numbers written as Urdu or English words`() {
        val result = ModerationFilter.check("Mera code zero teen char paanch che saat aath hai")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.PHONE_NUMBER, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `10 blocks WhatsApp and social platform mentions`() {
        val result = ModerationFilter.check("Yahan nahi WhatsApp par baat karte hain")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.SOCIAL_OR_URL, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `11 blocks @handle mentions`() {
        val result = ModerationFilter.check("Add me @ali_k99 on insta")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.SOCIAL_OR_URL, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `12 blocks URLs and dot pk domains`() {
        val result = ModerationFilter.check("Visit example.pk/chat for more")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.SOCIAL_OR_URL, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `13 blocks Roman Urdu abusive term`() {
        val result = ModerationFilter.check("Tum bohat bare harami ho")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.ABUSIVE_LANGUAGE, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `14 blocks English abusive term`() {
        val result = ModerationFilter.check("You are an idiot and an asshole")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.ABUSIVE_LANGUAGE, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `15 blocks Urdu script abusive term`() {
        val result = ModerationFilter.check("تم ایک کمینہ انسان ہو")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.ABUSIVE_LANGUAGE, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `16 blocks Roman Urdu threat phrase`() {
        val result = ModerationFilter.check("Chup raho warna tera ghar dekh lunga")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.THREAT, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `17 blocks English threat phrase`() {
        val result = ModerationFilter.check("I know where you live and I will kill you")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.THREAT, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `18 blocks flirt and meeting request in Roman Urdu`() {
        val result = ModerationFilter.check("Kya tum mujhse milogi kal milte hain?")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.FLIRT_OR_MEETING, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `19 blocks location and address requests`() {
        val result = ModerationFilter.check("Apni location bhejo ya address do")
        assertTrue(result is ModerationResult.Blocked)
        assertEquals(BlockCategory.FLIRT_OR_MEETING, (result as ModerationResult.Blocked).category)
    }

    @Test
    fun `20 flags personal identity curiosity without blocking`() {
        val result = ModerationFilter.check("Acha waise kahan rehte ho aap?")
        assertTrue(result is ModerationResult.Flagged)
    }

    @Test
    fun `21 does not block crisis expression so helpline can be shown`() {
        val result = ModerationFilter.check("Mujhe lagta hai ab jeena nahi hai, khudkushi ka khayal aa raha hai")
        assertEquals(ModerationResult.Allowed, result)
    }
}
