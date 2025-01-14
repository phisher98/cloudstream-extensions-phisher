package com.PublicSportsIPTV

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PublicSportsIPTVProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PublicSportsIPTV())
    }
}
