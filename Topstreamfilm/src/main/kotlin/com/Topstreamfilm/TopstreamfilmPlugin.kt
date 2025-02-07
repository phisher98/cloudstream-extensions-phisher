package com.Topstreamfilm

import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TopstreamfilmPlugin : BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(TopStreamFilm())
        registerExtractorAPI(SuperVideo())
        registerExtractorAPI(Dropload())
        registerExtractorAPI(MixDrop())
    }
}
