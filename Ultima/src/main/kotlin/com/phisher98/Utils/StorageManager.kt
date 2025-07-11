package com.phisher98

// import com.phisher98.UltimaUtils.Provider

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

    fun fetchExtensions(): Array<ExtensionInfo> {
        synchronized(allProviders) {
            var providers = allProviders
            var newProviderList = emptyArray<ExtensionInfo>()

            providers.forEach { provider ->
                if (!provider.name.equals("Ultima")) {
                    val doesProviderExist =
                            getKey<Array<ExtensionInfo>>("ULTIMA_EXTENSIONS_LIST")?.find {
                                it.name == provider.name
                            }
                    if (doesProviderExist == null) {
                        var mainPageList = emptyArray<SectionInfo>()
                        provider.mainPage.forEach { section ->
                            var sectionData =
                                    SectionInfo(section.name, section.data, provider.name, false)
                            mainPageList += sectionData
                        }
                        var providerData = ExtensionInfo(provider.name, mainPageList)
                        newProviderList += providerData
                    } else {
                        newProviderList += doesProviderExist
                    }
                }
            }

            if (newProviderList.size == providers.size) {
                return newProviderList
            } else {
                return newProviderList
                        .filter { new -> providers.find { new.name == it.name } != null }
                        .toTypedArray()
            }
        }
    }

    fun listMetaProviders(): Array<Pair<String, Boolean>> {
        val metaProviders = UltimaMetaProviderUtils.metaProviders
        val stored = getKey<Array<Pair<String, Boolean>>>("ULTIMA_CURRENT_META_PROVIDERS")
        stored ?: return metaProviders
        if (stored
                        .map { it.first }
                        .sorted()
                        .toString()
                        .equals(metaProviders.map { it.first }.sorted().toString())
        )
                return stored
        return metaProviders
                .map { metaProvider ->
                    stored.find { it.first.equals(metaProvider.first) } ?: metaProvider
                }
                .toTypedArray()
    }

    fun listMediaProviders(): Array<MediaProviderState> {
        val mediaProvidersList = UltimaMediaProvidersUtils.mediaProviders.map { it.name }
        val stored = getKey<Array<MediaProviderState>>("ULTIMA_CURRENT_MEDIA_PROVIDERS")
        stored
                ?: return mediaProvidersList
                        .map { MediaProviderState(it, true, null) }
                        .toTypedArray()
        if (mediaProvidersList
                        .sorted()
                        .toString()
                        .equals(stored.map { it.name }.sorted().toString())
        )
                return stored
        return mediaProvidersList
                .map { mediaProvider ->
                    stored.find { it.name.equals(mediaProvider) }
                            ?: MediaProviderState(mediaProvider, true, null)
                }
                .toTypedArray()
    }
}
