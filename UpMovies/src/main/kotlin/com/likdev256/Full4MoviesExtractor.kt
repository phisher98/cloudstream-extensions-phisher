package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.app

class Full4MoviesExtractor {

    suspend fun getStreamUrl(link: String): List<String> {
        val doc= app.get(link).document
        val links = emptySet<String>().toMutableSet()
        val sourceUrls = mutableListOf<String>()
        doc.select("p > a.autohyperlink").forEach {
            val url=it.attr("href")
            Log.d("Test Extractor Log", url)
            links += url
        }
        return links.toList()
    }
}