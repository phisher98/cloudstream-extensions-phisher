package com.phisher98

import android.app.ActivityManager
import android.content.Context
import com.lagradost.api.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced concurrency management for StreamPlay
 * - Device-adaptive concurrency
 * - Priority-based execution
 * - Progressive result streaming
 * - Memory-aware throttling
 */
object StreamPlayConcurrency {

    private const val TAG = "StreamPlayConcurrency"

    // ==================== Device Profile Detection ====================

    enum class DeviceProfile {
        LOW_END,    // < 2GB RAM, < 4 cores
        MID_RANGE,  // 2-4GB RAM, 4-6 cores
        HIGH_END;   // > 4GB RAM, > 6 cores

        val recommendedConcurrency: Int
            get() = when (this) {
                LOW_END -> 4
                MID_RANGE -> 15
                HIGH_END -> 30
            }

    }

    private var detectedProfile: DeviceProfile? = null

    /**
     * Detect device capabilities and return appropriate profile
     */
    fun detectDeviceProfile(context: Context): DeviceProfile {
        if (detectedProfile != null) return detectedProfile!!

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        val availableProcessors = Runtime.getRuntime().availableProcessors()

        val profile = when {
            totalRamMB < 2048 || availableProcessors < 4 -> DeviceProfile.LOW_END
            totalRamMB < 4096 || availableProcessors < 6 -> DeviceProfile.MID_RANGE
            else -> DeviceProfile.HIGH_END
        }

        detectedProfile = profile
        Log.d(TAG, "🔍 Detected device: $profile (RAM: ${totalRamMB}MB, Cores: $availableProcessors)")
        return profile
    }

    // ==================== Enhanced runLimitedAsync ====================

    /**
     * Run tasks with limited concurrency
     */
    suspend fun runLimitedAsync(
        concurrency: Int = 5,
        vararg tasks: suspend () -> Unit
    ) = coroutineScope {
        if (tasks.isEmpty()) return@coroutineScope

        val semaphore = Semaphore(concurrency)

        tasks.map { task ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        task()
                    } catch (e: Exception) {
                        Log.e(TAG, "Task failed: ${e.message}")
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Adaptive timeout based on provider history
     */
    fun getAdaptiveTimeout(providerId: String, baseTimeoutMs: Long = 15000): Long {
        val stats = StreamPlayCache.getProviderStats(providerId)

        if (stats.successCount == 0) return baseTimeoutMs

        if (stats.isCircuitBroken) return 5000L

        val avgTime = stats.avgTimeMs
        return when {
            avgTime == 0L -> baseTimeoutMs
            avgTime < 3000 -> max(avgTime + 2000, 5000)
            avgTime < 10000 -> avgTime + 5000
            else -> min(avgTime + 5000, baseTimeoutMs * 2)
        }
    }
}
