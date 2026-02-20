package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStoreHelper.ResumeWatchingResult
import com.phisher98.UltimaStorageManager as sm

object WatchSyncUtils {

    data class WatchSyncCreds(
        @JsonProperty("token") var token: String? = null,
        @JsonProperty("projectNum") var projectNum: Int? = null,
        @JsonProperty("deviceName") var deviceName: String? = null,
        @JsonProperty("deviceId") var deviceId: String? = null,  // draftIssueID
        @JsonProperty("itemId") var itemId: String? = null,       // projectItemID
        @JsonProperty("projectId") var projectId: String? = null,
        @JsonProperty("isThisDeviceSync") var isThisDeviceSync: Boolean = false,
        @JsonProperty("enabledDevices") var enabledDevices: MutableList<String>? = null
    ) {

        // --- API Response Models ---
        data class APIRes(@JsonProperty("data") var data: Data) {
            data class Data(
                @JsonProperty("viewer") var viewer: Viewer?,
                @JsonProperty("addProjectV2DraftIssue") var issue: Issue?,
                @JsonProperty("deleteProjectV2Item") var delItem: DelItem?
            ) {
                data class Viewer(@JsonProperty("projectV2") var projectV2: ProjectV2) {
                    data class ProjectV2(
                        @JsonProperty("id") var id: String,
                        @JsonProperty("items") var items: Items?
                    ) {
                        data class Items(
                            @JsonProperty("nodes") var nodes: Array<Node>?,
                        ) {
                            data class Node(
                                @JsonProperty("id") var id: String,
                                @JsonProperty("content") var content: Content
                            ) {
                                data class Content(
                                    @JsonProperty("id") var id: String,
                                    @JsonProperty("title") var title: String,
                                    @JsonProperty("bodyText") var bodyText: String,
                                )
                            }
                        }
                    }
                }

                data class Issue(@JsonProperty("projectItem") var projectItem: ProjectItem) {
                    data class ProjectItem(
                        @JsonProperty("id") var id: String,
                        @JsonProperty("content") var content: Content
                    ) {
                        data class Content(@JsonProperty("id") var id: String)
                    }
                }

                data class DelItem(@JsonProperty("deletedItemId") var deletedItemId: String)
            }
        }

        data class SyncDevice(
            @JsonProperty("name") var name: String,
            @JsonProperty("deviceId") var deviceId: String,
            @JsonProperty("itemId") var itemId: String,
            @JsonProperty("payload") var payload: SyncPayload? = null
        )

        data class SyncPayload(
            @JsonProperty("resumeWatching") val resumeWatching: List<ResumeWatchingResult>? = null,
            @JsonProperty("data") val data: Map<String, String>? = null,
            @JsonProperty("extensions") val extensions: Array<UltimaUtils.ExtensionInfo>? = null,
            @JsonProperty("metaProviders") val metaProviders: Array<Pair<String, Boolean>>? = null,
            @JsonProperty("mediaProviders") val mediaProviders: Array<UltimaUtils.MediaProviderState>? = null,
            val extNameOnHome: Boolean? = null,
        )

        private val apiUrl = "https://api.github.com/graphql"

        private fun Any.toStringData(): String = mapper.writeValueAsString(this)

        private fun isLoggedIn(): Boolean {
            return !(token.isNullOrEmpty() ||
                    projectNum == null ||
                    deviceName.isNullOrEmpty() ||
                    projectId.isNullOrEmpty())
        }

        private suspend fun apiCall(query: String): APIRes? {
            val header = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer " + (token ?: return null)
            )
            val data = """ { "query": $query } """
            val test = app.post(apiUrl, headers = header, json = data)
            return test.parsedSafe<APIRes>()
        }

        private val nonTransferableKeys = listOf(
            "result_resume_watching_migrated",
        )

        private fun String.isResume(): Boolean {
            return !nonTransferableKeys.any { this.contains(it) }
        }

        private fun String.isBackup(resumeWatching: List<ResumeWatchingResult>? = null): Boolean {
            var check = !nonTransferableKeys.any { this.contains(it) }
            if (check) {
                when {
                    this.contains("download_header_cache") -> {
                        val id = this.split("/").getOrNull(1)?.toIntOrNull()
                        check = id?.let { intId ->
                            resumeWatching?.any { if (it.parentId != null) it.parentId == intId else it.id == intId } == true
                        } ?: false
                    }
                    this.contains("video_pos_dur") -> {
                        val id = this.split("/").getOrNull(2)?.toIntOrNull()
                        check = id?.let { intId ->
                            resumeWatching?.any { it.id == intId } == true
                        } ?: false
                    }
                    this.contains("result_season") || this.contains("result_dub") || this.contains("result_episode") -> {
                        val id = this.split("/").getOrNull(2)?.toIntOrNull()
                        check = id?.let { intId ->
                            resumeWatching?.any { it.parentId == intId } == true
                        } ?: false
                    }
                }
            }
            return check
        }

