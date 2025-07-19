package com.phisher98

// import com.phisher98.UltimaUtils.Provider

import com.lagradost.api.Log
import com.phisher98.UltimaUtils.ExtensionInfo
import com.phisher98.UltimaUtils.MediaProviderState
import com.phisher98.UltimaUtils.SectionInfo
import com.phisher98.WatchSyncUtils.WatchSyncCreds
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey

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

    var deviceSyncCreds: WatchSyncCreds?
        get() = getKey("ULTIMA_WATCH_SYNC_CREDS")
        set(value) {
            setKey("ULTIMA_WATCH_SYNC_CREDS", value)
        }

    // #endregion - custom data variables

    fun deleteAllData() {
        listOf(
                        "ULTIMA_PROVIDER_LIST", // old key
                        "ULTIMA_EXT_NAME_ON_HOME",
                        "ULTIMA_EXTENSIONS_LIST",
                        "ULTIMA_CURRENT_META_PROVIDERS",
                        "ULTIMA_CURRENT_MEDIA_PROVIDERS",
                        "ULTIMA_WATCH_SYNC_CREDS"
                )
                .forEach { setKey(it, null) }
    }


    fun fetchExtensions(): Array<ExtensionInfo> = synchronized(allProviders) {
        val cachedExtensions = getKey<Array<ExtensionInfo>>("ULTIMA_EXTENSIONS_LIST")
        val providers = allProviders.filter { it.name != "Ultima" }

        providers.map { provider ->
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
        if (currentProviderNames.sorted() == storedNames) return stored

        return currentProviderNames.map { name ->
            stored.find { it.name == name } ?: MediaProviderState(name, enabled = true,  null)
        }.toTypedArray()
    }

}
