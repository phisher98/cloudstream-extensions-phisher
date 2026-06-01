package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.api.Log
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.mapper
import java.security.MessageDigest
import androidx.core.content.edit
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val backupMapper = jacksonObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

fun BackupFile.toJsonSorted(): String {
    return backupMapper.writeValueAsString(this)
}


data class BackupVars(
    @JsonProperty("_Bool") val bool: Map<String, Boolean>?,
    @JsonProperty("_Int") val int: Map<String, Int>?,
    @JsonProperty("_String") val string: Map<String, String>?,
    @JsonProperty("_Float") val float: Map<String, Float>?,
    @JsonProperty("_Long") val long: Map<String, Long>?,
    @JsonProperty("_StringSet") val stringSet: Map<String, Set<String>?>?,
)

data class BackupFile(
    @JsonProperty("datastore") val datastore: BackupVars,
    @JsonProperty("settings") val settings: BackupVars
)

data class UltimaEditor(
    val editor: SharedPreferences.Editor
) {
    fun <T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (isStringSet(value)) {
            editor.putStringSet(path, value as Set<String>)
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?): Boolean {
        if (value is Set<*>) {
            return value.filterIsInstance<String>().size == value.size
        }
        return false
    }

    fun apply() {
        editor.apply()
    }
}

object UltimaBackupUtils {
    private const val TAG = "UltimaSync"

    val nonTransferableKeys = listOf(
        "anilist_unixtime",
        "anilist_token",
        "anilist_user",
        "anilist_cached_list",
        "anilist_accounts",
        "anilist_active",
        "mal_user",
        "mal_cached_list",
        "mal_unixtime",
        "mal_refresh_token",
        "mal_token",
        "mal_accounts",
        "mal_active",
        "simkl_token",
        "simkl_user",
        "simkl_cached_list",
        "simkl_cached_time",
        "simkl_accounts",
        "simkl_active",
        "SIMKL_API_CACHE",
        "ANIWAVE_SIMKL_SYNC",
        "open_subtitles_user",
        "opensubtitles_accounts",
        "opensubtitles_active",
        "subdl_user",
        "subdl_accounts",
        "subdl_active",
        "biometric_key",
        "nginx_user",
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_path_key",
        "cs3-votes",
        "last_sync_api",
        "last_click_action",
        "last_opened_id",
        "library_folder",
        "result_resume_watching_migrated",
        "jsdelivr_proxy_key",
        //custom
        "fshare_setup",
        "fshare_token",
        "bluphim_token",
        "device_id",
        "sync_token",
        "sync_project_num",
        "sync_project_id",
        "sync_item_id",
        "sync_device_id",
        "restore_device",
        "backup_device",
        "download_info",
        "download_resume",
        "download_q_resume",
        "download_episode_cache",
        "prerelease_update",
        "data_store_helper/account_key_index",
        "VERSION_NAME",
        "FILES_TO_DELETE_KEY",
        "HAS_DONE_SETUP",
        "ULTIMA_WATCH_SYNC_CREDS",
        "ULTIMA_APP_SETTINGS_SYNC_CREDS",
        "used_fstream_providers_v3",
        "fstream_version",
        // Exclude active homepage provider from sync so each device keeps its local selection
        "home_api_used",
        "home_api",
        "user_selected_homepage_api",

        // Exclude UI tab selections and sorting modes
        "last_sync_api_key",
        "home_pref_homepage",
        "library_sorting_mode",
        "results_sorting_mode",
        "library_folder",
        "viewpager_item_key"
    )

    private fun String.isTransferable(): Boolean {
        val lower = this.lowercase()
        return !nonTransferableKeys.any { lower.contains(it.lowercase()) }
    }

    // --- v2 Category System ---

