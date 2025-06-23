package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.VidHidePro6

@CloudstreamPlugin
class TelugumvPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Telugumv())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Autoembed())
        registerExtractorAPI(smoothpre())
        registerExtractorAPI(movearnpre())
        }
}
