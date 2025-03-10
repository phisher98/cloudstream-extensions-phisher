package com.AnimeKai

import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import java.net.URLDecoder
import java.net.URLEncoder

//Credit: Special Thanks to Toasty for the Code
@Suppress("NAME_SHADOWING")
class AnimekaiDecoder {
    fun generateToken(n: String): String {
        var n = encodeURIComponent(n)
        n = base64UrlEncode(
            substitute(
                base64UrlEncode(
                    transform("sXmH96C4vhRrgi8",
                        reverseIt(
                            reverseIt(
                                base64UrlEncode(
                                    transform("kOCJnByYmfI",
                                        substitute(
                                            substitute(
                                                reverseIt(base64UrlEncode(transform("0DU8ksIVlFcia2", n))),
                                                "1wctXeHqb2",
                                                "1tecHq2Xbw"
                                            ),
                                            "48KbrZx1ml",
                                            "Km8Zb4lxr1"
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                "hTn79AMjduR5",
                "djn5uT7AMR9h"
            )
        )
        return n
    }

    fun decodeIframeData(n: String): String {
        return decodeURIComponent(
            transform(
                "0DU8ksIVlFcia2",
                base64UrlDecode(
                    reverseIt(
                        substitute(
                            substitute(
                                transform(
                                    "kOCJnByYmfI",
                                    base64UrlDecode(
                                        reverseIt(
                                            reverseIt(
                                                transform(
                                                    "sXmH96C4vhRrgi8",
                                                    base64UrlDecode(
                                                        substitute(
                                                            base64UrlDecode(n),
                                                            "djn5uT7AMR9h",
                                                            "hTn79AMjduR5"
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                                "Km8Zb4lxr1", "48KbrZx1ml"
                            ),
                            "1tecHq2Xbw", "1wctXeHqb2"
                        )
                    )
                )
            )
        )
    }

    fun decode(n: String): String {
        return decodeUri(
            substitute(
                transform(
                    "fnxEj3tD4Bl0X",
                    base64UrlDecode(
                        reverseIt(
                            reverseIt(
                                transform(
                                    "IjilzMV57GrnF",
                                    base64UrlDecode(
                                        substitute(
                                            reverseIt(
                                                substitute(
                                                    transform(
                                                        "PlzI69YVCtGwoa8",
                                                        base64UrlDecode(
                                                            base64UrlDecode(n)
                                                        )
                                                    ),
                                                    "c2IfHZwSX1mj",
                                                    "mwfXcS2ZjI1H"
                                                )
                                            ),
                                            "82NkgQDYbIF",
                                            "82IQNkFgYbD"
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                "crwkth05iJR8",
                "JRkt8rw0i5ch"
            )
        )
    }

    private fun base64UrlEncode(str: String): String {
        val base64Encoded = base64Encode(str.toByteArray(Charsets.ISO_8859_1))
        return base64Encoded.replace("+", "-").replace("/", "_").replace(Regex("=+$"), "")
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
