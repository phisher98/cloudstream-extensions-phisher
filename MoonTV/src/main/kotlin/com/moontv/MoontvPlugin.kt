package com.moontv

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {

    Moontv("https://moontv.to" to true),
    Cine("https://123cine.to" to true),
    Flixzone("https://flixzone.co" to true),
}


@CloudstreamPlugin
class MoontvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Moontv())
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(Fourspromax())
        registerExtractorAPI(Rapidairmax())
        registerExtractorAPI(rapidshare())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        private val serverNameMap = mapOf(
            ServerList.Moontv.link.first to "Moontv",
            ServerList.Cine.link.first to "123cine",
            ServerList.Flixzone.link.first to "Flixzone",
            )
        var currentMoontvServer: String
            get() = getKey("Moontv_CURRENT_SERVER") ?: ServerList.Moontv.link.first
            set(value) {
                setKey("Moontv_CURRENT_SERVER", value)
            }

        fun getCurrentServerName(): String {
            return serverNameMap[currentMoontvServer] ?: currentMoontvServer
        }

        fun getServerName(url: String): String {
            return serverNameMap[url] ?: url
        }
    }
}

