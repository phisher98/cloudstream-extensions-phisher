package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dwish
import com.lagradost.cloudstream3.extractors.StreamTape


@CloudstreamPlugin
class DramacoolPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Dramacool())
        // registerMainAPI(Dramacool2())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(dlions())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(asianload())
        registerExtractorAPI(MixDropSi())
        registerExtractorAPI(DramacoolExtractor())
        registerExtractorAPI(dhtpre())
        registerExtractorAPI(nikaplayerr())
        registerExtractorAPI(peytonepre())
    }
}
