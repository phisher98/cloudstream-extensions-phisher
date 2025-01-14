package com.Pinoymoviepedia

import android.content.Context
import com.lagradost.cloudstream3.extractors.Upstream
import com.lagradost.cloudstream3.extractors.VidHidePro3
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PinoymoviepediaProvider: Plugin() {
    override fun load(context: Context) {
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
