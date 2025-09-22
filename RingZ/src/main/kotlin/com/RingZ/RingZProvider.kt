package com.RingZ

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class RingZProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(RingZ())
    }
}