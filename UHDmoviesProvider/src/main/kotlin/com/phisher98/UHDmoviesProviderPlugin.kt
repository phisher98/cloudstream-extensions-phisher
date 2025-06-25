package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class UHDmoviesProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(UHDmoviesProvider())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(UHDMovies())
        registerExtractorAPI(DriveleechPro())
        registerExtractorAPI(DriveleechNet())
    }
}
