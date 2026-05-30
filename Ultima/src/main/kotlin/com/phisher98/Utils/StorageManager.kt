package com.phisher98

// import com.phisher98.UltimaUtils.Provider

import com.phisher98.UltimaUtils.ExtensionInfo
import com.phisher98.UltimaUtils.MediaProviderState
import com.phisher98.UltimaUtils.SectionInfo
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey

object UltimaStorageManager {

    // #region - custom data variables

    var extNameOnHome: Boolean
        get() = getKey("ULTIMA_EXT_NAME_ON_HOME") ?: true
        set(value) {
            setKey("ULTIMA_EXT_NAME_ON_HOME", value)
        }

    var currentExtensions: Array<ExtensionInfo>
        get() = getKey("ULTIMA_EXTENSIONS_LIST") ?: emptyArray<ExtensionInfo>()
        set(value) {
            setKey("ULTIMA_EXTENSIONS_LIST", value)
        }

    var currentMetaProviders: Array<Pair<String, Boolean>>
        get() = listMetaProviders()
        set(value) {
            setKey("ULTIMA_CURRENT_META_PROVIDERS", value)
        }

    var currentMediaProviders: Array<MediaProviderState>
        get() = listMediaProviders()
        set(value) {
            setKey("ULTIMA_CURRENT_MEDIA_PROVIDERS", value)
        }


    var appSettingsSyncCreds: AppSettingsSyncCreds?
        get() = getKey("ULTIMA_APP_SETTINGS_SYNC_CREDS")
        set(value) {
            setKey("ULTIMA_APP_SETTINGS_SYNC_CREDS", value)
        }

    var lastLocalSyncTime: Long
        get() = getKey("ULTIMA_LAST_LOCAL_SYNC_TIME") ?: 0L
        set(value) {
            setKey("ULTIMA_LAST_LOCAL_SYNC_TIME", value)
        }

    var syncV2Migrated: Boolean
        get() = getKey("ULTIMA_SYNC_V2_MIGRATED") ?: false
        set(value) {
            setKey("ULTIMA_SYNC_V2_MIGRATED", value)
        }

    fun getCategoryTimestamp(category: SyncCategory): Long {
        return getKey("ULTIMA_SYNC_TS_${category.key}") ?: 0L
    }

    fun setCategoryTimestamp(category: SyncCategory, ts: Long) {
        setKey("ULTIMA_SYNC_TS_${category.key}", ts)
    }

    fun getCategoryHash(category: SyncCategory): String {
        return getKey("ULTIMA_SYNC_HASH_${category.key}") ?: ""
    }

    fun setCategoryHash(category: SyncCategory, hash: String) {
        setKey("ULTIMA_SYNC_HASH_${category.key}", hash)
    }

    fun getCategorySyncedKeys(category: SyncCategory): Set<String> {
        return getKey<Array<String>>("ULTIMA_SYNCED_KEYS_${category.key}")?.toSet() ?: emptySet()
    }

    fun setCategorySyncedKeys(category: SyncCategory, keys: Set<String>) {
        setKey("ULTIMA_SYNCED_KEYS_${category.key}", keys.toTypedArray())
    }

    // #endregion - custom data variables

    fun deleteAllData() {
        listOf(
                        "ULTIMA_PROVIDER_LIST", // old key
                        "ULTIMA_EXT_NAME_ON_HOME",
                        "ULTIMA_EXTENSIONS_LIST",
                        "ULTIMA_CURRENT_META_PROVIDERS",
                        "ULTIMA_CURRENT_MEDIA_PROVIDERS",
                        "ULTIMA_APP_SETTINGS_SYNC_CREDS",
                        "ULTIMA_LAST_LOCAL_SYNC_TIME",
                        "ULTIMA_SYNC_V2_MIGRATED"
                )
                .forEach { setKey(it, null) }
        // Clear per-category sync state
        SyncCategory.entries.forEach { cat ->
            setKey("ULTIMA_SYNC_TS_${cat.key}", null)
            setKey("ULTIMA_SYNC_HASH_${cat.key}", null)
            setKey("ULTIMA_SYNCED_KEYS_${cat.key}", null)
        }
    }


    fun fetchExtensions(): Array<ExtensionInfo> {
        val providers = UltimaUtils.getAllProviders()
        return synchronized(providers) {
            val cachedExtensions = getKey<Array<ExtensionInfo>>("ULTIMA_EXTENSIONS_LIST")
            val filtered = providers.filter { it.name != "Ultima" }

            filtered.map { provider ->
                val existing = cachedExtensions?.find { it.name == provider.name }
                existing ?: ExtensionInfo(
                    name = provider.name,
                    provider.mainPage.map { section ->
                        SectionInfo(
                            name = section.name,
                            section.data,
                            provider.name,
                            false
                        )
                    }.toTypedArray()
                )
            }.toTypedArray()
        }
    }


    private fun listMetaProviders(): Array<Pair<String, Boolean>> {
        val currentProviders = UltimaMetaProviderUtils.metaProviders
        val storedProviders = getKey<Array<Pair<String, Boolean>>>("ULTIMA_CURRENT_META_PROVIDERS")
            ?: return currentProviders

        val currentNames = currentProviders.map { it.first }.sorted()
        val storedNames = storedProviders.map { it.first }.sorted()

        // If the names match (ignoring order), use the stored version
        if (currentNames == storedNames) return storedProviders

        // Merge stored flags if available, otherwise use default
        return currentProviders.map { provider ->
            storedProviders.find { it.first == provider.first } ?: provider
        }.toTypedArray()
    }


    private fun listMediaProviders(): Array<MediaProviderState> {
        val currentProviderNames = UltimaMediaProvidersUtils.mediaProviders.map { it.name }
        val stored = getKey<Array<MediaProviderState>>("ULTIMA_CURRENT_MEDIA_PROVIDERS")
            ?: return currentProviderNames.map { MediaProviderState(it, enabled = true, null) }.toTypedArray()

        val storedNames = stored.map { it.name }.sorted()
        if (currentProviderNames.sorted() == storedNames) {
            return stored.map { state ->
                MediaProviderState(
                    name = state.name,
                    enabled = state.enabled,
                    customDomain = state.customDomain,
                )
            }.toTypedArray()
        }

        return currentProviderNames.map { name ->
            val match = stored.find { it.name == name }
            MediaProviderState(
                name = name,
                enabled = match?.enabled ?: true,
                customDomain = match?.customDomain
            )
        }.toTypedArray()
    }

}
