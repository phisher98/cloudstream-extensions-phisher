package com.Phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Vidguardto2
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.Wishonly

@CloudstreamPlugin
class FourKHDHubProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(FourKHDHub())
    }
}