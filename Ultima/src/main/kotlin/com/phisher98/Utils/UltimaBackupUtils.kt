package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs

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
        if (lowerKey.contains("plugins_key") || lowerKey.contains("plugins_key_local") ||
            lowerKey.contains("ultima_extensions_list") || lowerKey.contains("ultima_current_meta_providers") ||
            lowerKey.contains("ultima_current_media_providers")) {
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
        if (lowerKey.contains("plugins_key") || lowerKey.contains("plugins_key_local") ||
            lowerKey.contains("ultima_extensions_list") || lowerKey.contains("ultima_current_meta_providers") ||
            lowerKey.contains("ultima_current_media_providers")) {
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

    fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) {
        if (context == null) return
        if (restoreSettings) {
            context.restoreMap(backupFile.settings.bool, true)
            context.restoreMap(backupFile.settings.int, true)
            context.restoreMap(backupFile.settings.string, true)
            context.restoreMap(backupFile.settings.float, true)
            context.restoreMap(backupFile.settings.long, true)
            context.restoreMap(backupFile.settings.stringSet, true)
        }
        if (restoreDataStore) {
            context.restoreMap(backupFile.datastore.bool)
            context.restoreMap(backupFile.datastore.int)
            context.restoreMap(backupFile.datastore.string)
            context.restoreMap(backupFile.datastore.float)
            context.restoreMap(backupFile.datastore.long)
            context.restoreMap(backupFile.datastore.stringSet)
        }
    }
}
