package com.example.toctoe

import java.text.Normalizer

fun normalizeNickname(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)

fun isValidNickname(value: String): Boolean {
    val nickname = normalizeNickname(value)
    val length = nickname.codePointCount(0, nickname.length)
    return length in 2..8
}

fun nicknameCodePointCount(value: String): Int = value.codePointCount(0, value.length)
