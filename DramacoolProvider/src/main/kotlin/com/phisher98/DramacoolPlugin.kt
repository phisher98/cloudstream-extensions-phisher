package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Dwish
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class DramacoolPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Dramacool())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(dlions())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDropSi())
    }
}


