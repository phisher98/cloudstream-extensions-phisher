package com.likdev256

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.YoutubeExtractor

@CloudstreamPlugin
class TamilDhoolProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(TamilDhoolProvider())
        registerExtractorAPI(YoutubeExtractor())
        registerExtractorAPI(Dailymotion())
    }
}
