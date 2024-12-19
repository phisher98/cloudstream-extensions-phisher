package com.hexated

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val KEY = intArrayOf(942683446, 876098358, 875967282, 943142451)
private val IV = intArrayOf(909653298, 909193779, 925905208, 892483379)

// Function to get the AES key from the array of integers
private fun getKey(words: IntArray): SecretKeySpec {
    val keyBytes = words.toByteArray()
    return SecretKeySpec(keyBytes, "AES")
}

// AES decryption function
fun decrypt(data: String): String {
    val key = getKey(KEY)
    val iv = IvParameterSpec(IV.toByteArray())

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key, iv)

    val encryptedBytes = Base64.decode(data, Base64.DEFAULT)
    return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
}

// Convert an IntArray to ByteArray
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
