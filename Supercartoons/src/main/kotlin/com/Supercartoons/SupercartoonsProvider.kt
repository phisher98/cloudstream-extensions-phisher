package com.Supercartoons

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SupercartoonsProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Supercartoons())
    }
}