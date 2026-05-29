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
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.mapper
import java.security.MessageDigest
import androidx.core.content.edit


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
        System.gc()
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
        return !nonTransferableKeys.any { lower.contains(it) }
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
    }

    private fun restoreBackupVars(context: Context, category: SyncCategory, vars: BackupVars, isSettings: Boolean) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        val prefs = if (isSettings) context.getDefaultSharedPrefs() else context.getSharedPrefs()
        val editor = editor(context, isSettings)
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

        // Fetch all online plugins from configured repos
        val allOnlinePlugins = mutableListOf<Pair<String, SitePlugin>>()
        val repositories = RepositoryManager.getRepositories()
        for (repo in repositories) {
            try {
                val repoPlugins = RepositoryManager.getRepoPlugins(repo.url)
                if (repoPlugins != null) allOnlinePlugins.addAll(repoPlugins)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch plugins for repo ${repo.url}: ${e.message}")
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
        val newlyDownloaded = mutableSetOf<String>()
        var downloadedAny = false

        for (plugin in pluginsList) {
            if (plugin.internalName.equals("Ultima", ignoreCase = true)) continue

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
                updatedPluginsList.add(plugin.copy(filePath = localFile.absolutePath))
            } else {
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
                                    downloadedAny = true
                                    newlyDownloaded.add(plugin.internalName)
                                    updatedPluginsList.add(plugin.copy(filePath = localFile.absolutePath, url = targetUrl))
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
        context.getSharedPrefs().edit().putString("PLUGINS_KEY", updatedJson).apply()
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

    // --- Legacy v1 methods (kept for migration) ---

    @Suppress("UNCHECKED_CAST")
    fun getBackup(context: Context?, resumeWatching: List<DataStoreHelper.ResumeWatchingResult>?): BackupFile? {
        if (context == null) return null

        val allData = context.getSharedPrefs().all.filter { it.key.isTransferable() }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isTransferable() }

        return BackupFile(
            datastore = buildBackupVars(allData),
            settings = buildBackupVars(allSettings)
        )
    }

    fun mergeBackupFiles(local: BackupFile?, cloud: BackupFile?): BackupFile? {
        if (local == null) return cloud
        if (cloud == null) return local

        return BackupFile(
            datastore = mergeBackupVars(local.datastore, cloud.datastore),
            settings = mergeBackupVars(local.settings, cloud.settings)
        )
    }

    private fun mergeBackupVars(local: BackupVars, cloud: BackupVars): BackupVars {
        return BackupVars(
            bool = mergeMaps(local.bool, cloud.bool),
            int = mergeMaps(local.int, cloud.int),
            float = mergeMaps(local.float, cloud.float),
            long = mergeMaps(local.long, cloud.long),
            string = mergeStringMaps(local.string, cloud.string),
            stringSet = mergeMaps(local.stringSet, cloud.stringSet)
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
        } catch (e: Exception) {}
        return 0L
    }

    private fun mergeStringMaps(local: Map<String, String>?, cloud: Map<String, String>?): Map<String, String>? {
        if (local == null) return cloud
        if (cloud == null) return local
        val merged = HashMap<String, String>(local)
        
        for ((key, cloudVal) in cloud) {
            val localVal = local[key]
            if (localVal == null) {
                merged[key] = cloudVal
            } else {
                val cloudTs = extractTimestamp(cloudVal)
                val localTs = extractTimestamp(localVal)
                
                if ((cloudTs == 0L && localTs == 0L) || cloudTs > localTs) {
                    merged[key] = cloudVal
                }
            }
        }
        return merged
    }

    private fun <K, V> mergeMaps(local: Map<K, V>?, cloud: Map<K, V>?): Map<K, V>? {
        if (local == null) return cloud
        if (cloud == null) return local
        val merged = HashMap<K, V>(local)
        merged.putAll(cloud)
        return merged
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

