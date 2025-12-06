package com.Kartoons

import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

//Link Decryptor

fun base64UrlToBytes(b64url: String): ByteArray {
    var s = b64url.replace('-', '+').replace('_', '/')
    val pad = s.length % 4
    if (pad != 0) {
        s += "=".repeat(4 - pad)
    }
    return base64DecodeArray(s)
}

fun deriveKeyBytes(secret: String): ByteArray {
    val fixed = secret.padEnd(32, ' ').substring(0, 32)
    return fixed.toByteArray(StandardCharsets.UTF_8)
}

fun stripPkcs7Padding(data: ByteArray): ByteArray {
    if (data.isEmpty()) return data
    val padValue = data.last().toInt() and 0xFF
    if (padValue !in 1..16) return data

    // Verify padding bytes
    for (i in 0 until padValue) {
        val b = data[data.size - 1 - i].toInt() and 0xFF
        if (b != padValue) {
            return data
        }
    }
    return data.copyOf(data.size - padValue)
}

/**
 * Generic decrypt:
 * - encryptedDataBase64Url = base64url( IV(16 bytes) || CIPHERTEXT )
 * - secretKeyString = the string used in JS before padEnd(32, " ")
 */

@Throws(IllegalArgumentException::class, Exception::class)
fun decryptAesCbcBase64Url(
    encryptedDataBase64Url: String,
): String {
    val secretKeyString = base64Decode("YmNhOWUwZGYxYTVhYmIzMjkwNmNhM2Y2M2FjMDRjZWY=")
    if (encryptedDataBase64Url.isEmpty() || secretKeyString.isEmpty()) {
        throw IllegalArgumentException("encrypted data and secret key must be provided")
    }

    // Derive key bytes in the same way as JS
    val keyBytes = deriveKeyBytes(secretKeyString)

    if (keyBytes.size != 32) {
        throw IllegalArgumentException("Key length ${keyBytes.size} != 32 bytes")
    }

    // Base64url decode
    val encryptedBytes = base64UrlToBytes(encryptedDataBase64Url)

    if (encryptedBytes.size <= 16) {
        throw IllegalArgumentException("Ciphertext too short: missing IV or data")
    }

    // Split IV + ciphertext
    val iv = encryptedBytes.copyOfRange(0, 16)
    val ciphertext = encryptedBytes.copyOfRange(16, encryptedBytes.size)

    // AES/CBC with manual PKCS7 unpadding
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

    val paddedPlaintext = cipher.doFinal(ciphertext)
    val plaintextBytes = stripPkcs7Padding(paddedPlaintext)

    return String(plaintextBytes, StandardCharsets.UTF_8)
}

//M3u8 Decryptor

private val STREAM_SECRET = base64Decode("cG1TMENBTUcxUnVxNDlXYk15aEUzZmgxc091TFlFTDlydEZhellZbGpWSTJqNEJQU29nNzNoVzdBN3hNaGNlSEQwaXdyUHJWVkRYTHZ4eVdy")

private fun deriveKeySha256(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(STREAM_SECRET.toByteArray(Charsets.UTF_8))
}

fun decryptStream(
    value: String?,
    prefix: String = "enc2:"
): String? {
    if (value.isNullOrEmpty()) {
        println("Error: empty input")
        return null
    }

    if (!value.startsWith(prefix)) {
        return value
    }

    return try {
        val rawB64url = value.substring(prefix.length)

        val blob = base64UrlToBytes(rawB64url)
        if (blob.size <= 12) {
            throw IllegalArgumentException("Ciphertext too short: need 12-byte IV + data")
        }

        val iv = blob.copyOfRange(0, 12)              // 12-byte IV
        val ctAndTag = blob.copyOfRange(12, blob.size) // ciphertext + 16-byte tag

        val keyBytes = deriveKeySha256()
        val keySpec = SecretKeySpec(keyBytes, "AES")

        //AES-GCM decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit auth tag
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plainBytes = cipher.doFinal(ctAndTag)

        //UTF-8 decode
        String(plainBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        println("Decryption failed: ${e.message}")
        null
    }
}