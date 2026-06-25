package com.phisher98

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils
import com.phisher98.UltimaUtils.SectionInfo

class Ultima(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "Ultima"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val sm = UltimaStorageManager

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


    override val mainPage get() = loadSections()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.isEmpty()) {
            throw ErrorLoadingException("Select sections from the extension's settings page to show here.")
        }

        return try {
            if (request.name == "watch_sync") {
                val homeSections = ArrayList<HomePageList>()

                try {
                    val payload = UltimaSettingsSyncUtils.fetchCategory(SyncCategory.RESUME_WATCHING)
                    if (payload != null && payload.data.isNotBlank()) {
                        val backupFile = try {
                            mapper.readValue<BackupFile>(payload.data)
                        } catch (_: Exception) {
                            null
                        }

                        if (backupFile != null) {
                            val resumeWatchingKey = backupFile.datastore.string?.keys?.find { it.contains("result_resume_watching") }
                            val resumeWatchingJson = resumeWatchingKey?.let { backupFile.datastore.string[it] }
                            val resumeWatchingList = resumeWatchingJson?.let {
                                try {
                                    mapper.readValue<List<com.lagradost.cloudstream3.utils.DataStoreHelper.ResumeWatchingResult>>(it)
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            if (!resumeWatchingList.isNullOrEmpty()) {
                                homeSections += HomePageList("Continue from Cloud", resumeWatchingList)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("getMainPage", "Error loading watch_sync: ${e.message}")
                }

                newHomePageResponse(homeSections, false)
            } else {
                val section = AppUtils.parseJson<SectionInfo>(request.data)
                val provider = UltimaUtils.getAllProviders().find { it.name == section.pluginName }
                    ?: throw ErrorLoadingException("Provider '${section.pluginName}' is not available.")

                val liveData = provider.mainPage
                    .find { it.name.equals(section.name, ignoreCase = true) }
                    ?.data
                    ?: section.url

                val response = provider.getMainPage(
                    page,
                    MainPageRequest(
                        name = section.name,
                        data = liveData,
                        horizontalImages = request.horizontalImages
                    )
                ) ?: return null

                newHomePageResponse(
                    response.items.map { list ->
                        HomePageList(request.name, list.list, list.isHorizontalImages)
                    },
                    response.hasNext
                )
            }
        } catch (e: Throwable) {
            Log.e("getMainPage", "Error loading main page: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    override suspend fun search(query: String, page: Int): SearchResponseList {
        val enabledPluginNames = sm.currentExtensions
            .flatMap { it.sections?.asList() ?: emptyList() }
            .filter { it.enabled }
            .map { it.pluginName }
            .distinct()

        if (enabledPluginNames.isEmpty()) return emptyList<SearchResponse>().toNewSearchResponseList()

        val allProviders = UltimaUtils.getAllProviders()

        val tasks = enabledPluginNames.mapNotNull { pluginName ->
            val provider = allProviders.find { it.name == pluginName } ?: return@mapNotNull null
            suspend {
                try {
                    val items = provider.search(query, 1)?.items
                        ?: provider.search(query).orEmpty()

                    items.map { item ->
                        newMovieSearchResponse(
                            "[$pluginName] ${item.name}",
                            item.url,
                        ) {
                            this.posterUrl = item.posterUrl
                            this.posterHeaders = item.posterHeaders
                            this.quality = item.quality
                            this.id = item.id
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Ultima", "Search failed for '$pluginName': ${e.message}")
                    emptyList<SearchResponse>()
                }
            }
        }

        return runLimitedParallel(limit = 4, tasks).flatten().toNewSearchResponseList()
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

        val providersToTry = UltimaUtils.getAllProviders().filter { it.name in enabledPlugins }

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
