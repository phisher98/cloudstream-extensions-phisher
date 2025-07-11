package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.DataStoreHelper.ResumeWatchingResult
import com.phisher98.UltimaStorageManager as sm

object WatchSyncUtils {
    data class WatchSyncCreds(
            @JsonProperty("token") var token: String? = null,
            @JsonProperty("projectNum") var projectNum: Int? = null,
            @JsonProperty("deviceName") var deviceName: String? = null,
            @JsonProperty("deviceId") var deviceId: String? = null, // draftIssueID
            @JsonProperty("itemId") var itemId: String? = null, // projectItemID
            @JsonProperty("projectId") var projectId: String? = null,
            @JsonProperty("isThisDeviceSync") var isThisDeviceSync: Boolean = false,
            @JsonProperty("enabledDevices") var enabledDevices: MutableList<String>? = null
    ) {
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
                @JsonProperty("deviceId") var deviceId: String, // draftIssueID // for add update
                @JsonProperty("itemId") var itemId: String, // projectItemID // for delete
                @JsonProperty("syncedData") var syncedData: List<ResumeWatchingResult>? = null
        )

        private val apiUrl = "https://api.github.com/graphql"

        private fun Any.toStringData(): String {
            return mapper.writeValueAsString(this)
        }

        fun isLoggedIn(): Boolean {
            return !(token.isNullOrEmpty() ||
                    projectNum == null ||
                    deviceName.isNullOrEmpty() ||
                    projectId.isNullOrEmpty())
        }

        private suspend fun apiCall(query: String): APIRes? {
            val apiUrl = "https://api.github.com/graphql"
            val header =
                    mapOf(
                            "Content-Type" to "application/json",
                            "Authorization" to "Bearer " + (token ?: return null)
                    )
            val data = """ { "query": ${query} } """
            val test = app.post(apiUrl, headers = header, json = data)
            val res = test.parsedSafe<APIRes>()
            return res
        }

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
            val syncData = getResumeWatching()?.toStringData() ?: "[]"
            val data = base64Encode(syncData.toByteArray())
            val query =
                    """ mutation AddProjectV2DraftIssue { addProjectV2DraftIssue( input: { projectId: "$projectId", title: "$deviceName", body: "$data" } ) { projectItem { id content { ... on DraftIssue { id } } } } } """
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
                    """ mutation DeleteIssue { deleteProjectV2Item( input: { projectId: "$projectId" itemId: "$itemId" } ) { deletedItemId } } """
            val res = apiCall(query.toStringData()) ?: return failure
            if (res.data.delItem?.deletedItemId.equals(itemId)) {
                itemId = null
                deviceId = null
                isThisDeviceSync = false
                sm.deviceSyncCreds = this
                return true to "Device de-registered"
            } else return failure
        }

        suspend fun syncThisDevice(): Pair<Boolean, String?> {
            val failure = false to "something went wrong"
            if (!isLoggedIn()) return failure
            if (!isThisDeviceSync) return failure
            val syncData = getResumeWatching()?.toStringData() ?: "[]"
            val data = base64Encode(syncData.toByteArray())
            val query =
                    """ mutation UpdateProjectV2DraftIssue { updateProjectV2DraftIssue( input: { draftIssueId: "$deviceId", title: "$deviceName", body: "$data" } ) { draftIssue { id } } } """
            apiCall(query.toStringData()) ?: return failure
            return true to "sync complete"
        }

        suspend fun fetchDevices(): List<SyncDevice>? {
            if (!isLoggedIn()) return null
            val query =
                    """ query User { viewer { projectV2(number: ${projectNum ?: return null}) { id items(first: 50) { nodes { id content { ... on DraftIssue { id title bodyText } } } totalCount } } } } """
            val res = apiCall(query.toStringData()) ?: return null
            val data =
                    res.data.viewer?.projectV2?.items?.nodes?.map {
                        val data = base64Decode(it.content.bodyText)
                        val syncData =
                                parseJson<Array<ResumeWatchingResult>?>(data)?.toList()
                                        ?: return null
                        SyncDevice(it.content.title, it.content.id, it.id, syncData)
                    }
            return data
        }
    }
}
