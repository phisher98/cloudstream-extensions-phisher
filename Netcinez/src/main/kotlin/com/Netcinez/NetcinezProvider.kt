package com.Netcinez

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class NetcinezProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Netcinez())
    }
}