    fun classifyKey(key: String): SyncCategory? {
        val lowerKey = key.lowercase()

        // Non-transferable keys don't belong to any category
        if (!key.isTransferable()) return null

        // Bookmarks
        if (lowerKey.contains("result_favorites_state_data") || lowerKey.contains("result_watch_state")) {
            return SyncCategory.BOOKMARKS
        }

        // Resume watching
        if (lowerKey.contains("result_resume_watching") || lowerKey.contains("video_pos_dur") ||
            lowerKey.contains("download_header_cache") || lowerKey.contains("result_season") ||
            lowerKey.contains("result_dub") || lowerKey.contains("result_episode")) {
            return SyncCategory.RESUME_WATCHING
        }

        // Search history
        if (lowerKey.contains("search_history")) {
            return SyncCategory.SEARCH_HISTORY
        }

        // Extensions (never sync local plugins)
        if (lowerKey.contains("plugins_key_local")) return null

        if (lowerKey.contains("plugins_key") ||
            lowerKey.contains("plugins_repositories") ||
            lowerKey.contains("repositories") ||
            lowerKey.contains("ultima_extensions_list") ||
            lowerKey.contains("ultima_current_meta_providers") ||
            lowerKey.contains("ultima_current_media_providers") ||
            key.equals(REPOSITORIES_KEY, ignoreCase = true)) {
            return SyncCategory.EXTENSIONS
        }

        // Everything else is settings
        return SyncCategory.SETTINGS
    }

    fun classifySettingsKey(key: String): SettingsSubCategory {
        val lowerKey = key.lowercase()
        return when {
            // Player settings
            lowerKey.contains("player") || lowerKey.contains("video") || lowerKey.contains("play") || 
            lowerKey.contains("buffer") || lowerKey.contains("resize") || lowerKey.contains("skip") || 
            lowerKey.contains("volume") || lowerKey.contains("brightness") || lowerKey.contains("gesture") ||
            lowerKey.contains("speed") || lowerKey.contains("decoder") || lowerKey.contains("render") ||
            lowerKey.contains("fit") || lowerKey.contains("aspect") -> SettingsSubCategory.PLAYER

            // Subtitles settings
            lowerKey.contains("subtitle") || lowerKey.contains("sub") || lowerKey.contains("caption") ||
            lowerKey.contains("lang") || lowerKey.contains("font") -> SettingsSubCategory.SUBTITLES

            // Theme settings
            lowerKey.contains("theme") || lowerKey.contains("dark") || lowerKey.contains("color") || 
            lowerKey.contains("accent") || lowerKey.contains("primary") || lowerKey.contains("style") -> SettingsSubCategory.THEME

            // Layout settings
            lowerKey.contains("layout") || lowerKey.contains("view") || lowerKey.contains("grid") || 
            lowerKey.contains("list") || lowerKey.contains("home") || lowerKey.contains("card") ||
            lowerKey.contains("tab") || lowerKey.contains("row") || lowerKey.contains("show_") ||
            lowerKey.contains("homepage") -> SettingsSubCategory.LAYOUT

            // Downloads settings
            lowerKey.contains("download") || lowerKey.contains("down") || lowerKey.contains("path") -> SettingsSubCategory.DOWNLOADS

            // General settings
            else -> SettingsSubCategory.GENERAL
        }
    }

    fun isKeyBackupEnabled(key: String, category: SyncCategory, creds: AppSettingsSyncCreds): Boolean {
        return when (category) {
            SyncCategory.EXTENSIONS -> creds.backupExtensions
            SyncCategory.BOOKMARKS -> creds.backupBookmarks
            SyncCategory.RESUME_WATCHING -> creds.backupResumeWatching
            SyncCategory.SEARCH_HISTORY -> creds.backupSearchHistory
            SyncCategory.SETTINGS -> {
                val sub = classifySettingsKey(key)
                creds.isSettingsBackupEnabled(sub)
            }
        }
    }

    fun isKeyRestoreEnabled(key: String, category: SyncCategory, creds: AppSettingsSyncCreds): Boolean {
        return when (category) {
            SyncCategory.EXTENSIONS -> creds.restoreExtensions
            SyncCategory.BOOKMARKS -> creds.restoreBookmarks
            SyncCategory.RESUME_WATCHING -> creds.restoreResumeWatching
            SyncCategory.SEARCH_HISTORY -> creds.restoreSearchHistory
            SyncCategory.SETTINGS -> {
                val sub = classifySettingsKey(key)
                creds.isSettingsRestoreEnabled(sub)
            }
        }
    }

