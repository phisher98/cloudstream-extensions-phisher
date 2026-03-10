package com.watch32

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    Watch32("https://watch32.sx" to true),
    HDToday("https://hdtodayz.to" to true),
    HDTodayAlt("https://hdtoday.cc" to true),
    MyFlixer("https://myflixerz.to" to true),
    MyFlixerAlt("https://myflixer.cx" to true),
    Zoechip("https://zoechip.cc" to true),
    ZoechipAlt("https://zoechip.gg" to true),
    MoviesJoy("https://moviesjoytv.to" to true),
    MoviesJoyAlt("https://moviesjoy.plus" to true),
    HiMovies("https://himovies.sx" to true),
    SoaperTV("https://soapertv.tube" to true),
    MovieOrca("https://www2.movieorca.com" to true),
    AttackerTV("https://attackertv.so" to true),
    MyFlixTor("https://myflixtor.tv" to true),
    DopeBox("https://dopebox.to" to true)
}
@CloudstreamPlugin
class Watch32Plugin : Plugin() {
    override fun load() {
        registerMainAPI(Watch32())
        registerExtractorAPI(Videostr())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        private val serverNameMap = mapOf(
            ServerList.Watch32.link.first to "Watch32",
            ServerList.HDToday.link.first to "HDToday",
            ServerList.HDTodayAlt.link.first to "HDToday",
            ServerList.MyFlixer.link.first to "MyFlixer",
            ServerList.MyFlixerAlt.link.first to "MyFlixer",
            ServerList.Zoechip.link.first to "Zoechip",
            ServerList.ZoechipAlt.link.first to "Zoechip",
            ServerList.MoviesJoy.link.first to "MoviesJoy",
            ServerList.MoviesJoyAlt.link.first to "MoviesJoy",
            ServerList.HiMovies.link.first to "HiMovies",
            ServerList.SoaperTV.link.first to "SoaperTV",
            ServerList.MovieOrca.link.first to "MovieOrca",
            ServerList.AttackerTV.link.first to "AttackerTV",
            ServerList.MyFlixTor.link.first to "MyFlixTor",
            ServerList.DopeBox.link.first to "DopeBox",
        )
        var currentWatch32Server: String
            get() = getKey("Watch32_CURRENT_SERVER") ?: ServerList.Watch32.link.first
            set(value) {
                setKey("Watch32_CURRENT_SERVER", value)
            }

        fun getCurrentServerName(): String {
            return serverNameMap[currentWatch32Server] ?: currentWatch32Server
        }

        fun getServerName(url: String): String {
            return serverNameMap[url] ?: url
        }
    }
}
