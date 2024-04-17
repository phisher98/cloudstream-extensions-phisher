package com.horis.cloudstreamplugins

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis

@CloudstreamPlugin
class Kshow123Plugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Kshow123Provider())
        addExtractor(Vidstream2("https://asianplay.pro"))
        addExtractor(Streamsss())
    }

    private fun addExtractor(element: ExtractorApi) {
        element.sourcePlugin = __filename
        extractorApis.add(0, element)
    }
}


