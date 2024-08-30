package com.HindiProviders

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.VidHidePro6

@CloudstreamPlugin
class TelugumvPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Telugumv())
        registerExtractorAPI(VidHidePro6())
        }
}
