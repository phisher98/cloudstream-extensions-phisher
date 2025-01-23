package com.hikaritv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnisagaProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Hikaritv())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Swishsrv())
        registerExtractorAPI(FilemoonV2())
    }
}