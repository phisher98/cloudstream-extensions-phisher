package com.phisher98

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY = "AmSmZVcH93UQUezi"
private val IV = intArrayOf(1382367819, 1465333859, 1902406224, 1164854838)

fun decrypt(encryptedB64: String): String {
    return try {
        val keyBytes = KEY.toByteArray(Charsets.UTF_8)
        val ivBytes = IV.toByteArray()
        val encryptedBytes = base64DecodeArray(encryptedB64) // Decode Base64 input
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    } catch (ex: BadPaddingException) {
        "Decryption failed: Invalid padding"
    } catch (ex: IllegalBlockSizeException) {
        "Decryption failed: Incorrect block size"
    } catch (ex: Exception) {
        "Decryption failed: ${ex.message}"
    }
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