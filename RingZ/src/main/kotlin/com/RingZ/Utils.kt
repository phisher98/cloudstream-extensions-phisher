package com.RingZ

import android.util.Log
import com.RingZ.RingZ.Companion.headers
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

object RingzConfigLoader {

    suspend fun fetchPages(
        baseUrlFetch: String,
        baseUrlResult: String,
        configPath: String,
        includeAdult: Boolean,
    ): List<Pair<String, String>> {
        val allowedKeys = listOf(
            "allData",
            "latest",
            "JustAdded",
            "Bollywood",
            "Hollywood",
            "South",
            "Punjabi",
            "Gujarati",
            "Bengali",
            "Marathi",
            "Adult",
            "webseries",
            "desihub",
            "anime",
        )

        return try {
            val configUrl = if (configPath.startsWith("/")) baseUrlFetch.trimEnd('/') + configPath else baseUrlFetch.trimEnd('/') + "/" + configPath

            val jsonText = app.get(configUrl, headers = headers).text

            val array = JSONArray(jsonText)

            var selected: JSONObject? = null
            var highestId = Long.MIN_VALUE

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val idNum = obj.optString("id", "").toLongOrNull()
                if (idNum != null && idNum > highestId) {
                    highestId = idNum
                    selected = obj
                }
                Log.d("Phisher",highestId.toString())

            }
            Log.d("Phisher",selected.toString())

            if (selected == null && array.length() > 0)
                selected = array.getJSONObject(array.length() - 1)

            if (selected == null) return emptyList()

            fun getString(key: String): String? =
                if (selected.has(key) && !selected.isNull(key)) selected.optString(key) else null

            fun resolve(path: String?): String? {
                if (path.isNullOrBlank()) return null
                val p = path.trim()
                return when {
                    p.startsWith("http://", true) || p.startsWith("https://", true) -> p
                    p.startsWith("/") -> baseUrlResult.trimEnd('/') + p
                    else -> baseUrlResult.trimEnd('/') + "/" + p
                }
            }

            val output = mutableListOf<Pair<String, String>>()

            for (key in allowedKeys) {
                val raw = getString(key) ?: continue
                if (!raw.contains(".json", ignoreCase = true)) continue
                if (!includeAdult && raw.contains("desihub", ignoreCase = true)) continue
                if (!includeAdult && raw.contains("adult", ignoreCase = true)) continue
                val resolved = resolve(raw) ?: continue
                output += resolved to key
            }

            val seen = linkedSetOf<String>()
            output.filter { seen.add(it.first) }

        } catch (_: Exception) {
            emptyList()
        }
    }
}