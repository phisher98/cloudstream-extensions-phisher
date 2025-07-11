package com.phisher98

import com.phisher98.UltimaUtils.SectionInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    val mapper = jacksonObjectMapper()
    var sectionNamesList: List<String> = emptyList()

    fun loadSections(): List<MainPageData> {
        sectionNamesList = emptyList()
        var data: List<MainPageData> = emptyList()
        data += mainPageOf("" to "watch_sync")
        var enabledSections: List<SectionInfo> = emptyList()
        val savedPlugins = sm.currentExtensions
        savedPlugins.forEach { plugin ->
            plugin.sections?.forEach { section -> if (section.enabled) enabledSections += section }
        }
        enabledSections.sortedByDescending { it.priority }.forEach { section ->
            data +=
                    mainPageOf(
                            mapper.writeValueAsString(section) to
                                    buildSectionName(section)
                    )
        }
        if (data.size.equals(0)) return mainPageOf("" to "") else return data
    }

    private fun buildSectionName(section: SectionInfo): String {
        var name: String
        if (sm.extNameOnHome) name = section.pluginName + ": " + section.name
        else if (sectionNamesList.contains(section.name))
                name =
                        "${section.name} ${sectionNamesList.filter { it.contains(section.name) }.size + 1}"
        else name = section.name
        sectionNamesList += name
        return name
    }

    override val mainPage = loadSections()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        sm.deviceSyncCreds?.syncThisDevice()
        if (request.name.isNotEmpty()) {
            try {
                if (request.name.equals("watch_sync")) {
                    val res =
                            sm.deviceSyncCreds
                                    ?.fetchDevices()
                                    ?.filter {
                                        deviceSyncData?.enabledDevices?.contains(it.deviceId)
                                                ?: false
                                    }
                                    ?.map {
                                        HomePageList(
                                                "Continue from: ${it.name}",
                                                it.syncedData ?: emptyList()
                                        )
                                    }
                                    ?: return null
                    return newHomePageResponse(res, false)
                } else {
                    val realSection: SectionInfo = AppUtils.parseJson<SectionInfo>(request.data)
                    val provider = allProviders.find { it.name == realSection.pluginName }
                    return provider?.getMainPage(
                            page,
                            MainPageRequest(
                                    request.name,
                                    realSection.url.toString(),
                                    request.horizontalImages
                            )
                    )
                }
            } catch (e: Throwable) {
                return null
            }
        } else
                throw ErrorLoadingException(
                        "Select sections from extension's settings page to show here."
                )
    }

    override suspend fun load(url: String): LoadResponse {
        val enabledPlugins =
                mainPage.filter { !it.name.equals("watch_sync") }.map {
                    AppUtils.parseJson<SectionInfo>(it.data).pluginName
                }
        val provider = allProviders.filter { it.name in enabledPlugins }
        for (i in 0 until (provider.size)) {
            try {
                return provider.get(i).load(url)!!
            } catch (e: Throwable) {}
        }
        return newMovieLoadResponse("Welcome to Ultima", "", TvType.Others, "")
    }
}
