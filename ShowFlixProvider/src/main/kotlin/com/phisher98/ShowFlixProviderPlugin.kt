package com.phisher98

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro3
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ShowFlixProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(ShowFlixProvider())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Showflixupnshare())
        registerExtractorAPI(Rubyvidhub())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Showflixarchives())
    }
}
