package com.allwish

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AllWishPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AllWish())
        registerExtractorAPI(MegaPlay())
        registerExtractorAPI(Zen())
    }
}
