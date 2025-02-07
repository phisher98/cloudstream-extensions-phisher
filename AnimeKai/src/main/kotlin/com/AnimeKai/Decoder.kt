package com.AnimeKai

import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri

//Credit: Special Thanks to Toasty for the Code
class AnimekaiDecoder {
    fun generateToken(input: String): String {
        val encodedInput = input.encodeUri().replace("+", "%20")
        var temp = base64UrlEncode(transform("gEUzYavPrGpj", reverseIt(encodedInput)))
        temp = substitute(temp, "U8nv0tEFGTb", "bnGvE80UtTF")
        temp = substitute(temp, "9ysoRqBZHV", "oqsZyVHBR9")
        temp = reverseIt(base64UrlEncode(transform("CSk63F7PwBHJKa", temp)))
        temp = substitute(temp, "cKj9BMN15LsdH", "NL5cdKs1jB9MH")
        temp = base64UrlEncode(reverseIt(base64UrlEncode(transform("T2zEp1WHL9CsSk7", temp))))
        return temp
    }


    fun decodeIframeData(n: String): String {
        val temp1 = base64UrlDecode(reverseIt(base64UrlDecode(n)))
        val temp2 = transform("T2zEp1WHL9CsSk7", temp1)
        val temp3 = reverseIt(substitute(temp2, "NL5cdKs1jB9MH", "cKj9BMN15LsdH"))
        val temp4 = transform("CSk63F7PwBHJKa", base64UrlDecode(temp3))
        val temp5 = substitute(temp4, "oqsZyVHBR9", "9ysoRqBZHV")
        val temp6 = base64UrlDecode(substitute(temp5, "bnGvE80UtTF", "U8nv0tEFGTb"))
        return reverseIt(transform("gEUzYavPrGpj", temp6)).decodeUri()
    }

    fun decode(n: String): String {
        var temp1 = base64UrlDecode(base64UrlDecode(n))
        temp1 = reverseIt(transform("E438hS1W9oRmB", temp1))
        temp1 = reverseIt(substitute(temp1, "D5qdzkGANMQZEi", "Q5diEGMADkZzNq"))
        temp1 = base64UrlDecode(
            substitute(
                transform("NZcfoMD7JpIrgQE", base64UrlDecode(temp1)),
                "kTr0pjKzBqZV",
                "kZpjzTV0KqBr"
            )
        )
        temp1 = reverseIt(
            substitute(
                transform("Gay7bxj5B81TJFM", temp1),
                "zcUxoJTi3fgyS",
                "oSgyJUfizcTx3"
            )
        )
        return temp1.decodeUri()
    }
    private fun base64UrlEncode(str: String): String {
        val base64Encoded = base64Encode(str.toByteArray(Charsets.ISO_8859_1))
        return base64Encoded.replace("+", "-").replace("/", "_").replace(Regex("=+$"), "")
    }
    fun base64UrlDecode(n: String): String {
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
}
