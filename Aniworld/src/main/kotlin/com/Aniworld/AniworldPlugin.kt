package com.Aniworld

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmoly

@CloudstreamPlugin
class AniworldPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Aniworld())
        registerMainAPI(Serienstream())
        registerExtractorAPI(Dooood())
        registerExtractorAPI(Vidmoly())
    }
}