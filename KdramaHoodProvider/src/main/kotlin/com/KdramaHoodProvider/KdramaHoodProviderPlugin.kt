package com.KdramaHoodProvider

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.Dwish

@CloudstreamPlugin
class KdramaHoodProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(KdramaHoodProvider())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(Embasic())
    }
}