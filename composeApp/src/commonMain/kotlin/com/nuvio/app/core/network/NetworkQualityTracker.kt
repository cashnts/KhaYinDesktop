package com.nuvio.app.core.network

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

enum class NetworkQualityTier(val displayName: String) {
    EXCELLENT("Excellent"), // < 150ms latency, high bandwidth
    GOOD("Good"),           // 150ms - 350ms latency
    MODERATE("Moderate"),   // 350ms - 650ms latency
    POOR("Poor")            // > 650ms latency or high failure/timeout rate
}

object NetworkQualityTracker {
    private val log = Logger.withTag("NetworkQualityTracker")
    private val lock = SynchronizedObject()

    private val _qualityState = MutableStateFlow(NetworkQualityTier.GOOD)
    val qualityState: StateFlow<NetworkQualityTier> = _qualityState.asStateFlow()

    private val rollingLatencies = mutableListOf<Long>()
    private const val MAX_SAMPLES = 15

    fun recordProbeLatency(latencyMs: Long, isSuccess: Boolean) {
        val effectiveLatency = if (!isSuccess) 2000L else latencyMs.coerceAtLeast(10L)
        val newTier = synchronized(lock) {
            if (rollingLatencies.size >= MAX_SAMPLES) {
                rollingLatencies.removeAt(0)
            }
            rollingLatencies.add(effectiveLatency)

            val avgLatency = rollingLatencies.average().roundToLong()
            when {
                avgLatency < 150L -> NetworkQualityTier.EXCELLENT
                avgLatency < 350L -> NetworkQualityTier.GOOD
                avgLatency < 650L -> NetworkQualityTier.MODERATE
                else -> NetworkQualityTier.POOR
            }
        }

        if (_qualityState.value != newTier) {
            log.i { "Network quality transition: ${_qualityState.value} -> $newTier (samples=${rollingLatencies.size})" }
            _qualityState.value = newTier
        }
    }

    val currentTier: NetworkQualityTier
        get() = _qualityState.value

    val averageLatencyMs: Long
        get() = synchronized(lock) {
            if (rollingLatencies.isEmpty()) 200L else rollingLatencies.average().roundToLong()
        }

    fun isPoorLine(): Boolean = currentTier == NetworkQualityTier.POOR
    fun isModerateOrPoorLine(): Boolean = currentTier == NetworkQualityTier.POOR || currentTier == NetworkQualityTier.MODERATE
}
