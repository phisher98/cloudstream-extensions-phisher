package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.lagradost.cloudstream3.CloudStreamApp

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    // Managed coroutine scope — all coroutines tied to plugin lifecycle
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track which categories have local changes pending push
    private val dirtyCategories = mutableSetOf<SyncCategory>()
    private val dirtyCategoriesLock = Any()

    // Guard against restore→backup loops
    @Volatile
    private var isRestoring = false
    @Volatile
    private var restoringUntil = 0L
    private val RESTORE_GUARD_MS = 5_000L  // 5s guard after restore completes to cover async pref callbacks
    private val sseLock = Any()

    // Guard against concurrent pull operations
    private val pullMutex = Mutex()
    private val syncMutex = Mutex()  // Prevents push and pull from overlapping

    private var dataPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var defaultPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pushJob: Job? = null
    private var ssePullJob: Job? = null  // Debounced SSE pull
    private var sseCall: okhttp3.Call? = null
    @Volatile
    private var isSseConnected = false
    private var lastSseSyncKey: String? = null
    private var lastSseUrl: String? = null

    // SSE exponential backoff state
    @Volatile
    private var sseRetryCount = 0
    private val SSE_MAX_BACKOFF_MS = 60_000L
    private val SSE_BASE_DELAY_MS = 5_000L

    // Track our own pushes to ignore the resulting SSE events
    @Volatile
    private var lastPushTimestamp = 0L

    companion object {
        private const val TAG = "UltimaSync"
        private const val PUSH_DEBOUNCE_MS = 2_000L  // 2s debounce to batch changes and prevent rapid-fire pushes
        private const val SSE_PULL_DEBOUNCE_MS = 3_000L  // 3s debounce for SSE-triggered pulls
        private const val IGNORE_OWN_PUSH_MS = 5_000L  // Ignore SSE events within 5s of our own push
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
        val ctx = CloudStreamApp.context ?: activity ?: return
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return

        pushJob?.cancel()
        pushJob = pluginScope.launch {
            delay(PUSH_DEBOUNCE_MS)
            syncMutex.withLock {
                mergeAndSyncAllCategories(ctx)
            }
        }
    }

    // --- Restore Logic ---

    private suspend fun pullChangedCategories(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn() || !creds.restoreDevice) return false

        // Prevent concurrent pull operations
        if (!pullMutex.tryLock()) {
            Log.d(TAG, "Pull already in progress, skipping")
            return false
        }

        try {
            val manifest = UltimaSettingsSyncUtils.fetchManifest(appContext)
            if (manifest == null) {
                Log.d(TAG, "No manifest found in cloud")
                return false
            }

            // Determine which categories need updating
            val categoriesToFetch = mutableListOf<Pair<SyncCategory, SyncCategoryMeta>>()
            for (category in SyncCategory.entries) {
                if (!creds.isRestoreEnabled(category)) continue
                val cloudMeta = manifest.getMeta(category) ?: continue
                val localTs = UltimaStorageManager.getCategoryTimestamp(category)
                if (!force && cloudMeta.ts <= localTs) continue
                Log.d(TAG, "Category ${category.key} changed: cloud=${cloudMeta.ts}, local=$localTs, device=${cloudMeta.device}")
                categoriesToFetch.add(category to cloudMeta)
            }

            if (categoriesToFetch.isEmpty()) return false

            // Fetch all changed category payloads in PARALLEL
            val fetchedPayloads = coroutineScope {
                categoriesToFetch.map { (category, meta) ->
                    async(Dispatchers.IO) {
                        try {
                            val payload = UltimaSettingsSyncUtils.fetchCategory(appContext, category)
                            if (payload != null && payload.data.isNotBlank()) {
                                Triple(category, meta, payload)
                            } else null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching category ${category.key}: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (fetchedPayloads.isEmpty()) return false

            var restoredAny = false
            isRestoring = true

            try {
                // Restore categories sequentially (SharedPreferences is not thread-safe)
                for ((category, cloudMeta, payload) in fetchedPayloads) {
                    try {
                        val backupFile = mapper.readValue<BackupFile>(payload.data)

                        when (category) {
                            SyncCategory.EXTENSIONS -> {
                                UltimaBackupUtils.restoreCategory(appContext, category, backupFile)
                                UltimaBackupUtils.restoreExtensionsCategory(appContext, backupFile)
                            }
                            SyncCategory.BOOKMARKS -> {
                                UltimaBackupUtils.restoreCategory(appContext, category, backupFile)
                                withContext(Dispatchers.Main) {
                                    try { MainActivity.bookmarksUpdatedEvent(true) } catch (_: Throwable) {}
                                }
                            }
                            else -> {
                                UltimaBackupUtils.restoreCategory(appContext, category, backupFile)
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
                // Keep isRestoring true briefly to block async pref change callbacks
                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                withContext(Dispatchers.Main) {
                    isRestoring = false
                }
            }

            if (restoredAny) {
                reload()
            }

            return restoredAny
        } finally {
            pullMutex.unlock()
        }
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

                // Build batch of categories to push
                val categoryData = mutableMapOf<SyncCategory, Pair<String, String>>()
                for (category in SyncCategory.entries) {
                    if (!creds.isBackupEnabled(category)) continue
                    val categoryBackup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching)
                    if (categoryBackup != null) {
                        val data = categoryBackup.toJson()
                        val hash = UltimaBackupUtils.computeHash(data)
                        categoryData[category] = Pair(data, hash)
                    }
                }

                // Batch push all categories
                if (categoryData.isNotEmpty()) {
                    UltimaSettingsSyncUtils.pushCategories(context, categoryData)
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
        activity = context as? AppCompatActivity
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
            pluginScope.launch {
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
            if (key != null) {
                if (key == "ULTIMA_APP_SETTINGS_SYNC_CREDS") {
                    Log.d(TAG, "Sync configuration changed, restarting SSE listener")
                    startSseListener(context)
                    return@OnSharedPreferenceChangeListener
                }
                if (!isRestoring && System.currentTimeMillis() > restoringUntil) {
                    val category = UltimaBackupUtils.classifyKey(key)
                    if (category != null) {
                        Log.d(TAG, "Pref changed: $key → category ${category.key}")
                        markDirty(category)
                    }
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
            if (!isRestoring && System.currentTimeMillis() > restoringUntil) {
                markDirty(SyncCategory.BOOKMARKS)
            }
        }

        val app = context.applicationContext as? android.app.Application
        app?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity is com.lagradost.cloudstream3.MainActivity) {
                    this@UltimaPlugin.activity = activity as? AppCompatActivity
                    pluginScope.launch {
                        try {
                            val currentCreds = UltimaStorageManager.appSettingsSyncCreds
                            if (currentCreds != null && currentCreds.isLoggedIn() && currentCreds.restoreDevice) {
                                pullChangedCategories(activity)
                                startSseListener(activity)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (activity === this@UltimaPlugin.activity) {
                    this@UltimaPlugin.activity = null
                }
            }
        })

        startSseListener(context)
    }

    fun startSseListener(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(sseLock) {
            val creds = UltimaStorageManager.appSettingsSyncCreds
            if (creds == null || !creds.isLoggedIn() || !creds.restoreDevice) {
                sseCall?.cancel()
                sseCall = null
                isSseConnected = false
                lastSseSyncKey = null
                lastSseUrl = null
                return
            }

            val activeUrl = creds.activeUrl
            val syncKey = creds.syncKey

            if (!force && isSseConnected && lastSseSyncKey == syncKey && lastSseUrl == activeUrl) {
                Log.d(TAG, "SSE listener already connected to correct URL and key, skipping restart")
                return
            }

            sseCall?.cancel()
            isSseConnected = false
            lastSseSyncKey = syncKey
            lastSseUrl = activeUrl

            // Firebase REST Streaming requires ?alt=sse
            val url = "${activeUrl}sync/${syncKey}/manifest.json?alt=sse"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .build()

            Log.d(TAG, "Starting SSE listener for URL: $url")

            val sseClient = com.lagradost.cloudstream3.app.baseClient.newBuilder()
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val call = sseClient.newCall(request)
            sseCall = call

            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    synchronized(sseLock) {
                        if (sseCall !== call) return
                        isSseConnected = false
                        sseCall = null
                        sseRetryCount++
                    }
                    if (call.isCanceled()) return
                    val backoffMs = calculateSseBackoff()
                    Log.e(TAG, "SSE connection failed: ${e.message}, reconnecting in ${backoffMs}ms")
                    pluginScope.launch {
                        delay(backoffMs)
                        startSseListener(appContext)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    synchronized(sseLock) {
                        if (sseCall !== call) {
                            response.close()
                            return
                        }
                    }
                    if (response.code != 200) {
                        synchronized(sseLock) {
                            if (sseCall !== call) {
                                response.close()
                                return
                            }
                            isSseConnected = false
                            sseCall = null
                            sseRetryCount++
                        }
                        response.close()
                        val backoffMs = calculateSseBackoff()
                        Log.e(TAG, "SSE connection failed with HTTP code ${response.code}, reconnecting in ${backoffMs}ms")
                        pluginScope.launch {
                            delay(backoffMs)
                            startSseListener(appContext)
                        }
                        return
                    }

                    synchronized(sseLock) {
                        if (sseCall !== call) {
                            response.close()
                            return
                        }
                        isSseConnected = true
                        sseRetryCount = 0
                    }

                    val source = response.body?.source() ?: return
                    try {
                        var currentEvent: String? = null
                        while (true) {
                            synchronized(sseLock) {
                                if (sseCall !== call) return
                            }
                            if (source.exhausted()) break
                            val line = source.readUtf8Line() ?: break

                            // Skip blank lines (SSE protocol uses blank lines as event delimiters)
                            if (line.isBlank()) {
                                currentEvent = null
                                continue
                            }

                            if (line.startsWith("event:")) {
                                currentEvent = line.substring(6).trim()
                                continue
                            }

                            if (line.startsWith("data:") && (currentEvent == "put" || currentEvent == "patch")) {
                                val json = line.substring(5).trim()
                                if (json != "null" && json.isNotEmpty()) {
                                    // Ignore SSE events that are from our own recent push
                                    val timeSinceLastPush = System.currentTimeMillis() - lastPushTimestamp
                                    if (timeSinceLastPush < IGNORE_OWN_PUSH_MS) {
                                        Log.d(TAG, "SSE: Ignoring event within ${timeSinceLastPush}ms of our own push")
                                    } else {
                                        // Debounce SSE-triggered pulls: cancel previous and wait
                                        ssePullJob?.cancel()
                                        ssePullJob = pluginScope.launch {
                                            delay(SSE_PULL_DEBOUNCE_MS)
                                            syncMutex.withLock {
                                                pullChangedCategories(appContext)
                                            }
                                        }
                                    }
                                }
                                currentEvent = null
                            }
                        }
                    } catch (e: Exception) {
                        synchronized(sseLock) {
                            if (sseCall === call) {
                                isSseConnected = false
                            }
                        }
                        if (!call.isCanceled()) {
                            Log.e(TAG, "SSE read error: ${e.message}")
                        }
                    } finally {
                        response.close()
                        var shouldRetry = false
                        synchronized(sseLock) {
                            if (sseCall === call) {
                                isSseConnected = false
                                sseCall = null
                                if (!call.isCanceled()) {
                                    sseRetryCount++
                                    shouldRetry = true
                                }
                            }
                        }
                        if (shouldRetry) {
                            val backoffMs = calculateSseBackoff()
                            pluginScope.launch {
                                delay(backoffMs)
                                startSseListener(appContext)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun calculateSseBackoff(): Long {
        // Exponential backoff: 5s, 10s, 20s, 40s, 60s (capped)
        val backoff = SSE_BASE_DELAY_MS * (1L shl minOf(sseRetryCount, 4))
        return minOf(backoff, SSE_MAX_BACKOFF_MS)
    }

    // --- Force push all categories (used for initial setup + Sync Now) ---

    suspend fun pushAllCategories(context: Context) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        val resumeWatching = getResumeWatching()

        // Build batch of categories to push
        val categoryData = mutableMapOf<SyncCategory, Pair<String, String>>()
        for (category in SyncCategory.entries) {
            if (!creds.isBackupEnabled(category)) continue
            try {
                val backup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching) ?: continue
                val data = backup.toJson()
                val hash = UltimaBackupUtils.computeHash(data)
                categoryData[category] = Pair(data, hash)
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing ${category.key}: ${e.message}")
            }
        }

        if (categoryData.isNotEmpty()) {
            lastPushTimestamp = System.currentTimeMillis()
            val pushed = UltimaSettingsSyncUtils.pushCategories(context, categoryData)
            lastPushTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Force-pushed ${pushed.size}/${categoryData.size} categories")
        }
    }

    // --- Force pull all categories (used for Sync Now) ---

    suspend fun forcePullAllCategories(context: Context): Boolean {
        return pullChangedCategories(context, force = true)
    }

    fun reload() {
        pluginScope.launch(Dispatchers.Main) {
            val act = activity
            if (act == null || act.isFinishing || act.isDestroyed) return@launch
            try {
                MainActivity.bookmarksUpdatedEvent.invoke(true)
                MainActivity.reloadLibraryEvent.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "reload events invoke failed: ${e.message}")
            }
        }
    }

    private suspend fun restoreAndReload(context: Context, category: SyncCategory, backupFile: BackupFile) {
        when (category) {
            SyncCategory.EXTENSIONS -> {
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
    }

    suspend fun mergeAndSyncCategory(context: Context, category: SyncCategory) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        val isBackup = creds.isBackupEnabled(category)
        val isRestore = creds.isRestoreEnabled(category)

        if (!isBackup && !isRestore) return

        try {
            // 1. Fetch cloud data
            val cloudPayload = UltimaSettingsSyncUtils.fetchCategory(context, category)
            val cloudBackup = if (cloudPayload != null && cloudPayload.data.isNotBlank()) {
                try {
                    mapper.readValue<BackupFile>(cloudPayload.data)
                } catch (e: Exception) {
                    null
                }
            } else null

            // 2. Get local data
            val resumeWatching = getResumeWatching()
            val localBackup = UltimaBackupUtils.getBackupForCategory(context, category, resumeWatching)

            // 3. Check emptiness
            val isLocalEmpty = localBackup == null || 
                ((localBackup.datastore.bool.isNullOrEmpty() && localBackup.datastore.int.isNullOrEmpty() && localBackup.datastore.string.isNullOrEmpty() && localBackup.datastore.float.isNullOrEmpty() && localBackup.datastore.long.isNullOrEmpty() && localBackup.datastore.stringSet.isNullOrEmpty()) &&
                 (localBackup.settings.bool.isNullOrEmpty() && localBackup.settings.int.isNullOrEmpty() && localBackup.settings.string.isNullOrEmpty() && localBackup.settings.float.isNullOrEmpty() && localBackup.settings.long.isNullOrEmpty() && localBackup.settings.stringSet.isNullOrEmpty()))

            val isCloudEmpty = cloudBackup == null ||
                ((cloudBackup.datastore.bool.isNullOrEmpty() && cloudBackup.datastore.int.isNullOrEmpty() && cloudBackup.datastore.string.isNullOrEmpty() && cloudBackup.datastore.float.isNullOrEmpty() && cloudBackup.datastore.long.isNullOrEmpty() && cloudBackup.datastore.stringSet.isNullOrEmpty()) &&
                 (cloudBackup.settings.bool.isNullOrEmpty() && cloudBackup.settings.int.isNullOrEmpty() && cloudBackup.settings.string.isNullOrEmpty() && cloudBackup.settings.float.isNullOrEmpty() && cloudBackup.settings.long.isNullOrEmpty() && cloudBackup.settings.stringSet.isNullOrEmpty()))

            if (isLocalEmpty) {
                // Local is empty: pull from cloud if available
                if (!isCloudEmpty && cloudBackup != null && isRestore) {
                    Log.d(TAG, "Local is empty for ${category.key}, pulling from cloud")
                    isRestoring = true
                    try {
                        restoreAndReload(context, category, cloudBackup)
                    } finally {
                        restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                        withContext(Dispatchers.Main) {
                            isRestoring = false
                        }
                    }
                    // Use the cloud payload timestamp directly instead of re-fetching manifest
                    UltimaStorageManager.setCategoryTimestamp(category, cloudPayload?.ts ?: System.currentTimeMillis())
                    val dataHash = UltimaBackupUtils.computeHash(cloudPayload?.data ?: "")
                    UltimaStorageManager.setCategoryHash(category, dataHash)
                }
            } else if (isCloudEmpty) {
                // Cloud is empty: push local to cloud if available
                if (localBackup != null && isBackup) {
                    Log.d(TAG, "Cloud is empty for ${category.key}, pushing local to cloud")
                    val data = localBackup.toJson()
                    val hash = UltimaBackupUtils.computeHash(data)
                    UltimaSettingsSyncUtils.pushCategory(context, category, data, hash)
                    lastPushTimestamp = System.currentTimeMillis()
                }
            } else {
                // Both have data: merge them
                if (localBackup != null && cloudBackup != null) {
                    Log.d(TAG, "Merging local and cloud data for ${category.key}")
                    val localCategoryTs = UltimaStorageManager.getCategoryTimestamp(category)
                    val mergedBackup = UltimaBackupUtils.mergeBackupFiles(localBackup, cloudBackup, localCategoryTs)
                    if (mergedBackup != null) {
                        // Write merged data locally if restore is enabled
                        if (isRestore) {
                            isRestoring = true
                            try {
                                restoreAndReload(context, category, mergedBackup)
                            } finally {
                                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                                withContext(Dispatchers.Main) {
                                    isRestoring = false
                                }
                            }
                        }
                        // Push merged data to cloud if backup is enabled
                        if (isBackup) {
                            val data = mergedBackup.toJson()
                            val hash = UltimaBackupUtils.computeHash(data)
                            UltimaSettingsSyncUtils.pushCategory(context, category, data, hash)
                            lastPushTimestamp = System.currentTimeMillis()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error merging and syncing category ${category.key}: ${e.message}")
        }
    }

    suspend fun mergeAndSyncAllCategories(context: Context) {
        val appContext = context.applicationContext
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn()) return

        consumeDirtyCategories()

        // Fetch manifest once for all categories
        val manifest = UltimaSettingsSyncUtils.fetchManifest(appContext)
        val resumeWatching = getResumeWatching()

        // Fetch all cloud category payloads in parallel
        val enabledCategories = SyncCategory.entries.filter { cat ->
            creds.isBackupEnabled(cat) || creds.isRestoreEnabled(cat)
        }

        val cloudPayloads = coroutineScope {
            enabledCategories.map { category ->
                async(Dispatchers.IO) {
                    try {
                        val payload = UltimaSettingsSyncUtils.fetchCategory(appContext, category)
                        category to payload
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching category ${category.key}: ${e.message}")
                        category to null
                    }
                }
            }.awaitAll().toMap()
        }

        // Process each category and collect pushes
        val categoriesToPush = mutableMapOf<SyncCategory, Pair<String, String>>()
        var restoredAny = false

        for (category in enabledCategories) {
            val isBackup = creds.isBackupEnabled(category)
            val isRestore = creds.isRestoreEnabled(category)
            if (!isBackup && !isRestore) continue

            try {
                val cloudPayload = cloudPayloads[category]
                val cloudBackup = if (cloudPayload != null && cloudPayload.data.isNotBlank()) {
                    try { mapper.readValue<BackupFile>(cloudPayload.data) } catch (_: Exception) { null }
                } else null

                val localBackup = UltimaBackupUtils.getBackupForCategory(appContext, category, resumeWatching)

                val isLocalEmpty = localBackup == null ||
                    ((localBackup.datastore.bool.isNullOrEmpty() && localBackup.datastore.int.isNullOrEmpty() && localBackup.datastore.string.isNullOrEmpty() && localBackup.datastore.float.isNullOrEmpty() && localBackup.datastore.long.isNullOrEmpty() && localBackup.datastore.stringSet.isNullOrEmpty()) &&
                     (localBackup.settings.bool.isNullOrEmpty() && localBackup.settings.int.isNullOrEmpty() && localBackup.settings.string.isNullOrEmpty() && localBackup.settings.float.isNullOrEmpty() && localBackup.settings.long.isNullOrEmpty() && localBackup.settings.stringSet.isNullOrEmpty()))

                val isCloudEmpty = cloudBackup == null ||
                    ((cloudBackup.datastore.bool.isNullOrEmpty() && cloudBackup.datastore.int.isNullOrEmpty() && cloudBackup.datastore.string.isNullOrEmpty() && cloudBackup.datastore.float.isNullOrEmpty() && cloudBackup.datastore.long.isNullOrEmpty() && cloudBackup.datastore.stringSet.isNullOrEmpty()) &&
                     (cloudBackup.settings.bool.isNullOrEmpty() && cloudBackup.settings.int.isNullOrEmpty() && cloudBackup.settings.string.isNullOrEmpty() && cloudBackup.settings.float.isNullOrEmpty() && cloudBackup.settings.long.isNullOrEmpty() && cloudBackup.settings.stringSet.isNullOrEmpty()))

                if (isLocalEmpty) {
                    if (!isCloudEmpty && cloudBackup != null && isRestore) {
                        val cloudMeta = manifest?.getMeta(category)
                        val cloudHash = cloudMeta?.hash ?: ""
                        val localHash = UltimaStorageManager.getCategoryHash(category)
                        if (cloudHash != localHash) {
                            Log.d(TAG, "Local is empty for ${category.key}, pulling from cloud")
                            isRestoring = true
                            try {
                                restoreAndReload(appContext, category, cloudBackup)
                                restoredAny = true
                            } finally {
                                restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                                withContext(Dispatchers.Main) {
                                    isRestoring = false
                                }
                            }
                            if (cloudMeta != null) {
                                UltimaStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                                UltimaStorageManager.setCategoryHash(category, cloudMeta.hash)
                            }
                        }
                    }
                } else if (isCloudEmpty) {
                    if (localBackup != null && isBackup) {
                        val data = localBackup.toJson()
                        val hash = UltimaBackupUtils.computeHash(data)
                        val cloudMeta = manifest?.getMeta(category)
                        val cloudHash = cloudMeta?.hash ?: ""
                        if (hash != cloudHash) {
                            Log.d(TAG, "Cloud is empty for ${category.key}, queuing push")
                            categoriesToPush[category] = Pair(data, hash)
                        }
                    }
                } else {
                    if (localBackup != null && cloudBackup != null) {
                        val localCategoryTs = UltimaStorageManager.getCategoryTimestamp(category)
                        val mergedBackup = UltimaBackupUtils.mergeBackupFiles(localBackup, cloudBackup, localCategoryTs)
                        if (mergedBackup != null) {
                            val data = mergedBackup.toJson()
                            val hash = UltimaBackupUtils.computeHash(data)
                            
                            val localHash = UltimaStorageManager.getCategoryHash(category)
                            val cloudMeta = manifest?.getMeta(category)
                            val cloudHash = cloudMeta?.hash ?: ""

                            if (hash != localHash && isRestore) {
                                Log.d(TAG, "Merged data different from local for ${category.key}, restoring")
                                isRestoring = true
                                try {
                                    restoreAndReload(appContext, category, mergedBackup)
                                    restoredAny = true
                                } finally {
                                    restoringUntil = System.currentTimeMillis() + RESTORE_GUARD_MS
                                    withContext(Dispatchers.Main) {
                                        isRestoring = false
                                    }
                                }
                                UltimaStorageManager.setCategoryHash(category, hash)
                                if (cloudMeta != null) {
                                    UltimaStorageManager.setCategoryTimestamp(category, cloudMeta.ts)
                                }
                            }

                            if (hash != cloudHash && isBackup) {
                                Log.d(TAG, "Merged data different from cloud for ${category.key}, queuing push")
                                categoriesToPush[category] = Pair(data, hash)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error merging category ${category.key}: ${e.message}")
            }
        }

        // Batch push all categories that need pushing
        if (categoriesToPush.isNotEmpty()) {
            lastPushTimestamp = System.currentTimeMillis()
            val pushed = UltimaSettingsSyncUtils.pushCategories(appContext, categoriesToPush)
            lastPushTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Batch pushed ${pushed.size}/${categoriesToPush.size} categories in mergeAndSyncAll")
            val failed = categoriesToPush.keys - pushed
            if (failed.isNotEmpty()) {
                Log.e(TAG, "Failed to push categories in mergeAndSyncAll: ${failed.map { it.key }}")
                synchronized(dirtyCategoriesLock) {
                    dirtyCategories.addAll(failed)
                }
                scheduleDebouncedPush() // Reschedule retry
            }
        }

        if (restoredAny) {
            reload()
        }
    }
}