    fun computeHash(data: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(data.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun getBackupForCategory(context: Context, category: SyncCategory, resumeWatching: List<DataStoreHelper.ResumeWatchingResult>? = null): BackupFile? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        val allData = context.getSharedPrefs().all.filter { entry ->
            entry.key.isTransferable() && classifyKey(entry.key) == category &&
            isResumeWatchingRelevant(entry.key, resumeWatching) &&
            isKeyBackupEnabled(entry.key, category, creds)
        }
        val allSettings = context.getDefaultSharedPrefs().all.filter { entry ->
            entry.key.isTransferable() && classifyKey(entry.key) == category &&
            isResumeWatchingRelevant(entry.key, resumeWatching) &&
            isKeyBackupEnabled(entry.key, category, creds)
        }

        if (allData.isEmpty() && allSettings.isEmpty()) return null

        return BackupFile(
            datastore = buildBackupVars(allData),
            settings = buildBackupVars(allSettings)
        )
    }

    private fun isResumeWatchingRelevant(key: String, resumeWatching: List<DataStoreHelper.ResumeWatchingResult>?): Boolean {
        if (resumeWatching == null) return true
        val lowerKey = key.lowercase()
        if (lowerKey.contains("download_header_cache")) {
            val id = key.split("/").getOrNull(1)?.toIntOrNull()
            return id?.let { intId ->
                resumeWatching.any { if (it.parentId != null) it.parentId == intId else it.id == intId }
            } ?: false
        } else if (lowerKey.contains("video_pos_dur")) {
            val id = key.split("/").getOrNull(2)?.toIntOrNull()
            return id?.let { intId ->
                resumeWatching.any { it.id == intId }
            } ?: false
        } else if (lowerKey.contains("result_season") || lowerKey.contains("result_dub") || lowerKey.contains("result_episode")) {
            val id = key.split("/").getOrNull(2)?.toIntOrNull()
            return id?.let { intId ->
                resumeWatching.any { it.parentId == intId }
            } ?: false
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildBackupVars(data: Map<String, *>) : BackupVars {
        return BackupVars(
            data.filter { it.value is Boolean } as? Map<String, Boolean>,
            data.filter { it.value is Int } as? Map<String, Int>,
            data.filter { it.value is String } as? Map<String, String>,
            data.filter { it.value is Float } as? Map<String, Float>,
            data.filter { it.value is Long } as? Map<String, Long>,
            data.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )
    }

    fun restoreCategory(context: Context, category: SyncCategory, backupFile: BackupFile) {
        Log.d(TAG, "Restoring category: ${category.key}")

        // Restore datastore prefs
        restoreBackupVars(context, category, backupFile.datastore, isSettings = false)
        // Restore default settings prefs
        restoreBackupVars(context, category, backupFile.settings, isSettings = true)

        try {
            val keys = getBackupFileKeys(backupFile)
            UltimaStorageManager.setCategorySyncedKeys(category, keys)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save synced keys on restore: ${e.message}")
        }
    }

    private fun restoreBackupVars(context: Context, category: SyncCategory, vars: BackupVars, isSettings: Boolean) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        val prefs = if (isSettings) context.getDefaultSharedPrefs() else context.getSharedPrefs()
        val editor = editor(context, isSettings)
        
        // Delete local keys that are missing in the incoming backup (only for dynamic categories)
        if (isDynamicCategory(category)) {
            val incomingKeys = mutableSetOf<String>()
            vars.bool?.keys?.let { incomingKeys.addAll(it) }
            vars.int?.keys?.let { incomingKeys.addAll(it) }
            vars.float?.keys?.let { incomingKeys.addAll(it) }
            vars.long?.keys?.let { incomingKeys.addAll(it) }
            vars.stringSet?.keys?.let { incomingKeys.addAll(it) }
            vars.string?.keys?.let { incomingKeys.addAll(it) }

            prefs.all.forEach { (k, _) ->
                if (k.isTransferable() && classifyKey(k) == category && isKeyRestoreEnabled(k, category, creds)) {
                    if (!incomingKeys.contains(k)) {
                        Log.d(TAG, "Removing deleted local key: $k")
                        editor.editor.remove(k)
                    }
                }
            }
        }

        vars.bool?.forEach { (k, v) -> if (k.isTransferable() && isKeyRestoreEnabled(k, category, creds)) editor.setKeyRaw(k, v) }
        vars.int?.forEach { (k, v) -> if (k.isTransferable() && isKeyRestoreEnabled(k, category, creds)) editor.setKeyRaw(k, v) }
        vars.float?.forEach { (k, v) -> if (k.isTransferable() && isKeyRestoreEnabled(k, category, creds)) editor.setKeyRaw(k, v) }
        vars.long?.forEach { (k, v) -> if (k.isTransferable() && isKeyRestoreEnabled(k, category, creds)) editor.setKeyRaw(k, v) }
        vars.stringSet?.forEach { (k, v) -> if (k.isTransferable() && isKeyRestoreEnabled(k, category, creds)) editor.setKeyRaw(k, v) }
        // Strings last — excludes PLUGINS_KEY and REPOSITORIES_KEY (handled separately for extensions)
        vars.string?.forEach { (k, v) ->
            if (k.isTransferable() &&
                !k.equals("PLUGINS_KEY", ignoreCase = true) &&
                !k.equals(REPOSITORIES_KEY, ignoreCase = true) &&
                !k.equals("plugins_repositories", ignoreCase = true) &&
                !k.equals("repositories", ignoreCase = true) &&
                isKeyRestoreEnabled(k, category, creds)) {
                
                val localVal = prefs.getString(k, null)
                val cloudTs = extractTimestamp(v)
                val localTs = extractTimestamp(localVal)
                
                if (localVal == null || (cloudTs == 0L && localTs == 0L) || cloudTs > localTs) {
                    editor.setKeyRaw(k, v)
                }
            }
        }
        editor.apply()
    }

    // --- Extensions-specific restore logic ---

    private fun forceConvertRawGitUrl(url: String): String {
        val ghRegex = Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)/(.*)$")
        val match = ghRegex.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    suspend fun restoreExtensionsCategory(context: Context, backupFile: BackupFile) {
        Log.d(TAG, "Restoring extensions category with download/load logic")

        // 1. Merge repositories first
        mergeRepositories(context, backupFile)

        // 2. Download and load plugins
        downloadAndLoadPlugins(context, backupFile)
    }

    private fun mergeRepositories(context: Context, backupFile: BackupFile) {
        try {
            val repoValue = backupFile.settings.string?.entries?.find {
                it.key.equals(REPOSITORIES_KEY, ignoreCase = true) ||
                it.key.equals("plugins_repositories", ignoreCase = true) ||
                it.key.equals("repositories", ignoreCase = true)
            }?.value
                ?: backupFile.datastore.string?.entries?.find {
                    it.key.equals(REPOSITORIES_KEY, ignoreCase = true) ||
                    it.key.equals("plugins_repositories", ignoreCase = true) ||
                    it.key.equals("repositories", ignoreCase = true)
                }?.value

            if (!repoValue.isNullOrBlank()) {
                try {
                    val incomingRepos = mapper.readValue<Array<RepositoryData>>(repoValue)
                    val currentRepos = RepositoryManager.getRepositories()
                    val mergedRepos = (currentRepos + incomingRepos).distinctBy { it.url.trim().lowercase() }

                    val mergedReposJson = mergedRepos.toTypedArray().toJson()

                    context.getDefaultSharedPrefs().edit {
                        putString(REPOSITORIES_KEY, mergedReposJson)
                            .putString("plugins_repositories", mergedReposJson)
                            .putString("repositories", mergedReposJson)
                    }

                    context.getSharedPrefs().edit {
                        putString(REPOSITORIES_KEY, mergedReposJson)
                            .putString("plugins_repositories", mergedReposJson)
                            .putString("repositories", mergedReposJson)
                    }

                    setKey(REPOSITORIES_KEY, mergedRepos.toTypedArray())
                    try {
                        setKey("plugins_repositories", mergedRepos.toTypedArray())
                        setKey("repositories", mergedRepos.toTypedArray())
                    } catch (_: Exception) {}

                    Log.d(TAG, "Merged repos: ${currentRepos.size} local + ${incomingRepos.size} incoming = ${mergedRepos.size} merged")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to merge repos: ${e.message}")
                    // Fallback: write incoming directly
                    context.getDefaultSharedPrefs().edit {
                        putString(REPOSITORIES_KEY, repoValue)
                            .putString("plugins_repositories", repoValue)
                            .putString("repositories", repoValue)
                    }
                    context.getSharedPrefs().edit {
                        putString(REPOSITORIES_KEY, repoValue)
                            .putString("plugins_repositories", repoValue)
                            .putString("repositories", repoValue)
                    }
                    try {
                        val repoArray = mapper.readValue<Array<RepositoryData>>(repoValue)
                        setKey(REPOSITORIES_KEY, repoArray)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Repository merge error: ${e.message}")
        }
    }

    private suspend fun downloadAndLoadPlugins(context: Context, backupFile: BackupFile) {
        val pluginsJson = backupFile.datastore.string?.entries?.find { it.key.equals("PLUGINS_KEY", ignoreCase = true) }?.value
            ?: backupFile.settings.string?.entries?.find { it.key.equals("PLUGINS_KEY", ignoreCase = true) }?.value

        if (pluginsJson.isNullOrBlank()) return

        val pluginsList = try {
            mapper.readValue<Array<PluginData>>(pluginsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plugins JSON: ${e.message}")
            return
        }

        // Fetch all online plugins from configured repos (in parallel)
        val allOnlinePlugins = mutableListOf<Pair<String, SitePlugin>>()
        val repositories = RepositoryManager.getRepositories()
        coroutineScope {
            val deferreds = repositories.map { repo ->
                async(Dispatchers.IO) {
                    try {
                        RepositoryManager.getRepoPlugins(repo.url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch plugins for repo ${repo.url}: ${e.message}")
                        null
                    }
                }
            }
            deferreds.awaitAll().forEach { result ->
                if (result != null) allOnlinePlugins.addAll(result)
            }
        }

        // Detect deleted plugins
        val currentLocalPlugins = PluginManager.getPluginsOnline()
        val incomingNames = pluginsList.map { it.internalName }.toSet()
        val deletedPlugins = currentLocalPlugins.filter {
            !incomingNames.contains(it.internalName) &&
            !it.internalName.equals("Ultima", ignoreCase = true)
        }

        val updatedPluginsList = mutableListOf<PluginData>()
        val ultimaPlugin = currentLocalPlugins.find { it.internalName.equals("Ultima", ignoreCase = true) }
        if (ultimaPlugin != null) {
            updatedPluginsList.add(ultimaPlugin)
        }
        val newlyDownloaded = mutableSetOf<String>()
        var downloadedAny = false

        val downloadSemaphore = Semaphore(4)
        coroutineScope {
            val downloadResults = pluginsList.filter {
                !it.internalName.equals("Ultima", ignoreCase = true)
            }.map { plugin ->
                async(Dispatchers.IO) {
                    downloadSemaphore.withPermit {
                        val match = allOnlinePlugins.find { it.second.internalName.equals(plugin.internalName, ignoreCase = true) }

                        val localFile = if (match != null) {
                            PluginManager.getPluginPath(context, plugin.internalName, match.first)
                        } else {
                            val cleanPath = plugin.filePath.replace('\\', '/')
                            val relativePath = if (cleanPath.contains("Extensions/")) {
                                "Extensions/" + cleanPath.substringAfter("Extensions/")
                            } else {
                                "Extensions/DefaultRepo/" + cleanPath.substringAfterLast('/')
                            }
                            File(context.filesDir, relativePath)
                        }

                        val targetUrl = match?.second?.url ?: plugin.url

                        if (localFile.exists() && localFile.length() > 0) {
                            Triple(plugin.copy(filePath = localFile.absolutePath), false, false)
                        } else {
                            var resultPlugin: PluginData? = null
                            if (!targetUrl.isNullOrBlank()) {
                                val downloadUrl = forceConvertRawGitUrl(targetUrl)
                                Log.d(TAG, "Downloading plugin: ${plugin.internalName} from $downloadUrl")
                                try {
                                    val tempFile = File.createTempFile(plugin.internalName, ".tmp", context.cacheDir)
                                    try {
                                        val response = com.lagradost.cloudstream3.app.get(downloadUrl)
                                        if (response.code == 200) {
                                            val body = response.okhttpResponse.body
                                            tempFile.outputStream().use { fos ->
                                                body.byteStream().use { bis -> bis.copyTo(fos) }
                                            }
                                            if (tempFile.exists() && tempFile.length() > 0) {
                                                localFile.parentFile?.mkdirs()
                                                tempFile.copyTo(localFile, overwrite = true)
                                                resultPlugin = plugin.copy(filePath = localFile.absolutePath, url = targetUrl)
                                            }
                                        }
                                    } finally {
                                        if (tempFile.exists()) tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Download error for ${plugin.internalName}: ${e.message}")
                                }
                            }

                            // Clean up 0 KB files
                            if (localFile.exists() && localFile.length() == 0L) {
                                try { localFile.delete() } catch (_: Exception) {}
                            }

                            if (resultPlugin != null) {
                                Triple(resultPlugin, true, true)
                            } else {
                                null
                            }
                        }
                    }
                }
            }.awaitAll()

            for (result in downloadResults) {
                if (result != null) {
                    val (pluginData, isNewlyDownloaded, wasDownloaded) = result
                    updatedPluginsList.add(pluginData)
                    if (isNewlyDownloaded) newlyDownloaded.add(pluginData.internalName)
                    if (wasDownloaded) downloadedAny = true
                }
            }
        }

        // Unload deleted plugins
        for (plugin in deletedPlugins) {
            try {
                PluginManager.unloadPlugin(plugin.filePath)
                val file = File(plugin.filePath)
                if (file.exists()) file.delete()
                Log.d(TAG, "Unloaded deleted plugin: ${plugin.internalName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading ${plugin.internalName}: ${e.message}")
            }
        }

        // Save updated PLUGINS_KEY
        val updatedJson = updatedPluginsList.toTypedArray().toJson()
        context.getSharedPrefs().edit { putString("PLUGINS_KEY", updatedJson) }
        try {
            setKey("PLUGINS_KEY", updatedPluginsList.toTypedArray())
        } catch (_: Exception) {}

        // Load/reload plugins
        var loadedAny = false
        for (plugin in updatedPluginsList) {
            try {
                val isLoaded = PluginManager.plugins.containsKey(plugin.filePath)
                val isNew = newlyDownloaded.contains(plugin.internalName)

                if (isLoaded && isNew) {
                    PluginManager.unloadPlugin(plugin.filePath)
                }

                if (!isLoaded || isNew) {
                    val apiName = plugin.internalName.replace("provider", "", ignoreCase = true)
                    val loaded = PluginManager.loadSinglePlugin(context, apiName) || PluginManager.loadSinglePlugin(context, plugin.internalName)
                    if (loaded) loadedAny = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ${plugin.internalName}: ${e.message}")
            }
        }

        // Refresh UI
        if (downloadedAny || loadedAny || deletedPlugins.isNotEmpty()) {
            try {
                com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
            } catch (_: Throwable) {}
        }
    }

    // --- Shared utility methods ---

    fun editor(context: Context, isEditingAppSettings: Boolean = false): UltimaEditor {
        val editor: SharedPreferences.Editor = if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return UltimaEditor(editor)
    }


    fun getBackupFileKeys(backupFile: BackupFile): Set<String> {
        val keys = mutableSetOf<String>()
        backupFile.datastore.bool?.keys?.let { keys.addAll(it) }
        backupFile.datastore.int?.keys?.let { keys.addAll(it) }
        backupFile.datastore.float?.keys?.let { keys.addAll(it) }
        backupFile.datastore.long?.keys?.let { keys.addAll(it) }
        backupFile.datastore.stringSet?.keys?.let { keys.addAll(it) }
        backupFile.datastore.string?.keys?.let { keys.addAll(it) }
        backupFile.settings.bool?.keys?.let { keys.addAll(it) }
        backupFile.settings.int?.keys?.let { keys.addAll(it) }
        backupFile.settings.float?.keys?.let { keys.addAll(it) }
        backupFile.settings.long?.keys?.let { keys.addAll(it) }
        backupFile.settings.stringSet?.keys?.let { keys.addAll(it) }
        backupFile.settings.string?.keys?.let { keys.addAll(it) }
        return keys
    }

    fun isDynamicCategory(category: SyncCategory): Boolean {
        return category == SyncCategory.BOOKMARKS ||
               category == SyncCategory.RESUME_WATCHING ||
               category == SyncCategory.SEARCH_HISTORY
    }

    fun extractIdFromKey(key: String): Int? {
        val lower = key.lowercase()
        return when {
            lower.contains("download_header_cache") -> key.split("/").getOrNull(1)?.toIntOrNull()
            lower.contains("video_pos_dur") -> key.split("/").getOrNull(2)?.toIntOrNull()
            lower.contains("result_season") -> key.split("/").getOrNull(2)?.toIntOrNull()
            lower.contains("result_dub") -> key.split("/").getOrNull(2)?.toIntOrNull()
            lower.contains("result_episode") -> key.split("/").getOrNull(2)?.toIntOrNull()
            lower.contains("result_favorites_state_data") -> key.split("/").getOrNull(1)?.toIntOrNull()
            lower.contains("result_watch_state") -> key.split("/").getOrNull(1)?.toIntOrNull()
            lower.contains("result_resume_watching") -> key.split("/").getOrNull(1)?.toIntOrNull()
            else -> null
        }
    }

     private fun getSpecificKeyTimestamp(
        key: String,
        category: SyncCategory,
        stringMap: Map<String, String>?
    ): Long {
        if (stringMap == null) return 0L
        // If the key is a string key that directly contains a timestamp, extract it
        val directVal = stringMap[key]
        if (directVal != null) {
            val ts = extractTimestamp(directVal)
            if (ts > 0L) return ts
        }

        // If not found, try to find a related key with the same ID
        val id = extractIdFromKey(key) ?: return 0L
        
        // Look for favorites / resume watching / search history keys with this ID
        val relatedKeys = when (category) {
            SyncCategory.BOOKMARKS -> listOf("result_favorites_state_data/$id")
            SyncCategory.RESUME_WATCHING -> listOf("result_resume_watching/$id", "video_pos_dur/$id")
            else -> emptyList()
        }

        for (relKey in relatedKeys) {
            val v = stringMap[relKey]
            if (v != null) {
                val ts = extractTimestamp(v)
                if (ts > 0L) return ts
            }
        }

        // Fuzzy match for video_pos_dur if needed
        if (category == SyncCategory.RESUME_WATCHING) {
            stringMap.forEach { (k, v) ->
                if (k.contains("video_pos_dur") && k.contains("/$id")) {
                    val ts = extractTimestamp(v)
                    if (ts > 0L) return ts
                }
            }
        }

        return 0L
    }

    private fun <T> mergeCategoryMap(
        category: SyncCategory,
        local: Map<String, T>?,
        cloud: Map<String, T>?,
        localCategoryTs: Long,
        cloudPayloadTs: Long,
        localStrings: Map<String, String>?,
        cloudStrings: Map<String, String>?,
        isLocallyDirty: Boolean = false
    ): Map<String, T>? {
        if (local == null && cloud == null) return null

        val lastSyncedKeys = if (isDynamicCategory(category)) {
            UltimaStorageManager.getCategorySyncedKeys(category)
        } else {
            emptySet()
        }

        if (local == null) {
            val nonNullCloud = cloud ?: return null
            if (localCategoryTs == 0L || !isDynamicCategory(category) || lastSyncedKeys.isEmpty()) return nonNullCloud
            return nonNullCloud.filter { (key, _) ->
                val inLastSync = lastSyncedKeys.contains(key)
                if (!inLastSync) {
                    true
                } else {
                    val itemTs = getSpecificKeyTimestamp(key, category, cloudStrings)
                    itemTs > localCategoryTs
                }
            }
        }
        if (cloud == null) {
            val nonNullLocal = local
            if (localCategoryTs == 0L || !isDynamicCategory(category) || lastSyncedKeys.isEmpty()) return nonNullLocal
            return nonNullLocal.filter { (key, _) ->
                val inLastSync = lastSyncedKeys.contains(key)
                if (!inLastSync) {
                    true
                } else {
                    val itemTs = getSpecificKeyTimestamp(key, category, localStrings)
                    itemTs > localCategoryTs
                }
            }
        }

        val merged = HashMap<String, T>()

        // 1. Keys present in both or only in local
        for ((key, localVal) in local) {
            val cloudVal = cloud[key]
            if (cloudVal != null) {
                val localTs = getSpecificKeyTimestamp(key, category, localStrings)
                val cloudTs = getSpecificKeyTimestamp(key, category, cloudStrings)
                
                if (localTs > 0L || cloudTs > 0L) {
                    // Use individual timestamps if available (Dynamic Categories)
                    if (cloudTs > localTs) {
                        merged[key] = cloudVal
                    } else {
                        merged[key] = localVal
                    }
                } else {
                    // Fallback to category payload timestamps (Static Categories)
                    if (cloudPayloadTs > localCategoryTs && !isLocallyDirty) {
                        merged[key] = cloudVal
                    } else {
                        merged[key] = localVal
                    }
                }
            } else {
                // Key is in local but NOT in cloud
                val itemTs = getSpecificKeyTimestamp(key, category, localStrings)
                if (itemTs > 0L) {
                    // Dynamic category logic (item has timestamp)
                    if (localCategoryTs == 0L || lastSyncedKeys.isEmpty()) {
                        merged[key] = localVal
                    } else {
                        val inLastSync = lastSyncedKeys.contains(key)
                        if (!inLastSync) {
                            merged[key] = localVal
                        } else {
                            if (itemTs > localCategoryTs) {
                                merged[key] = localVal
                            }
                        }
                    }
                } else {
                    // Static category logic (no individual timestamp)
                    if (localCategoryTs == 0L) {
                        merged[key] = localVal
                    } else if (cloudPayloadTs > localCategoryTs && !isLocallyDirty && lastSyncedKeys.contains(key)) {
                        // Cloud is newer AND we previously synced this key.
                        // Since it's missing in cloud, the other device deleted it!
                        // drop it
                    } else {
                        // Either cloud is older, locally dirty, or this is a brand new local key (not in lastSyncedKeys)
                        merged[key] = localVal
                    }
                }
            }
        }

        // 2. Keys present only in cloud
        for ((key, cloudVal) in cloud) {
            if (!local.containsKey(key)) {
                val itemTs = getSpecificKeyTimestamp(key, category, cloudStrings)
                if (itemTs > 0L) {
                    // Dynamic category logic
                    if (localCategoryTs == 0L || lastSyncedKeys.isEmpty()) {
                        merged[key] = cloudVal
                    } else {
                        val inLastSync = lastSyncedKeys.contains(key)
                        if (!inLastSync) {
                            merged[key] = cloudVal
                        } else {
                            if (itemTs > localCategoryTs) {
                                merged[key] = cloudVal
                            }
                        }
                    }
                } else {
                    // Static category logic
                    if (localCategoryTs == 0L) {
                        merged[key] = cloudVal
                    } else if (cloudPayloadTs > localCategoryTs && !isLocallyDirty) {
                        // Cloud is newer and has a key we don't have, so we should accept it
                        merged[key] = cloudVal
                    } else {
                        // Cloud is older or local is dirty, drop it because it was deleted locally
                    }
                }
            }
        }

        return merged
    }

    fun mergeBackupFiles(local: BackupFile?, cloud: BackupFile?, localCategoryTs: Long, cloudPayloadTs: Long, isLocallyDirty: Boolean = false): BackupFile? {
        if (local == null) return cloud
        if (cloud == null) return local

        val sampleKey = local.datastore.string?.keys?.firstOrNull()
            ?: local.settings.string?.keys?.firstOrNull()
            ?: local.datastore.bool?.keys?.firstOrNull()
            ?: local.settings.bool?.keys?.firstOrNull()
            ?: cloud.datastore.string?.keys?.firstOrNull()
            ?: cloud.settings.string?.keys?.firstOrNull()

        val category = sampleKey?.let { classifyKey(it) } ?: SyncCategory.SETTINGS

        return BackupFile(
            datastore = mergeBackupVars(local.datastore, cloud.datastore, localCategoryTs, cloudPayloadTs, category, isLocallyDirty),
            settings = mergeBackupVars(local.settings, cloud.settings, localCategoryTs, cloudPayloadTs, category, isLocallyDirty)
        )
    }

    private fun mergeBackupVars(local: BackupVars, cloud: BackupVars, localCategoryTs: Long, cloudPayloadTs: Long, category: SyncCategory, isLocallyDirty: Boolean): BackupVars {
        return BackupVars(
            bool = mergeCategoryMap(category, local.bool, cloud.bool, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty),
            int = mergeCategoryMap(category, local.int, cloud.int, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty),
            float = mergeCategoryMap(category, local.float, cloud.float, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty),
            long = mergeCategoryMap(category, local.long, cloud.long, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty),
            string = mergeCategoryMap(category, local.string, cloud.string, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty),
            stringSet = mergeCategoryMap(category, local.stringSet, cloud.stringSet, localCategoryTs, cloudPayloadTs, local.string, cloud.string, isLocallyDirty)
        )
    }

    private fun extractTimestamp(json: String?): Long {
        if (json == null) return 0L
        try {
            val updateTimeMatch = "\"updateTime\":\\s*(\\d+)".toRegex().find(json)
            if (updateTimeMatch != null) {
                return updateTimeMatch.groupValues[1].toLong()
            }
            val latestUpdatedTimeMatch = "\"latestUpdatedTime\":\\s*(\\d+)".toRegex().find(json)
            if (latestUpdatedTimeMatch != null) {
                return latestUpdatedTimeMatch.groupValues[1].toLong()
            }
            val searchedAtMatch = "\"searchedAt\":\\s*(\\d+)".toRegex().find(json)
            if (searchedAtMatch != null) {
                return searchedAtMatch.groupValues[1].toLong()
            }
        } catch (_: Exception) {}
        return 0L
    }

    fun BackupVars.isEmpty(): Boolean {
        return bool.isNullOrEmpty() &&
               int.isNullOrEmpty() &&
               string.isNullOrEmpty() &&
               float.isNullOrEmpty() &&
               long.isNullOrEmpty() &&
               stringSet.isNullOrEmpty()
    }
}

