package com.AnimeKai

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

//Credit: Special Thanks to Toasty for the Code

//Credit: Thanks to https://github.com/amarullz/AnimeTV/blob/master/tools/utils/kai.js

//var Base64UrlDecode = safeAtob
//var transform = rc4
//var substitute = replaceChars
//var reverseIt = reverseString

@Suppress("NAME_SHADOWING")
class AnimekaiDecoder {
    fun generateToken(n: String): String {
        val encodeTransforms: List<(Int) -> Int> = listOf(
            { n -> (n + 111) % 256 },
            { n -> (n + 212) % 256 },
            { n -> n xor 217 },
            { n -> (n + 214) % 256 },
            { n -> (n + 151) % 256 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> (n - 1 + 256) % 256 },
            { n -> (n - 96 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> (n - 206 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n + 116) % 256 },
            { n -> n xor 70 },
            { n -> n xor 147 },
            { n -> (n + 190) % 256 },
            { n -> n xor 222 },
            { n -> (n - 118 + 256) % 256 },
            { n -> (n - 227 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> ((n shl 4) or (n ushr 4)) and 255 },
            { n -> (n + 22) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n + 94) % 256 },
            { n -> (n + 146) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n - 206 + 256) % 256 },
            { n -> (n - 62 + 256) % 256 }
        )

        @SuppressLint("NewApi")
        fun encode(input: String): String {
            val encoded = URLEncoder.encode(input, "UTF-8")
            val transformedBytes = encoded.mapIndexed { i, c ->
                encodeTransforms[i % encodeTransforms.size](c.code) and 255
            }.map { it.toByte() }.toByteArray()
            return base64UrlEncode(String(transformedBytes, Charsets.ISO_8859_1))
        }

        return encode(n)
    }

    fun decodeIframeData(input: String): String {
        val decodeTransforms: List<(Int) -> Int> = listOf(
            { n -> (n - 111 + 256) % 256 },
            { n -> (n - 212 + 256) % 256 },
            { n -> n xor 217 },
            { n -> (n - 214 + 256) % 256 },
            { n -> (n - 151 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> (n + 1) % 256 },
            { n -> (n + 96) % 256 },
            { n -> n.inv() and 255 },
            { n -> n.inv() and 255 },
            { n -> (n + 206) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n - 116 + 256) % 256 },
            { n -> n xor 70 },
            { n -> n xor 147 },
            { n -> (n - 190 + 256) % 256 },
            { n -> n xor 222 },
            { n -> (n + 118) % 256 },
            { n -> (n + 227) % 256 },
            { n -> n.inv() and 255 },
            { n -> ((n ushr 4) or (n shl 4)) and 255 },
            { n -> (n - 22 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n - 94 + 256) % 256 },
            { n -> (n - 146 + 256) % 256 },
            { n -> n.inv() and 255 },
            { n -> (n + 206) % 256 },
            { n -> (n + 62) % 256 },
        )

        val decoded = base64UrlDecode(input)
        val transformed = decoded.mapIndexed { i, c ->
            decodeTransforms[i % decodeTransforms.size](c.code) and 255
        }.map { it.toChar() }
        return decodeURIComponent(transformed.joinToString(""))
    }

    fun decode(input: String, operations: List<List<String>>): String {
        var result = input
        for (op in operations) {
            result = when (op[0]) {
                "safeb64_decode" -> base64UrlDecode(result)  // Your safe base64 decode method
                "reverse" -> reverseIt(result)  // Your reverse string method
                "substitute" -> {
                    if (op.size < 3) throw IllegalArgumentException("Substitute requires two arguments: from and to")
                    substitute(result, op[1], op[2])  // Your substitute method
                }
                "rc4" -> {
                    if (op.size < 2) throw IllegalArgumentException("RC4 requires one argument: the key")
                    transform(op[1], result)  // Your RC4 transform method
                }
                "urldecode" -> URLDecoder.decode(result, "UTF-8")  // URL decode
                else -> error("Unknown operation: ${op[0]}")  // Handle unknown operations
            }
        }
        return result
    }

    private fun base64UrlEncode(str: String): String {
        return base64Encode(str.toByteArray(Charsets.ISO_8859_1))
            .replace("+", "-")
            .replace("/", "_")
            .replace(Regex("=+$"), "")
    }

    private fun base64UrlDecode(n: String): String {
        val padded = n.padEnd(n.length + ((4 - (n.length % 4)) % 4), '=')
            .replace('-', '+')
            .replace('_', '/')
        return base64Decode(padded)
    }


    private fun transform(n: String, t: String): String {
        val v = IntArray(256) { it }
        var c = 0
        val f = StringBuilder()
        for (w in 0 until 256) {
            c = (c + v[w] + n[w % n.length].code) % 256
            v[w] = v[c].also { v[c] = v[w] }
        }
        var a = 0
        var w = 0
        c = 0
        while (a < t.length) {
            w = (w + 1) % 256
            c = (c + v[w]) % 256
            v[w] = v[c].also { v[c] = v[w] }
            f.append((t[a].code xor v[(v[w] + v[c]) % 256]).toChar())
            a++
        }
        return f.toString()
    }

    private fun reverseIt(input: String) = input.reversed()

    private fun substitute(input: String, keys: String, values: String): String {
        val map = mutableMapOf<Char, Char>()
        for (i in keys.indices) {
            map[keys[i]] = values.getOrNull(i) ?: keys[i]
        }
        val result = StringBuilder()
        for (char in input) {
            result.append(map[char] ?: char)
        }
        return result.toString()
    }

    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun decodeURIComponent(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun decodeUri(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
