package com.phisher98

import com.phisher98.UltimaUtils.SectionInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.nicehttp.Requests.Companion.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.collections.forEach

class Ultima(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "Ultima"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val sm = UltimaStorageManager
    private val deviceSyncData = sm.deviceSyncCreds

    private val mapper = jacksonObjectMapper()
    private var sectionNamesList: List<String> = emptyList()

    private fun loadSections(): List<MainPageData> {
        val tempSectionNames = mutableListOf<String>()

        val result = mutableListOf<MainPageData>()
        val savedPlugins = sm.currentExtensions

        result += mainPageOf("" to "watch_sync")

        val enabledSections = savedPlugins
            .flatMap { it.sections?.asList() ?: emptyList() }
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        enabledSections.forEach { section ->
            try {
                val sectionKey = mapper.writeValueAsString(section)
                val sectionName = buildSectionName(section, tempSectionNames)
                result += mainPageOf(sectionKey to sectionName)
            } catch (e: Exception) {
                Log.e("loadSections", "Failed to load section ${section.name}: ${e.message}")
            }
        }

        sectionNamesList = tempSectionNames

        return if (result.size <= 1) mainPageOf("" to "") else result
    }


    private fun buildSectionName(section: SectionInfo, names: MutableList<String>): String {
        val name = if (sm.extNameOnHome) {
            "${section.pluginName}: ${section.name}"
        } else if (names.contains(section.name)) {
            "${section.name} ${names.count { it.startsWith(section.name) } + 1}"
        } else {
            section.name
        }
        names += name
        return name
    }


    override val mainPage = loadSections()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val creds = sm.deviceSyncCreds
        creds?.syncThisDevice()

        if (request.name.isEmpty()) {
            throw ErrorLoadingException("Select sections from the extension's settings page to show here.")
        }

        return try {
            if (request.name == "watch_sync") {
                val syncedDevices = creds?.fetchDevices()
                val filteredDevices = syncedDevices?.filter {
                    deviceSyncData?.enabledDevices?.contains(it.deviceId) == true
                } ?: emptyList()

                if (filteredDevices.isEmpty()) {
                    Log.w("getMainPage", "No enabled devices found in the synced list.")
                    return null
                }

                val homeSections = ArrayList<HomePageList>(filteredDevices.size)

                for (device in filteredDevices) {
                    val syncedContent = device.syncedData ?: continue
                    homeSections += HomePageList("Continue from: ${device.name}", syncedContent)
                }

                newHomePageResponse(homeSections, false)
            } else {
                val section = AppUtils.parseJson<SectionInfo>(request.data)
                val provider = allProviders.find { it.name == section.pluginName }
                    ?: throw ErrorLoadingException("Provider '${section.pluginName}' is not available.")

                provider.getMainPage(
                    page,
                    MainPageRequest(
                        name = request.name,
                        data = section.url,
                        horizontalImages = request.horizontalImages
                    )
                )
            }
        } catch (e: Throwable) {
            Log.e("getMainPage", "Error loading main page: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    override suspend fun search(query: String): List<SearchResponse>? {
        val enabledSections = mainPage
            .filter { !it.name.equals("watch_sync", ignoreCase = true) }
            .mapNotNull {
                try {
                    val section = AppUtils.parseJson<SectionInfo>(it.data)
                    section.pluginName to section
                } catch (_: Exception) {
                    null
                }
            }

        val tasks = mutableListOf<suspend () -> List<SearchResponse>>()

        for ((pluginName, _) in enabledSections) {
            val provider = allProviders.find { it.name == pluginName } ?: continue

            tasks += suspend {
                try {
                    when (val result = provider.search(query)) {
                        is List<*> -> {
                            result.map { item ->
                                when (item) {
                                    is MovieSearchResponse -> item.copy(name = "[$pluginName] ${item.name}")
                                    is AnimeSearchResponse -> item.copy(name = "[$pluginName] ${item.name}")
                                    is TvSeriesSearchResponse -> item.copy(name = "[$pluginName] ${item.name}")
                                    else -> item
                                }
                            }
                        }
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("search", "Search failed for provider $pluginName: ${e.message}")
                    emptyList()
                }
            }
        }


        return runLimitedParallel(limit = 4, tasks).flatten()
    }

    override suspend fun load(url: String): LoadResponse {
        val enabledPlugins = mainPage
            .filter { !it.name.equals("watch_sync", ignoreCase = true) }
            .mapNotNull {
                try {
                    AppUtils.parseJson<SectionInfo>(it.data).pluginName
                } catch (_: Exception) {
                    null
                }
            }

        val providersToTry = allProviders.filter { it.name in enabledPlugins }

        for (provider in providersToTry) {
            try {
                val response = provider.load(url)

                if (response != null &&
                    response.name.isNotBlank() &&
                    !response.posterUrl.isNullOrBlank()
                ) {
                    return response
                }
            } catch (_: Throwable) {
                // Optional: Log specific provider failure if debugging
                Log.e("Ultima load", "Failed loading from ${provider.name}")
            }
        }

        return newMovieLoadResponse("Welcome to Ultima", "", TvType.Others, "")
    }


}