        /** Build JSON with all fields we want to sync */
        private suspend fun buildSyncJson(): String {
            val context = AcraApplication.context
            val sharedPrefs = context?.getSharedPrefs()

            // Only keep keys that are not non-transferable
            val filteredPrefs = sharedPrefs?.all
                ?.filter { (key, _) ->
                    (key.contains("resume") || key.contains("download")) &&
                            key.isResume() &&
                            key.isBackup(getResumeWatching())
                }
                ?.toMap()
            Log.d("Phisher shared", filteredPrefs.toString())

            val map = mapOf(
                "resumeWatching" to getResumeWatching(),
                "data" to filteredPrefs,
                "extNameOnHome" to sm.extNameOnHome,
                "extensions" to sm.currentExtensions,
                "metaProviders" to sm.currentMetaProviders,
                "mediaProviders" to sm.currentMediaProviders
            )

            return mapper.writeValueAsString(map)
        }


        // --- API Methods ---
        suspend fun syncProjectDetails(): Pair<Boolean, String?> {
            val failure = false to "something went wrong"
            val query =
                """ query Viewer { viewer { projectV2(number: ${projectNum ?: return failure}) { id } } } """
            val res = apiCall(query.toStringData()) ?: return failure
            projectId = res.data.viewer?.projectV2?.id ?: return failure
            sm.deviceSyncCreds = this
            return true to "Project details saved"
        }

        suspend fun registerThisDevice(): Pair<Boolean, String?> {
            val failure = false to "something went wrong"
            if (!isLoggedIn()) return failure

            val data = base64Encode(buildSyncJson().toByteArray())
            val query =
                """ mutation AddProjectV2DraftIssue {
                    addProjectV2DraftIssue(
                        input: { projectId: "$projectId", title: "$deviceName", body: "$data" }
                    ) { projectItem { id content { ... on DraftIssue { id } } } }
                } """

            val res = apiCall(query.toStringData()) ?: return failure
            itemId = res.data.issue?.projectItem?.id ?: return failure
            deviceId = res.data.issue?.projectItem?.content?.id ?: return failure
            isThisDeviceSync = true
            sm.deviceSyncCreds = this
            return true to "Device is registered"
        }

        suspend fun deregisterThisDevice(): Pair<Boolean, String?> {
            val failure = false to "something went wrong"
            if (!isLoggedIn()) return failure

            val query =
                """ mutation DeleteIssue {
                    deleteProjectV2Item(input: { projectId: "$projectId", itemId: "$itemId" }) {
                        deletedItemId
                    }
                } """

            val res = apiCall(query.toStringData()) ?: return failure
            return if (res.data.delItem?.deletedItemId.equals(itemId)) {
                itemId = null
                deviceId = null
                isThisDeviceSync = false
                sm.deviceSyncCreds = this
                true to "Device de-registered"
            } else failure
        }

        suspend fun syncThisDevice(): Pair<Boolean, String?> {
            val failure = false to "something went wrong"
            if (!isLoggedIn() || !isThisDeviceSync) return failure

            val data = base64Encode(buildSyncJson().toByteArray())
            val query =
                """ mutation UpdateProjectV2DraftIssue {
                    updateProjectV2DraftIssue(
                        input: { draftIssueId: "$deviceId", title: "$deviceName", body: "$data" }
                    ) { draftIssue { id } }
                } """

            apiCall(query.toStringData()) ?: return failure
            return true to "sync complete"
        }

        suspend fun fetchDevices(): List<SyncDevice>? {
            if (!isLoggedIn()) return null

            val query =
                """ query User {
                    viewer {
                        projectV2(number: ${projectNum ?: return null}) {
                            id
                            items(first: 50) {
                                nodes { id content { ... on DraftIssue { id title bodyText } } }
                                totalCount
                            }
                        }
                    }
                } """

            val res = apiCall(query.toStringData()) ?: return null

            return res.data.viewer?.projectV2?.items?.nodes?.mapNotNull { node ->
                val raw = base64Decode(node.content.bodyText)
                val payload = runCatching {
                    parseJson<SyncPayload?>(raw) ?: run {
                        val oldResume = parseJson<List<ResumeWatchingResult>?>(raw)
                        SyncPayload(resumeWatching = oldResume)
                    }
                }.getOrElse {
                    Log.e("fetchDevices", "Failed to parse payload for ${node.content.title}: ${it.message}")
                    null
                }

                payload?.let {
                    SyncDevice(
                        name = node.content.title,
                        deviceId = node.content.id,
                        itemId = node.id,
                        payload = it
                    )
                }
            }?.also { Log.i("fetchDevices", "Fetched ${it.size} devices") }
        }
    }
}
