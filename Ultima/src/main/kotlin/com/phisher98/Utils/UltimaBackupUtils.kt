package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import android.app.Activity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.api.Log
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.RepositoryManager.convertRawGitUrl
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.mapper



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
    )

    private fun String.isResume(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private fun shouldBackupKey(key: String, creds: AppSettingsSyncCreds): Boolean {
        val lowerKey = key.lowercase()
        if (lowerKey.contains("result_favorites_state_data") || lowerKey.contains("result_watch_state")) {
            return creds.backupBookmarks
        }
        if (lowerKey.contains("result_resume_watching") || lowerKey.contains("video_pos_dur") || 
            lowerKey.contains("download_header_cache") || lowerKey.contains("result_season") || 
            lowerKey.contains("result_dub") || lowerKey.contains("result_episode")) {
            return creds.backupResumeWatching
        }
        if (lowerKey.contains("search_history")) {
            return creds.backupSearchHistory
        }
        if (lowerKey.contains("plugins_key_local")) {
            return false
        }
        if (lowerKey.contains("plugins_key") ||
            lowerKey.contains("plugins_repositories") ||
            lowerKey.contains("repositories") ||
            lowerKey.contains("ultima_extensions_list") || lowerKey.contains("ultima_current_meta_providers") ||
            lowerKey.contains("ultima_current_media_providers") ||
            key.equals(REPOSITORIES_KEY, ignoreCase = true)) {
            return creds.backupExtensions
        }
        if (lowerKey.contains("sub") || lowerKey.contains("caption")) {
            return creds.backupSubtitles
        }
        if (lowerKey.contains("player")) {
            return creds.backupPlayer
        }
        if (lowerKey.contains("theme") || lowerKey.contains("accent") || lowerKey.contains("color")) {
            return creds.backupTheme
        }
        if (lowerKey.contains("layout") || lowerKey.contains("home") || lowerKey.contains("ui") || 
            lowerKey.contains("poster") || lowerKey.contains("ext_name_on_home")) {
            return creds.backupLayout
        }
        if (lowerKey.contains("download")) {
            return creds.backupDownloads
        }
        return creds.backupGeneral
    }

    private fun shouldRestoreKey(key: String, creds: AppSettingsSyncCreds): Boolean {
        val lowerKey = key.lowercase()
        if (lowerKey.contains("result_favorites_state_data") || lowerKey.contains("result_watch_state")) {
            return creds.restoreBookmarks
        }
        if (lowerKey.contains("result_resume_watching") || lowerKey.contains("video_pos_dur") || 
            lowerKey.contains("download_header_cache") || lowerKey.contains("result_season") || 
            lowerKey.contains("result_dub") || lowerKey.contains("result_episode")) {
            return creds.restoreResumeWatching
        }
        if (lowerKey.contains("search_history")) {
            return creds.restoreSearchHistory
        }
        if (lowerKey.contains("plugins_key_local")) {
            return false
        }
        if (lowerKey.contains("plugins_key") ||
            lowerKey.contains("plugins_repositories") ||
            lowerKey.contains("repositories") ||
            lowerKey.contains("ultima_extensions_list") || lowerKey.contains("ultima_current_meta_providers") ||
            lowerKey.contains("ultima_current_media_providers") ||
            key.equals(REPOSITORIES_KEY, ignoreCase = true)) {
            return creds.restoreExtensions
        }
        if (lowerKey.contains("sub") || lowerKey.contains("caption")) {
            return creds.restoreSubtitles
        }
        if (lowerKey.contains("player")) {
            return creds.restorePlayer
        }
        if (lowerKey.contains("theme") || lowerKey.contains("accent") || lowerKey.contains("color")) {
            return creds.restoreTheme
        }
        if (lowerKey.contains("layout") || lowerKey.contains("home") || lowerKey.contains("ui") || 
            lowerKey.contains("poster") || lowerKey.contains("ext_name_on_home")) {
            return creds.restoreLayout
        }
        if (lowerKey.contains("download")) {
            return creds.restoreDownloads
        }
        return creds.restoreGeneral
    }

    private fun String.isBackup(resumeWatching: List<DataStoreHelper.ResumeWatchingResult>? = null): Boolean {
        var check = !nonTransferableKeys.any { this.contains(it) }
        if (check) {
            if (this.contains("download_header_cache")) {
                val id = this.split("/").getOrNull(1)?.toIntOrNull()
                check = id?.let { intId ->
                    resumeWatching?.any { if (it.parentId != null) it.parentId == intId else it.id == intId } == true
                } ?: false
            } else if (this.contains("video_pos_dur")) {
                val id = this.split("/").getOrNull(2)?.toIntOrNull()
                check = id?.let { intId ->
                    resumeWatching?.any { it.id == intId } == true
                } ?: false
            } else if (this.contains("result_season") || this.contains("result_dub") || this.contains("result_episode")) {
                val id = this.split("/").getOrNull(2)?.toIntOrNull()
                check = id?.let { intId ->
                    resumeWatching?.any { it.parentId == intId } == true
                } ?: false
            }
        }
        
        return check
    }

    @Suppress("UNCHECKED_CAST")
    fun getBackup(context: Context?, resumeWatching: List<DataStoreHelper.ResumeWatchingResult>?): BackupFile? {
        if (context == null) return null

        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        val allData = context.getSharedPrefs().all.filter { it.key.isBackup(resumeWatching) && shouldBackupKey(it.key, creds) }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isBackup(resumeWatching) && shouldBackupKey(it.key, creds) }

        val allDataSorted = BackupVars(
            allData.filter { it.value is Boolean } as? Map<String, Boolean>,
            allData.filter { it.value is Int } as? Map<String, Int>,
            allData.filter { it.value is String } as? Map<String, String>,
            allData.filter { it.value is Float } as? Map<String, Float>,
            allData.filter { it.value is Long } as? Map<String, Long>,
            allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val allSettingsSorted = BackupVars(
            allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettings.filter { it.value is Int } as? Map<String, Int>,
            allSettings.filter { it.value is String } as? Map<String, String>,
            allSettings.filter { it.value is Float } as? Map<String, Float>,
            allSettings.filter { it.value is Long } as? Map<String, Long>,
            allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        return BackupFile(
            allDataSorted,
            allSettingsSorted
        )
    }

    fun editor(context: Context, isEditingAppSettings: Boolean = false): UltimaEditor {
        val editor: SharedPreferences.Editor = if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return UltimaEditor(editor)
    }

    fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false
    ) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        val editor2 = editor(this, isEditingAppSettings)
        map?.forEach {
            if (it.key.isResume() && shouldRestoreKey(it.key, creds)) {
                editor2.setKeyRaw(it.key, it.value)
            }
        }
        editor2.apply()
    }

    private fun forceConvertRawGitUrl(url: String): String {
        val ghRegex = Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)/(.*)$")
        val match = ghRegex.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    suspend fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) {
        if (context == null) return

        val creds = UltimaStorageManager.appSettingsSyncCreds ?: AppSettingsSyncCreds()

        // 1. Restore standard settings first (excluding PLUGINS_KEY and REPOSITORIES_KEY) to register repositories, bookmarks, etc.
        if (restoreSettings) {
            val filteredSettingsBool = backupFile.settings.bool
            val filteredSettingsInt = backupFile.settings.int
            val filteredSettingsString = backupFile.settings.string?.filterKeys { 
                !it.equals("PLUGINS_KEY", ignoreCase = true) &&
                !it.equals(REPOSITORIES_KEY, ignoreCase = true) &&
                !it.equals("plugins_repositories", ignoreCase = true) &&
                !it.equals("repositories", ignoreCase = true)
            }
            val filteredSettingsFloat = backupFile.settings.float
            val filteredSettingsLong = backupFile.settings.long
            val filteredSettingsStringSet = backupFile.settings.stringSet

            context.restoreMap(filteredSettingsBool, true)
            context.restoreMap(filteredSettingsInt, true)
            context.restoreMap(filteredSettingsString, true)
            context.restoreMap(filteredSettingsFloat, true)
            context.restoreMap(filteredSettingsLong, true)
            context.restoreMap(filteredSettingsStringSet, true)
        }

        if (restoreDataStore) {
            val filteredDataStoreBool = backupFile.datastore.bool
            val filteredDataStoreInt = backupFile.datastore.int
            val filteredDataStoreString = backupFile.datastore.string?.filterKeys {
                !it.equals("PLUGINS_KEY", ignoreCase = true) && 
                !it.equals("PLUGINS_KEY_LOCAL", ignoreCase = true) &&
                !it.equals(REPOSITORIES_KEY, ignoreCase = true) &&
                !it.equals("plugins_repositories", ignoreCase = true) &&
                !it.equals("repositories", ignoreCase = true)
            }
            val filteredDataStoreFloat = backupFile.datastore.float
            val filteredDataStoreLong = backupFile.datastore.long
            val filteredDataStoreStringSet = backupFile.datastore.stringSet

            context.restoreMap(filteredDataStoreBool)
            context.restoreMap(filteredDataStoreInt)
            context.restoreMap(filteredDataStoreString)
            context.restoreMap(filteredDataStoreFloat)
            context.restoreMap(filteredDataStoreLong)
            context.restoreMap(filteredDataStoreStringSet)
        }

        // 1.5. If restoreExtensions is enabled, merge repositories with the incoming backup to prevent overwriting local repos
        if (creds.restoreExtensions) {
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
                        // Merge current and incoming, keeping all unique by url
                        val mergedRepos = (currentRepos + incomingRepos).distinctBy { it.url.trim().lowercase() }
                        
                        val mergedReposJson = mergedRepos.toTypedArray().toJson()
                        
                        // Write to all possible keys to be absolutely sure
                        context.getDefaultSharedPrefs().edit()
                            .putString(REPOSITORIES_KEY, mergedReposJson)
                            .putString("plugins_repositories", mergedReposJson)
                            .putString("repositories", mergedReposJson)
                            .apply()
                            
                        context.getSharedPrefs().edit()
                            .putString(REPOSITORIES_KEY, mergedReposJson)
                            .putString("plugins_repositories", mergedReposJson)
                            .putString("repositories", mergedReposJson)
                            .apply()
                            
                        setKey(REPOSITORIES_KEY, mergedRepos.toTypedArray())
                        try {
                            setKey("plugins_repositories", mergedRepos.toTypedArray())
                            setKey("repositories", mergedRepos.toTypedArray())
                        } catch (e: Exception) {
                            Log.w("UltimaSync", "Optional setKey failed: ${e.message}")
                        }
                        
                        Log.d("UltimaSync", "Merged and restored repositories successfully. Current: ${currentRepos.size}, Incoming: ${incomingRepos.size}, Merged: ${mergedRepos.size}")
                    } catch (e: Exception) {
                        Log.e("UltimaSync", "Failed to merge repositories: ${e.message}")
                        // Fallback: write incoming repos directly to all possible keys
                        context.getDefaultSharedPrefs().edit()
                            .putString(REPOSITORIES_KEY, repoValue)
                            .putString("plugins_repositories", repoValue)
                            .putString("repositories", repoValue)
                            .apply()
                        context.getSharedPrefs().edit()
                            .putString(REPOSITORIES_KEY, repoValue)
                            .putString("plugins_repositories", repoValue)
                            .putString("repositories", repoValue)
                            .apply()
                        try {
                            val repoArray = mapper.readValue<Array<RepositoryData>>(repoValue)
                            setKey(REPOSITORIES_KEY, repoArray)
                            setKey("plugins_repositories", repoArray)
                            setKey("repositories", repoArray)
                        } catch (e2: Exception) {
                            Log.e("UltimaSync", "Failed fallback repositories restore: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UltimaSync", "Failed to restore repositories directly: ${e.message}")
            }
        }

        // 2. Query repositories and download missing extensions
        if (creds.restoreExtensions) {
            val pluginsJson = backupFile.datastore.string?.entries?.find { it.key.equals("PLUGINS_KEY", ignoreCase = true) }?.value
                ?: backupFile.settings.string?.entries?.find { it.key.equals("PLUGINS_KEY", ignoreCase = true) }?.value
            if (!pluginsJson.isNullOrBlank()) {
                val pluginsList = try {
                    com.lagradost.cloudstream3.mapper.readValue<Array<PluginData>>(pluginsJson)
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Failed to parse plugins JSON: ${e.message}")
                    null
                }

                if (pluginsList != null) {
                    // Fetch all online plugins from all configured repositories
                    val allOnlinePlugins = mutableListOf<Pair<String, SitePlugin>>()
                    val repositories = RepositoryManager.getRepositories()
                    for (repo in repositories) {
                        try {
                            val repoPlugins = RepositoryManager.getRepoPlugins(repo.url)
                            if (repoPlugins != null) {
                                allOnlinePlugins.addAll(repoPlugins)
                            }
                        } catch (e: Exception) {
                            Log.e("UltimaSync", "Failed to fetch plugins for repository ${repo.url}: ${e.message}")
                        }
                    }

                    val currentLocalOnlinePlugins = PluginManager.getPluginsOnline()
                    val incomingInternalNames = pluginsList.map { it.internalName }.toSet()
                    val deletedPlugins = currentLocalOnlinePlugins.filter { 
                        !incomingInternalNames.contains(it.internalName) && 
                        !it.internalName.equals("Ultima", ignoreCase = true) 
                    }

                    val updatedPluginsList = mutableListOf<PluginData>()
                    val newlyDownloadedPlugins = mutableSetOf<String>()
                    var downloadedAny = false

                    for (plugin in pluginsList) {
                        // Explicit safeguard: skip Ultima itself
                        if (plugin.internalName.equals("Ultima", ignoreCase = true)) {
                            continue
                        }

                        // Try to find the plugin in the fetched repository plugins
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

                        // Determine target download URL
                        val targetUrl = match?.second?.url ?: plugin.url

                        if (localFile.exists() && localFile.length() > 0) {
                            Log.d("UltimaSync", "Plugin file already exists: ${localFile.absolutePath}")
                            // Just update path
                            updatedPluginsList.add(plugin.copy(filePath = localFile.absolutePath))
                        } else {
                            // If the file does not exist, download it
                            if (!targetUrl.isNullOrBlank()) {
                                // Apply conversion for raw github blocking
                                val downloadUrl = forceConvertRawGitUrl(targetUrl)
                                Log.d("UltimaSync", "Downloading plugin: ${plugin.internalName} from $downloadUrl")
                                try {
                                    val tempFile = File.createTempFile(plugin.internalName, ".tmp", context.cacheDir)
                                    try {
                                        val response = com.lagradost.cloudstream3.app.get(downloadUrl)
                                        if (response.code == 200) {
                                            val body = response.okhttpResponse.body
                                            tempFile.outputStream().use { fos ->
                                                body.byteStream().use { bis ->
                                                    bis.copyTo(fos)
                                                }
                                            }
                                            if (tempFile.exists() && tempFile.length() > 0) {
                                                localFile.parentFile?.mkdirs()
                                                tempFile.copyTo(localFile, overwrite = true)
                                                Log.d("UltimaSync", "Downloaded ${plugin.internalName} successfully to ${localFile.absolutePath} (${tempFile.length()} bytes)")
                                                downloadedAny = true
                                                newlyDownloadedPlugins.add(plugin.internalName)
                                                updatedPluginsList.add(plugin.copy(filePath = localFile.absolutePath, url = targetUrl))
                                            } else {
                                                Log.e("UltimaSync", "Downloaded file is empty (0 bytes) for ${plugin.internalName}")
                                            }
                                        } else {
                                            Log.e("UltimaSync", "Failed to download ${plugin.internalName}: HTTP ${response.code}")
                                        }
                                    } finally {
                                        if (tempFile.exists()) {
                                            tempFile.delete()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("UltimaSync", "Error downloading plugin ${plugin.internalName}: ${e.message}")
                                }
                            } else {
                                Log.e("UltimaSync", "No download URL available for missing plugin: ${plugin.internalName}")
                            }

                            // Final safeguard: if download failed, ensure we delete any 0 KB file that might be there
                            if (localFile.exists() && localFile.length() == 0L) {
                                try {
                                    localFile.delete()
                                    Log.d("UltimaSync", "Cleaned up 0 KB file for ${plugin.internalName}")
                                } catch (e: Exception) {
                                    Log.e("UltimaSync", "Failed to delete 0 KB file for ${plugin.internalName}: ${e.message}")
                                }
                            }
                        }
                    }

                    // Unload and delete any plugins that were deleted on the other device
                    for (plugin in deletedPlugins) {
                        try {
                            Log.d("UltimaSync", "Unloading and deleting plugin ${plugin.internalName} since it was deleted on another device")
                            PluginManager.unloadPlugin(plugin.filePath)
                            val file = File(plugin.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("UltimaSync", "Error deleting plugin ${plugin.internalName}: ${e.message}")
                        }
                    }

                    // Save the updated list back into both SharedPreferences and Datastore
                    val updatedPluginsJson = updatedPluginsList.toTypedArray().toJson()
                    context.getSharedPrefs().edit().putString("PLUGINS_KEY", updatedPluginsJson).apply()
                    try {
                        setKey("PLUGINS_KEY", updatedPluginsList.toTypedArray())
                        Log.d("UltimaSync", "Saved PLUGINS_KEY to Datastore successfully")
                    } catch (e: Exception) {
                        Log.e("UltimaSync", "Failed to save PLUGINS_KEY to Datastore: ${e.message}")
                    }

                    // Load/Reload all plugins in the list to make sure they are active
                    var loadedAny = false
                    for (plugin in updatedPluginsList) {
                        try {
                            val isLoaded = PluginManager.plugins.containsKey(plugin.filePath)
                            val isNew = newlyDownloadedPlugins.contains(plugin.internalName)

                            // If the plugin is already loaded, but we just downloaded a new version, unload it first to force hot-reload
                            if (isLoaded && isNew) {
                                Log.d("UltimaSync", "Unloading old version of plugin ${plugin.internalName} for hot-reload")
                                PluginManager.unloadPlugin(plugin.filePath)
                            }

                            // Load if it's not loaded, or if we just unloaded it (meaning isNew is true)
                            if (!isLoaded || isNew) {
                                val apiName = plugin.internalName.replace("provider", "", ignoreCase = true)
                                // Try loading with both the stripped API name and original internalName to bypass loadSinglePlugin mismatch
                                val loaded = PluginManager.loadSinglePlugin(context, apiName) || PluginManager.loadSinglePlugin(context, plugin.internalName)
                                Log.d("UltimaSync", "Loaded plugin ${plugin.internalName} directly: $loaded")
                                if (loaded) {
                                    loadedAny = true
                                }
                            } else {
                                Log.d("UltimaSync", "Plugin ${plugin.internalName} is already loaded and not updated, skipping load")
                            }
                        } catch (e: Exception) {
                            Log.e("UltimaSync", "Error loading plugin ${plugin.internalName}: ${e.message}")
                        }
                    }

                    // Refresh UI if we downloaded, loaded, or deleted any plugins
                    if (downloadedAny || loadedAny || deletedPlugins.isNotEmpty()) {
                        try {
                            com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
                        } catch (e: Throwable) {
                            Log.w("UltimaSync", "afterPluginsLoadedEvent invoke failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
