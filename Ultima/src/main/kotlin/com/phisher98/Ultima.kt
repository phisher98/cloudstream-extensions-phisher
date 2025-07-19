package com.phisher98

import com.phisher98.UltimaUtils.SectionInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
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
        sectionNamesList = emptyList()

        val result = mutableListOf<MainPageData>()
        val savedPlugins = sm.currentExtensions

        // Always include the "watch_sync" section
        result += mainPageOf("" to "watch_sync")

        // Extract and filter enabled sections from extensions
        val enabledSections = savedPlugins
            .flatMap { it.sections?.asList() ?: emptyList() }
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        enabledSections.forEachIndexed { _, section ->
            try {
                val sectionKey = mapper.writeValueAsString(section)
                val sectionName = buildSectionName(section)
                result += mainPageOf(sectionKey to sectionName)
            } catch (e: Exception) {
                Log.e("loadSections", "Failed to load section ${section.name}: ${e.message}")
            }
        }

        if (result.isEmpty()) {
            return mainPageOf("" to "")
        }
        return result
    }

    private fun buildSectionName(section: SectionInfo): String {
        val name: String = if (sm.extNameOnHome) section.pluginName + ": " + section.name
        else if (sectionNamesList.contains(section.name))
            "${section.name} ${sectionNamesList.filter { it.contains(section.name) }.size + 1}"
        else section.name
        sectionNamesList += name
        return name
    }

    override val mainPage = loadSections()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val creds = sm.deviceSyncCreds
        creds?.syncThisDevice()
        if (request.name.isEmpty()) {
            throw ErrorLoadingException(
                "Select sections from the extension's settings page to show here."
            )
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

                val homeSections = filteredDevices.map {
                    val syncedContent = it.syncedData ?: emptyList()
                    HomePageList("Continue from: ${it.name}", syncedContent)
                }

                newHomePageResponse(homeSections, false)
            } else {
                val section = AppUtils.parseJson<SectionInfo>(request.data)
                val provider = allProviders.find { it.name == section.pluginName }

                if (provider == null) {
                    throw ErrorLoadingException("Provider '${section.pluginName}' is not available.")
                }

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

        providersToTry.forEach { provider ->
            try {
                val response = provider.load(url)
                if (response != null) return response
            } catch (_: Throwable) {}
        }
        return newMovieLoadResponse("Welcome to Ultima", "", TvType.Others, "")
    }

}
