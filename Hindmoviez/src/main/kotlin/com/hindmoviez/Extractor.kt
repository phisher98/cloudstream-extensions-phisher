package com.hindmoviez

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class Gdshine : ExtractorApi() {
    override val name = "Gdshine"
    override val mainUrl = "https://gdshine.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast('/')

        val fileData = app.get("$mainUrl/api/files/s/$id")
            .parsedSafe<Response>()
            ?.data ?: return

        val workerData = app.post("$mainUrl/api/downloads/${fileData.id}/via-worker")
            .parsedSafe<Worker>()
            ?.data ?: return

        callback(
            newExtractorLink(
                name,
                "[Gdshine] $referer",
                workerData.copyUrl
            ) {
                quality = getIndexQuality(fileData.name)
            }
        )
    }

    data class Response(
        val data: Data
    )

    data class Data(
        val id: String,
        val name: String
    )

    data class Worker(
        val data: WorkerData
    )

    data class WorkerData(
        val copyUrl: String
    )
}