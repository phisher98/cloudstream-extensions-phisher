package com.KdramaHoodProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.Dwish

@CloudstreamPlugin
class KdramaHoodProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(KdramaHoodProvider())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(Embasic())
    }
}