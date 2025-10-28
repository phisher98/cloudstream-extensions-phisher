package com.OneTouchTV

import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

private val keyHex = base64Decode("Njk2ZDM3MzI2MzY4NjE3MjUwNjE3MzczNzc2ZjcyNjQ2ZjY2NjQ0OTZlNjk3NDU2NjU2Mzc0NmY3MjUzNzQ2ZA==")
private val ivHex  = base64Decode("Njk2ZDM3MzI2MzY4NjE3MjUwNjE3MzczNzc2ZjcyNjQ=")
private val key = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private val iv  = ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun normalizeCustomAlphabet(s: String): String {
    return s.replace("-_.", "/")
        .replace("@", "+")
        .replace("\\s+".toRegex(), "")
}

fun base64ToBytes(b64: String): ByteArray {
    var base64Str = b64
    val pad = base64Str.length % 4
    if (pad != 0) base64Str += "=".repeat(4 - pad)
    return base64DecodeArray(base64Str)
}

fun decryptAes256Cbc(cipherBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    if (cipherBytes.size % 16 != 0) {
        throw IllegalArgumentException("Ciphertext length (${cipherBytes.size}) not multiple of 16.")
    }
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKey = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    return cipher.doFinal(cipherBytes)
}

fun decryptString(input: String): String {
    val normalized = normalizeCustomAlphabet(input)
    val cipherBytes = base64ToBytes(normalized)
    val plaintextBytes = decryptAes256Cbc(cipherBytes, key, iv)
    val text = String(plaintextBytes, Charsets.UTF_8)
    return JSONObject(text).getString("result")
}