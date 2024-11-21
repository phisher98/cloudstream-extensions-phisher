package com.TorraStream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TorraStreamProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TorraStream())
    }
}
