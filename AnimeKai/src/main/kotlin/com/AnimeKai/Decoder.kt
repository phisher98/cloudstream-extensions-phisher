package com.AnimeKai

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
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
fun homefn(): List<List<Int>> {
    val f = listOf(
        listOf(0, 88), listOf(0, 33), listOf(0, 234), listOf(4, 1, 7),
        listOf(0, 101), listOf(2, 188), listOf(2, 45), listOf(2, 74),
        listOf(2, 232), listOf(2, 208), listOf(2, 124), listOf(0, 110),
        listOf(2, 211), listOf(2, 9), listOf(0, 153), listOf(0, 140),
        listOf(3, 255), listOf(4, 6, 2), listOf(4, 4, 4), listOf(4, 7, 1),
        listOf(4, 3, 5), listOf(4, 4, 4), listOf(0, 92), listOf(0, 39),
        listOf(0, 97), listOf(3, 255), listOf(0, 65), listOf(0, 213),
        listOf(0, 199), listOf(0, 110)
    )
    return f
}

class AnimekaiDecoder {
    fun generateToken(n: String): String {
        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("NewApi")
        fun encode(n: String): String {
            val n = URLEncoder.encode(n, "UTF-8")
            val out = mutableListOf<Int>()

            val fx = listOf<(Int, Int, Int?) -> Int>(
                { a, b, _ -> (a + b) % 256 },                    // add
                { a, b, _ -> (a * b) and 256 },                   // sum (you might want and 255?)
                { a, b, _ -> a xor b },                           // xor
                { a, b, _ -> a.inv() and b },                     // not
                { a, b, c -> ((a shl b) or (a ushr (c ?: 0))) and 255 } // bitwise shift
            )

            val f = homefn() // <-- use the homefn() you defined earlier

            for (i in n.indices) {
                val fn = f[i % f.size]
                val operation = fx[fn[0]]
                out.add(operation(n[i].code, fn[1], fn.getOrNull(2)))
            }

            val byteArray = out.map { it.toByte() }.toByteArray()
            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArray)
        }
        return encode(n)
    }

    fun decodeIframeData(input: String): String {
        // Use URL-safe Base64 decoding
        val decodedBytes = Base64.getUrlDecoder().decode(input)
        val n = decodedBytes.map { it.toInt() and 0xFF } // Always unsigned
        val out = mutableListOf<Int>()

        // Function list for different operations
        val fx = listOf<(Int, Int, Int?) -> Int>(
            { a, b, _ -> (a - b + 256) % 256 }, // subtract mod 256
            { a, b, _ -> a },                   // ignore division safely (rarely used, or do nothing)
            { a, b, _ -> a xor b },             // xor
            { a, b, _ -> a.inv() and b },       // not
            { a, b, c -> ((a ushr b) or (a shl (c ?: 0))) and 255 } // bitwise (reverse)
        )

        val f = homefn()

        for (i in n.indices) {
            // Retrieve the function and parameters for each step
            val fn = f[i % f.size]
            val operation = fx[fn[0]]
            // Apply the operation
            out.add(operation(n[i], fn[1], fn.getOrNull(2)))
        }

        // Convert the result to a UTF-8 string
        val resultBytes = out.map { it.toByte() }.toByteArray()
        val resultString = String(resultBytes, Charsets.UTF_8)

        // Decode the result string
        return URLDecoder.decode(resultString, "UTF-8")
    }



    fun decode(n: String): String {
        val step1 = base64UrlDecode(n) // B(n)
        val step2 = base64UrlDecode(step1) // B(B(n))
        val key1 = transform("VA3Y4Qj1DB", step2) // y(key, data)
        val key2 = reverseIt(substitute(key1, "cnifqMFatTbg", "niMFfctgqbTa"))
        val key3 = transform("gYXmZMti3aW7", base64UrlDecode(key2)) // y(key, B(data))
        val key4 = reverseIt(substitute(key3, "nhdEm2PHjwO5", "5HPwnOmdhjE2"))
        val finalKey = transform("5JuOqt6PZH", base64UrlDecode(reverseIt(substitute(key4, "bYPIshuCg3DN", "3ubICsgNhDYP"))))
        return decodeURIComponent(finalKey)
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
