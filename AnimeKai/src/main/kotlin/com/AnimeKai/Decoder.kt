package com.AnimeKai

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

//Special Credit: Thanks to amarullz
//https://github.com/amarullz/AnimeTV

class AnimekaiDecoder {
    @SuppressLint("NewApi")
    fun generateToken(input: String, kaiKeysSrc: List<List<String>>): String {
        val homeKeyBin = mutableListOf<MutableList<ByteArray>>()
        for (i in kaiKeysSrc.indices) {
            val innerList = mutableListOf<ByteArray>()
            for (j in kaiKeysSrc[i].indices) {
                innerList.add(Base64.getDecoder().decode(kaiKeysSrc[i][j]))
            }
            homeKeyBin.add(innerList)
        }

        // Apply homeEncode logic
        var n = URLEncoder.encode(input, "UTF-8")
        val keyLen = homeKeyBin[0].size

        for (j in 0 until keyLen) {
            val o = mutableListOf<Byte>()
            for (i in n.indices) {
                val c = (n[i].code and 0xFF)
                val k = homeKeyBin[c][j]
                o.add(k[i % k.size])
            }
            n = Base64.getEncoder().encodeToString(o.toByteArray())
        }

        return n.replace("/", "_").replace("+", "-").replace("=", "")
    }

    @RequiresApi(Build.VERSION_CODES.O)

    fun decodeIframeData(input: String, homeKeys: List<List<String>>): String {
        // Step 1: Convert base64-encoded string matrix into ByteArray matrix
        val key = mutableListOf<MutableList<ByteArray>>()
        for (i in homeKeys.indices) {
            val innerList = mutableListOf<ByteArray>()
            for (j in homeKeys[i].indices) {
                innerList.add(Base64.getDecoder().decode(homeKeys[i][j]))
            }
            key.add(innerList)
        }

        // Step 2: Decode encoded input back to standard base64 format
        var n = input.replace('_', '/').replace('-', '+')

        val keyLen = key[0].size

        // Step 3: Run reverse transformation for each round
        for (j in (keyLen - 1) downTo 0) {
            val decoded = Base64.getDecoder().decode(n)
            val o = ByteArray(decoded.size)

            for (i in decoded.indices) {
                val byte = decoded[i]
                for (k in key.indices) {
                    val expectedByte = key[k][j][i % key[k][j].size]
                    if (expectedByte == byte) {
                        o[i] = k.toByte()
                        break
                    }
                }
            }

            n = String(o, Charsets.ISO_8859_1)
        }
        return URLDecoder.decode(n, "UTF-8")
    }

    //Megaup

    @RequiresApi(Build.VERSION_CODES.O)
    fun decode(n: String, megaKeysSrc: List<List<String>>): String {
        // Step 1: convert List<List<String>> to MutableList<MutableList<ByteArray>>
        val megaKeyBin = mutableListOf<MutableList<ByteArray>>()
        for (innerList in megaKeysSrc) {
            val decodedInner = mutableListOf<ByteArray>()
            for (item in innerList) {
                decodedInner.add(Base64.getDecoder().decode(item))
            }
            megaKeyBin.add(decodedInner)
        }

        // Step 2: do the megaDecode logic with megaKeyBin
        var decodedString = n.replace('_', '/').replace('-', '+')
        val keyLen = megaKeyBin[0].size
        for (j in 1 until keyLen) {
            val o = Base64.getDecoder().decode(decodedString)
            for (i in o.indices) {
                val np = (o[i].toInt() and 0xFF)
                val ky = megaKeyBin[np][j]
                o[i] = ky[i % ky.size]
            }
            decodedString = String(o, Charsets.ISO_8859_1)
        }

        return URLDecoder.decode(decodedString)
    }
}
