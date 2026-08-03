package com.example.toctoe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerValidationTest {
    @Test
    fun nickname_acceptsChineseAndUnicodeByCodePoint() {
        assertTrue(isValidNickname("熊浩"))
        assertTrue(isValidNickname("玩家😀"))
        assertEquals(3, nicknameCodePointCount("玩家😀"))
    }

    @Test
    fun nickname_rejectsBlankTooShortAndTooLong() {
        assertFalse(isValidNickname("   "))
        assertFalse(isValidNickname("熊"))
        assertFalse(isValidNickname("123456789"))
    }

    @Test
    fun nickname_isTrimmedAndNormalized() {
        assertEquals("熊浩", normalizeNickname("  熊浩  "))
    }
}
