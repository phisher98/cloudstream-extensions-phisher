package com.yflix

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    Yflix("https://yflix.to" to true),
    Moviesz("https://1movies.bz" to true),
    SOLARMovie("https://solarmovie.fi" to true),
    Sfix("https://sflix.fi" to true),
    Movhub("https://movhub.ws" to true),
}

@CloudstreamPlugin
class YflixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Yflix())
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
            ServerList.Yflix.link.first to "YFlix",
            ServerList.Moviesz.link.first to "1Movies",
            ServerList.SOLARMovie.link.first to "SolarMovie",
            ServerList.Sfix.link.first to "SFlix",
            ServerList.Movhub.link.first to "Movhub",
        )

        var currentYflixServer: String
            get() = getKey("Yflix_CURRENT_SERVER") ?: ServerList.Yflix.link.first
            set(value) {
                setKey("Yflix_CURRENT_SERVER", value)
            }

        fun getCurrentServerName(): String {
            return serverNameMap[currentYflixServer] ?: currentYflixServer
        }

        fun getServerName(url: String): String {
            return serverNameMap[url] ?: url
        }
    }
}

