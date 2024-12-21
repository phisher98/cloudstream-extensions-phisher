package com.Topstreamfilm

import android.content.Context
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TopstreamfilmPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(TopStreamFilm())
        registerExtractorAPI(SuperVideo())
        registerExtractorAPI(Dropload())
        registerExtractorAPI(MixDrop())
    }
}
