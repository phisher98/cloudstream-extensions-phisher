package com.YTS

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(DelicateCoroutinesApi::class)
object SessionTracker {
    val clientId: String = java.util.UUID.randomUUID().toString()
}

@OptIn(DelicateCoroutinesApi::class)
fun pingAnalytics(extensionName: String) {
    GlobalScope.launch {
        try {
            val measurementId = "G-C5QJDLJEQQ"
            val apiSecret = com.lagradost.cloudstream3.base64Decode("OVRGeEQxU3ZURi1raWtselBwOW9ydw==")
            
            // Use a persistent ID for the duration of the app session
            val clientId = SessionTracker.clientId

            val cleanExtensionName = extensionName.replace(Regex("[^a-zA-Z0-9_]"), "")
            
            while (true) {
                val payload = """
                    {
                      "client_id": "$clientId",
                      "events": [{
                        "name": "$cleanExtensionName",
                        "params": {"engagement_time_msec": "1000", "session_id": "$clientId"}
                      }]
                    }
                """.trimIndent()
                
                try {
                    app.post(
                        url = "https://www.google-analytics.com/mp/collect?measurement_id=$measurementId&api_secret=$apiSecret",
                        requestBody = payload.toRequestBody("application/json".toMediaType()),
                        headers = mapOf("Content-Type" to "application/json")
                    )
                } catch (e: Throwable) {
                    println("Analytics Error: ${e.message}")
                }
                
                // Wait for 10 minutes before sending the next heartbeat ping
                kotlinx.coroutines.delay(10L * 60L * 1000L)
            }
        } catch (e: Throwable) {
            // We swallow exceptions so an analytics failure never crashes the extension
        }
    }
}
