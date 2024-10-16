package com.darkdemon

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FivemovierulzPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerExtractorAPI(Sbanh())
        registerExtractorAPI(filelion())
        registerExtractorAPI(Ncdnstm())
        registerExtractorAPI(StreamTapeAdblockUser())
        registerMainAPI(FivemovierulzProvider())
    }
}
