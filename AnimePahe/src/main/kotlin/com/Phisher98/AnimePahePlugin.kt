package com.phisher98

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    BEST("https://animepahe.pw" to true),

    ORG("https://animepahe.org" to true),
    SI("https://animepahe.com" to true)
}

@CloudstreamPlugin
class AnimePaheProviderPlugin: Plugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AnimePahe())
        registerExtractorAPI(Kwik())
        registerExtractorAPI(Pahe())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        var currentAnimepaheServer: String
            get() = getKey("ANIMEPAHE_CURRENT_SERVER") ?: ServerList.BEST.link.first
            set(value) {
                setKey("ANIMEPAHE_CURRENT_SERVER", value)
            }

        /** Full cookie string saved after a successful WebView CF bypass (e.g. "cf_clearance=abc; __ddg2_=xyz") */
        var cfCookies: String
            get() = getKey("ANIMEPAHE_CF_COOKIES") ?: ""
            set(value) {
                setKey("ANIMEPAHE_CF_COOKIES", value)
            }

        /** The exact User-Agent string used by the WebView to solve the challenge. */
        var cfUserAgent: String
            get() = getKey("ANIMEPAHE_CF_USER_AGENT") ?: ""
            set(value) {
                setKey("ANIMEPAHE_CF_USER_AGENT", value)
            }

        /** The host for which cfCookies were captured (e.g. "https://animepahe.com") */
        var cfCookieHost: String
            get() = getKey("ANIMEPAHE_CF_COOKIE_HOST") ?: ""
            set(value) {
                setKey("ANIMEPAHE_CF_COOKIE_HOST", value)
            }
    }
}