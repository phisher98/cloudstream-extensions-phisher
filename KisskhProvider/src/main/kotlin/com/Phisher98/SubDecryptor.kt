package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY = "AmSmZVcH93UQUezi"
private const val KEY2 = "8056483646328763"
private const val KEY3 = "sWODXX04QRTkHdlZ"


private val IV = intArrayOf(1382367819, 1465333859, 1902406224, 1164854838)
private val IV2 = intArrayOf(909653298, 909193779, 925905208, 892483379)
private val IV3 = intArrayOf(946894696, 1634749029, 1127508082, 1396271183)



fun decrypt(encryptedB64: String): String {
    val keyIvPairs = listOf(
        Pair(KEY.toByteArray(Charsets.UTF_8), IV.toByteArray()),
        Pair(KEY2.toByteArray(Charsets.UTF_8), IV2.toByteArray()),
        Pair(KEY3.toByteArray(Charsets.UTF_8), IV3.toByteArray())
    )

    val encryptedBytes = base64DecodeArray(encryptedB64) // Decode Base64 input

    for ((keyBytes, ivBytes) in keyIvPairs) {
        try {
            return decryptWithKeyIv(keyBytes, ivBytes, encryptedBytes)
        } catch (ex: Exception) {
            println("Decryption attempt failed with key/IV pair. Error: ${ex.message}")
        }
    }
    return "Decryption failed: All keys/IVs failed"
}

private fun decryptWithKeyIv(keyBytes: ByteArray, ivBytes: ByteArray, encryptedBytes: ByteArray): String {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
    return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
}

private fun IntArray.toByteArray(): ByteArray {
    return ByteArray(size * 4).also { bytes ->
        forEachIndexed { index, value ->
            bytes[index * 4] = (value shr 24).toByte()
            bytes[index * 4 + 1] = (value shr 16).toByte()
            bytes[index * 4 + 2] = (value shr 8).toByte()
            bytes[index * 4 + 3] = value.toByte()
        }
    }
}