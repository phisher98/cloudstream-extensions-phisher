package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.*

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    // Track which categories have local changes pending push
    private val dirtyCategories = mutableSetOf<SyncCategory>()
    private val dirtyCategoriesLock = Any()

    // Guard against restore→backup loops
    @Volatile
    private var isRestoring = false

    private var dataPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var defaultPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pushJob: Job? = null

    companion object {
        private const val TAG = "UltimaSync"
        private const val PUSH_DEBOUNCE_MS = 5000L
        private const val POLL_INTERVAL_MS = 60_000L
    }

    // --- Category Dirty Tracking ---

    private fun markDirty(category: SyncCategory) {
        synchronized(dirtyCategoriesLock) {
            dirtyCategories.add(category)
        }
        scheduleDebouncedPush()
    }

    private fun markDirtyFromKey(key: String) {
        val category = UltimaBackupUtils.classifyKey(key) ?: return
        markDirty(category)
    }

    private fun consumeDirtyCategories(): Set<SyncCategory> {
        synchronized(dirtyCategoriesLock) {
            val copy = dirtyCategories.toSet()
            dirtyCategories.clear()
            return copy
        }
    }

    // --- Debounced Push ---

    private fun scheduleDebouncedPush() {
        val ctx = activity ?: return
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return

        pushJob?.cancel()
        pushJob = CoroutineScope(Dispatchers.IO).launch {
            delay(PUSH_DEBOUNCE_MS)
            pushDirtyCategories(ctx)
        }
    }

    private suspend fun pushDirtyCategories(context: Context) {
        if (isRestoring) {
            Log.d(TAG, "Skipping push — restore in progress")
            return
        }

        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return

        val dirty = consumeDirtyCategories()
        if (dirty.isEmpty()) return

        val resumeWatching = getResumeWatching()

        for (category in dirty) {
            if (!creds.isCategoryEnabled(category)) continue

            try {
                val backup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching)
                if (backup == null) {
                    Log.d(TAG, "No data for category ${category.key}, skipping push")
                    continue
                }

                val data = backup.toJson()
                val hash = UltimaBackupUtils.computeHash(data)
                val localHash = UltimaStorageManager.getCategoryHash(category)

                if (hash == localHash) {
                    Log.d(TAG, "Category ${category.key} unchanged (hash=$hash), skipping push")
                    continue
                }

                val success = UltimaSettingsSyncUtils.pushCategory(context, category, data, hash)
                if (success) {
                    Log.d(TAG, "Pushed category ${category.key} (hash=$hash)")
                } else {
                    Log.e(TAG, "Failed to push category ${category.key}")
                    // Re-mark as dirty for retry
                    synchronized(dirtyCategoriesLock) {
                        dirtyCategories.add(category)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing category ${category.key}: ${e.message}")
            }
        }
    }

    // --- Restore Logic ---

    private suspend fun pullChangedCategories(context: Context, force: Boolean = false): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn() || !creds.restoreDevice) return false

        val manifest = UltimaSettingsSyncUtils.fetchManifest(context)
        if (manifest == null) {
            Log.d(TAG, "No manifest found in cloud")
            return false
        }

        var restoredAny = false
        isRestoring = true

        try {
            for (category in SyncCategory.entries) {
                if (!creds.isCategoryEnabled(category)) continue

                val cloudMeta = manifest.getMeta(category) ?: continue
                val localTs = UltimaStorageManager.getCategoryTimestamp(category)

                if (!force && cloudMeta.ts <= localTs) continue

                Log.d(TAG, "Category ${category.key} changed: cloud=${cloudMeta.ts}, local=$localTs, device=${cloudMeta.device}")

                try {
                    val payload = UltimaSettingsSyncUtils.fetchCategory(context, category) ?: continue
                    if (payload.data.isBlank()) continue

                    val backupFile = mapper.readValue<BackupFile>(payload.data)

                    when (category) {
                        SyncCategory.EXTENSIONS -> {
                            // Extensions need special download/load handling
                            UltimaBackupUtils.restoreCategory(context, category, backupFile)
                            UltimaBackupUtils.restoreExtensionsCategory(context, backupFile)
                        }
                        SyncCategory.BOOKMARKS -> {
                            UltimaBackupUtils.restoreCategory(context, category, backupFile)
                            withContext(Dispatchers.Main) {
                                try { MainActivity.bookmarksUpdatedEvent(true) } catch (_: Throwable) {}
                            }
                        }
                        else -> {
                            UltimaBackupUtils.restoreCategory(context, category, backupFile)
                        }
                    }

                    // Update local state
                    UltimaStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                    UltimaStorageManager.setCategoryHash(category, cloudMeta.hash)
                    restoredAny = true

                    Log.d(TAG, "Restored category ${category.key} from ${cloudMeta.device}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring category ${category.key}: ${e.message}")
                }
            }
        } finally {
            isRestoring = false
        }

        if (restoredAny) {
            reload()
        }

        return restoredAny
    }

    // --- v1 → v2 Migration ---

    private suspend fun migrateFromV1(context: Context) {
        if (UltimaStorageManager.syncV2Migrated) return

        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        Log.d(TAG, "Checking for v1 data to migrate...")

        try {
            val oldData = UltimaSettingsSyncUtils.fetchSharedData(context)
            if (oldData != null && !oldData.syncedData.isNullOrBlank()) {
                Log.d(TAG, "Found v1 shared_data, migrating to v2 categories...")

                withContext(Dispatchers.Main) {
                    showToast("Migrating sync data to v2...")
                }

                val fullBackup = mapper.readValue<BackupFile>(oldData.syncedData!!)
                val resumeWatching = getResumeWatching()

                // Split and push each category
                for (category in SyncCategory.entries) {
                    if (!creds.isCategoryEnabled(category)) continue

                    val categoryBackup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching)
                    if (categoryBackup != null) {
                        val data = categoryBackup.toJson()
                        val hash = UltimaBackupUtils.computeHash(data)
                        UltimaSettingsSyncUtils.pushCategory(context, category, data, hash)
                        Log.d(TAG, "Migrated category ${category.key}")
                    }
                }

                // Clean up old data
                UltimaSettingsSyncUtils.deleteSharedData(context)
                Log.d(TAG, "Deleted old v1 shared_data")

                withContext(Dispatchers.Main) {
                    showToast("Sync migration complete!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "v1→v2 migration error: ${e.message}")
        }

        UltimaStorageManager.syncV2Migrated = true
    }

    // --- Plugin Lifecycle ---

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        registerMainAPI(Ultima(this))

        UltimaStorageManager.currentMetaProviders.forEach { metaProvider ->
            when (metaProvider.first) {
                else -> {}
            }
        }

        openSettings = { ctx ->
            val act = ctx as? AppCompatActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val frag = UltimaSettings(this)
                frag.show(act.supportFragmentManager, "UltimaSettingsDialog")
            } else {
                Log.e("Plugin", "Activity is not valid anymore, cannot show settings dialog")
            }
        }

        // --- Sync initialization ---
        val creds = UltimaStorageManager.appSettingsSyncCreds
        if (creds != null && creds.isLoggedIn()) {
            // Startup: migrate + pull changed categories
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    migrateFromV1(context)

                    if (creds.restoreDevice) {
                        val restored = pullChangedCategories(context)
                        if (restored) {
                            withContext(Dispatchers.Main) {
                                showToast("Synced from cloud")
                            }
                        }
                    }

                    // If this is a fresh setup with no cloud data, do initial push
                    if (creds.backupDevice) {
                        val manifest = UltimaSettingsSyncUtils.fetchManifest(context)
                        if (manifest == null) {
                            Log.d(TAG, "No manifest in cloud — performing initial push")
                            pushAllCategories(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Startup sync failed: ${e.message}")
                }
            }
        }

        // Register preference change listeners for category-level dirty tracking
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && !isRestoring) {
                val category = UltimaBackupUtils.classifyKey(key)
                if (category != null) {
                    Log.d(TAG, "Pref changed: $key → category ${category.key}")
                    markDirty(category)
                }
            }
        }
        dataPrefsListener = listener
        defaultPrefsListener = listener
        try {
            context.getSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            context.getDefaultSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            Log.d(TAG, "Registered preference change listeners")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register preference change listeners: ${e.message}")
        }

        // Event hooks for specific categories
        MainActivity.bookmarksUpdatedEvent += { _: Boolean ->
            if (!isRestoring) markDirty(SyncCategory.BOOKMARKS)
        }

        // Periodic manifest poll loop
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    val currentCreds = UltimaStorageManager.appSettingsSyncCreds
                    if (currentCreds != null && currentCreds.isLoggedIn()) {
                        if (currentCreds.restoreDevice) {
                            pullChangedCategories(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic poll error: ${e.message}")
                }
            }
        }
    }

    // --- Force push all categories (used for initial setup + Sync Now) ---

    suspend fun pushAllCategories(context: Context) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        val resumeWatching = getResumeWatching()

        for (category in SyncCategory.entries) {
            if (!creds.isCategoryEnabled(category)) continue

            try {
                val backup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching) ?: continue
                val data = backup.toJson()
                val hash = UltimaBackupUtils.computeHash(data)
                UltimaSettingsSyncUtils.pushCategory(context, category, data, hash)
                Log.d(TAG, "Force-pushed category ${category.key}")
            } catch (e: Exception) {
                Log.e(TAG, "Error force-pushing ${category.key}: ${e.message}")
            }
        }
    }

    // --- Force pull all categories (used for Sync Now) ---

    suspend fun forcePullAllCategories(context: Context): Boolean {
        return pullChangedCategories(context, force = true)
    }

    fun reload() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                afterPluginsLoadedEvent.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "afterPluginsLoadedEvent invoke failed: ${e.message}")
            }
        }
    }
}