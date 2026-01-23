package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.OkRuHTTP
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamSB8
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Streamlare
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.Plugin
import androidx.core.content.edit
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Vidguardto2
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import org.json.JSONArray

@CloudstreamPlugin
class StreamPlayPlugin: Plugin() {
    private val registeredMainApis = mutableListOf<MainAPI>()
    private val PREF_FILE = "StreamPlay"
    private val PREF_KEY_LINKS = "streamplay_stremio_saved_links"

    override fun load(context: Context) {

        val sharedPref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val mainApis = listOf(
            StreamPlay(sharedPref),
            StreamPlayAnime(), StreamPlayStremioCatelog("","StreamPlay StremioC",sharedPref)
        )
        val savedSet = sharedPref.getStringSet("enabled_plugins_saved", null)
        val defaultEnabled = mainApis.map { it.name }.toSet()
        val enabledSet = savedSet ?: defaultEnabled

        Log.d("StreamPlay", "SavedSet: $savedSet, DefaultEnabled: $defaultEnabled")
        Log.d("StreamPlay", "Final enabled set: $enabledSet")

        for (api in mainApis) {
            if (enabledSet.contains(api.name)) {
                registerMainAPI(api)
                registeredMainApis.add(api)
                Log.d("StreamPlay", "Registered plugin: ${api.name}")
            } else {
                Log.d("StreamPlay", "Not enabled: ${api.name}")
            }
        }

        sharedPref.edit { remove("enabled_plugins_set") }
        reload(context)
        //=====================MainAPI============================//

        //registerMainAPI(StreamPlayTest(sharedPref))

        //=====================Extractors=========================//

        registerExtractorAPI(Animefever())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(MultimoviesSB())
        registerExtractorAPI(Yipsu())
        registerExtractorAPI(Mwish())
        registerExtractorAPI(TravelR())
        registerExtractorAPI(Playm4u())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VCloud())
        registerExtractorAPI(Kwik())
        registerExtractorAPI(VCloudGDirect())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Snolaxstream())
        registerExtractorAPI(Pixeldra())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(Graceaddresscommunity())
        registerExtractorAPI(M4ufree())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(Filelion())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(dlions())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(dwish())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Uploadever())
        registerExtractorAPI(Netembed())
        registerExtractorAPI(Flaswish())
        registerExtractorAPI(Comedyshow())
        registerExtractorAPI(Ridoo())
        registerExtractorAPI(Streamvid())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(do0od())
        registerExtractorAPI(doodre())
        registerExtractorAPI(Embedrise())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(FilemoonNl())
        registerExtractorAPI(Alions())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(AllinoneDownloader())
        registerExtractorAPI(Tellygossips())
        registerExtractorAPI(Tvlogy())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Sethniceletter())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(fastdlserver())
        registerExtractorAPI(GDFlix1())
        registerExtractorAPI(GDFlix2())
        registerExtractorAPI(furher())
        registerExtractorAPI(Servertwo())
        registerExtractorAPI(MultimoviesAIO())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(MixDropSi())
        registerExtractorAPI(MixDropPs())
        registerExtractorAPI(Streamlare())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        registerExtractorAPI(Embtaku())
        registerExtractorAPI(bulbasaur())
        registerExtractorAPI(Megacloud())
        registerExtractorAPI(Cdnstreame())
        registerExtractorAPI(Rapidplayers())
        registerExtractorAPI(Maxfinishseveral())
        registerExtractorAPI(Pahe())
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(oxxxfile())
        registerExtractorAPI(Hblinks())
        registerExtractorAPI(VidStack())
        registerExtractorAPI(Videostr())
        registerExtractorAPI(DriveleechPro())
        registerExtractorAPI(DriveleechNet())
        registerExtractorAPI(Molop())
        registerExtractorAPI(showflixupnshare())
        registerExtractorAPI(Embedwish())
        registerExtractorAPI(Rubyvidhub())
        registerExtractorAPI(smoothpre())
        registerExtractorAPI(Akirabox())
        registerExtractorAPI(BuzzServer())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Vidguardto2())
        registerExtractorAPI(Hubstreamdad())
        registerExtractorAPI(StreamwishHG())
        registerExtractorAPI(PixelServer())
        registerExtractorAPI(Streameeeeee())
        registerExtractorAPI(Vidora())
        registerExtractorAPI(Fourspromax())
        registerExtractorAPI(XdMoviesExtractor())
        registerExtractorAPI(Hubdrive())
        registerExtractorAPI(Rapidairmax())
        registerExtractorAPI(Rapidshare())
        registerExtractorAPI(HdStream4u())
        registerExtractorAPI(Hubstream())
        registerExtractorAPI(Hubcdnn())
        registerExtractorAPI(HUBCDN())
        registerExtractorAPI(PixelDrainDev())
        registerExtractorAPI(Krakenfiles())
        registerExtractorAPI(MegaUpTwoTwo())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(StreamwishTO())
        registerExtractorAPI(mixdrop21())
        registerExtractorAPI(m1xdrop())

        openSettings = { ctx ->
            val act = ctx as AppCompatActivity
            if (!act.isFinishing && !act.isDestroyed) {
                val frag = MainSettingsFragment(this, sharedPref)
                frag.show(act.supportFragmentManager, "Frag")
            } else {
                Log.e("Plugin", "Activity is not valid anymore, cannot show settings dialog")
            }
        }
    }


    fun reload(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val json = prefs.getString(PREF_KEY_LINKS, null) ?: "[]"
            val arr = JSONArray(json)
            val links = mutableListOf<LinkEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                links.add(
                    LinkEntry(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        name = obj.optString("name", ""),
                        link = obj.optString("link", ""),
                        type = obj.optString("type", "StremioX")
                    )
                )
            }
            for (item in links) {
                val pluginsOnline: Array<PluginData> = PluginManager.getPluginsOnline()
                var found: PluginData? = null
                for (p in pluginsOnline) {
                    if (p.internalName.contains(item.name, ignoreCase = true)) {
                        found = p
                        break
                    }
                }
                if (found != null) {
                    try {
                        PluginManager.unloadPlugin(found.filePath)
                    } catch (e: Throwable) {
                        Log.e("StreamplayStremioXPlugin", "unload failed ${e.message}")
                    }
                } else {
                    try {
                        when (item.type) {
                            "StremioC" -> {
                                try {
                                    registerMainAPI(StreamPlayStremioCatelog(
                                        item.link,
                                        item.name,
                                        sharedPref = prefs
                                    ))
                                } catch (_: Throwable) {
                                    try { registerMainAPI(StreamPlayStremioCatelog(
                                        "",
                                        item.name,
                                        sharedPref = prefs
                                    )) } catch (_: Throwable) {}
                                }
                            }
                            else -> {
                                try { registerMainAPI(StreamPlayStremioCatelog(
                                    item.link,
                                    item.name,
                                    sharedPref = prefs
                                )) } catch (_: Throwable) {}
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("StreamplayStremioXPlugin", "register failed ${e.message}")
                    }
                }
            }
            try {
                MainActivity.afterPluginsLoadedEvent.invoke(true)
            } catch (e: Throwable) {
                Log.w("StreamplayStremioXPlugin", "afterPluginsLoaded invoke failed ${e.message}")
            }
        } catch (e: Throwable) {
            Log.e("StreamplayStremioXPlugin", "reload error ${e.message}")
        }
    }
    data class LinkEntry(
        val id: Long,
        val name: String,
        val link: String,
        val type: String
    )
}
