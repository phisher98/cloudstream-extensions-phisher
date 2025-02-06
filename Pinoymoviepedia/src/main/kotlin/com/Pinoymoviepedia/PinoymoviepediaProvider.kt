package com.Pinoymoviepedia

import com.lagradost.cloudstream3.extractors.Upstream
import com.lagradost.cloudstream3.extractors.VidHidePro3
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PinoymoviepediaProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pinoymoviepedia())
        registerMainAPI(Bluray())
        registerExtractorAPI(Ds2play())
        registerExtractorAPI(Upstream())
        registerExtractorAPI(Vidsp())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHideplus())
        registerExtractorAPI(Voe())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(Luluvdostore())
    }
}
