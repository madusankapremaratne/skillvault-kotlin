package com.knovik.skillvault.util

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import com.knovik.skillvault.data.entity.PerformanceMetric
import com.knovik.skillvault.data.repository.ResumeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Utility for benchmarking and performance monitoring.
 * Tracks metrics for research publication.
 */
@Singleton
class BenchmarkingUtil @Inject constructor(
    private val context: Context,
    private val resumeRepository: ResumeRepository,
) {

    /**
     * Measure execution time of a suspend function.
     */
    suspend inline fun <T> measureSuspend(
        label: String,
        block: suspend () -> T,
    ): Pair<T, Long> {
        val startTime = SystemClock.uptimeMillis()
        val result = block()
        val endTime = SystemClock.uptimeMillis()
        val executionTime = endTime - startTime
        
        Timber.d("$label executed in ${executionTime}ms")
        return Pair(result, executionTime)
    }

    /**
     * Measure execution time of a regular function.
     */
    inline fun <T> measure(
        label: String,
        block: () -> T,
    ): Pair<T, Long> {
        val executionTime = measureTimeMillis {
            block()
        }
        Timber.d("$label executed in ${executionTime}ms")
        return Pair(block(), executionTime)
    }

    /**
     * Get current memory statistics.
     */
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        val nativeHeap = Debug.getNativeHeap()
        val nativeHeapSize = nativeHeap.sumOf { it.size }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        return MemoryStats(
            javaHeapUsedMB = usedMemory / (1024 * 1024),
            javaHeapFreeMB = freeMemory / (1024 * 1024),
            javaHeapMaxMB = maxMemory / (1024 * 1024),
            nativeHeapSizeKB = nativeHeapSize / 1024,
            deviceTotalMemoryMB = memInfo.totalMem / (1024 * 1024),
            deviceAvailableMemoryMB = memInfo.availMem / (1024 * 1024),
        )
    }

    /**
     * Get battery information.
     */
    fun getBatteryStats(): BatteryStats? {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return null

            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            val voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
            val temperature = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE)
            val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)

            return BatteryStats(
                chargeCountermAh = level / 1000,
                energyCountermWh = capacity / 1000000,
                voltagemV = voltage,
                temperatureCelsius = temperature / 10,
                status = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    else -> "unknown"
                },
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to get battery stats")
            return null
        }
    }

    /**
     * Get device information for benchmarking context.
     */
    fun getDeviceInfo(): String {
        return """
            Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            CPU: ${android.os.Build.HARDWARE}
            RAM: ${getMemoryStats().deviceTotalMemoryMB}MB
        """.trimIndent()
    }

    /**
     * Record a performance metric to database.
     */
    suspend fun recordMetric(
        metricType: String,
        metricName: String,
        value: Double,
        unit: String,
    ) = withContext(Dispatchers.IO) {
        try {
            val stats = resumeRepository.getStorageStats()
            val metric = PerformanceMetric(
                metricType = metricType,
                metricName = metricName,
                value = value,
                unit = unit,
                resumeCount = stats["resumeCount"]?.toInt() ?: 0,
                embeddingCount = stats["embeddingCount"]?.toInt() ?: 0,
                deviceInfo = getDeviceInfo(),
            )
            resumeRepository.recordMetric(metric)
            Timber.d("Recorded metric: $metricName = $value$unit")
        } catch (e: Exception) {
            Timber.e(e, "Failed to record metric")
        }
    }

    /**
     * Generate a comprehensive benchmark report.
     */
    suspend fun generateBenchmarkReport(): BenchmarkReport = withContext(Dispatchers.Default) {
        val memoryStats = getMemoryStats()
        val batteryStats = getBatteryStats()
        val storageStats = resumeRepository.getStorageStats()

        BenchmarkReport(
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            memoryStats = memoryStats,
            batteryStats = batteryStats,
            resumeCount = storageStats["resumeCount"] ?: 0,
            embeddingCount = storageStats["embeddingCount"] ?: 0,
            totalTextSizeBytes = storageStats["totalTextSizeBytes"] ?: 0,
            embeddingStorageSizeBytes = storageStats["embeddingStorageSizeBytes"] ?: 0,
            estimatedTotalSizeBytes = storageStats["estimatedTotalSizeBytes"] ?: 0,
        )
    }

    /**
     * Export benchmark report as JSON for analysis.
     */
    fun exportReportAsJSON(report: BenchmarkReport): String {
        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${report.timestamp},")
            appendLine("  \"device\": \"${report.deviceInfo.replace("\"", "\\\"")}\",")
            appendLine("  \"memory\": {")
            appendLine("    \"javaHeapUsedMB\": ${report.memoryStats.javaHeapUsedMB},")
            appendLine("    \"deviceTotalMemoryMB\": ${report.memoryStats.deviceTotalMemoryMB},")
            appendLine("    \"deviceAvailableMemoryMB\": ${report.memoryStats.deviceAvailableMemoryMB}")
            appendLine("  },")
            appendLine("  \"data\": {")
            appendLine("    \"resumeCount\": ${report.resumeCount},")
            appendLine("    \"embeddingCount\": ${report.embeddingCount},")
            appendLine("    \"textSizeBytes\": ${report.totalTextSizeBytes},")
            appendLine("    \"embeddingStorageBytes\": ${report.embeddingStorageSizeBytes},")
            appendLine("    \"totalSizeBytes\": ${report.estimatedTotalSizeBytes},")
            appendLine("    \"compressionRatio\": ${if (report.totalTextSizeBytes > 0) report.embeddingStorageSizeBytes.toDouble() / report.totalTextSizeBytes else 0.0}")
            appendLine("  }")
            appendLine("}")
        }
    }
}

/**
 * Memory statistics data class.
 */
data class MemoryStats(
    val javaHeapUsedMB: Long,
    val javaHeapFreeMB: Long,
    val javaHeapMaxMB: Long,
    val nativeHeapSizeKB: Long,
    val deviceTotalMemoryMB: Long,
    val deviceAvailableMemoryMB: Long,
)

/**
 * Battery statistics data class.
 */
data class BatteryStats(
    val chargeCountermAh: Int,
    val energyCountermWh: Int,
    val voltagemV: Int,
    val temperatureCelsius: Int,
    val status: String,
)

/**
 * Comprehensive benchmark report.
 */
data class BenchmarkReport(
    val timestamp: Long,
    val deviceInfo: String,
    val memoryStats: MemoryStats,
    val batteryStats: BatteryStats? = null,
    val resumeCount: Long,
    val embeddingCount: Long,
    val totalTextSizeBytes: Long,
    val embeddingStorageSizeBytes: Long,
    val estimatedTotalSizeBytes: Long,
)
