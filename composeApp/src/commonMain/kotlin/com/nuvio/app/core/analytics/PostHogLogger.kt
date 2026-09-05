package com.nuvio.app.core.analytics

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import kotlin.concurrent.Volatile

/**
 * Multiplatform OpenTelemetry (OTLP) HTTP log forwarder for PostHog Logs.
 * Batches structured application logs and sends them to PostHog's `/i/v1/logs` endpoint.
 */
object PostHogLogger {
    private val log = Logger.withTag("PostHogLogger")
    private const val ENDPOINT = "https://us.i.posthog.com/i/v1/logs"
    private const val FLUSH_INTERVAL_MS = 4_000L
    private const val MAX_BATCH_SIZE = 40
    private const val MAX_QUEUE_CAPACITY = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueMutex = Mutex()
    private val pendingLogs = mutableListOf<LogRecord>()
    private val flushMutex = Mutex()

    @Volatile
    private var isStarted = false
    var isEnabled: Boolean = true

    private var serviceName: String = "khayin-desktop"
    private var appVersion: String = ""
    private var platform: String = "Desktop"
    private var deviceType: String = "desktop"
    private var osName: String = "Unknown"
    private var osVersion: String = "Unknown"
    private var deviceModel: String = "Unknown"
    private var deviceBrand: String = "Unknown"

    data class LogRecord(
        val timestampNano: Long,
        val severityNumber: Int,
        val severityText: String,
        val message: String,
        val tag: String,
        val attributes: Map<String, Any> = emptyMap()
    )

    fun initialize(
        service: String = "khayin-desktop",
        version: String = "",
        platformName: String = "Desktop",
        deviceTypeName: String = "desktop",
        os: String = "Unknown",
        osVer: String = "Unknown",
        model: String = "Unknown",
        brand: String = "Unknown"
    ) {
        serviceName = service
        appVersion = version
        platform = platformName
        deviceType = deviceTypeName
        osName = os
        osVersion = osVer
        deviceModel = model
        deviceBrand = brand
        start()
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                var hasLogs = false
                queueMutex.withLock {
                    hasLogs = pendingLogs.isNotEmpty()
                }
                if (hasLogs) {
                    flushInternal()
                }
            }
        }
    }

    fun log(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any>? = null
    ) {
        if (!isEnabled || AppFeaturePolicy.isAdminClient) return

        val normLevel = level.uppercase()
        val (sevNum, sevText) = when (normLevel) {
            "TRACE" -> 1 to "TRACE"
            "DEBUG" -> 5 to "DEBUG"
            "INFO" -> 9 to "INFO"
            "WARN", "WARNING" -> 13 to "WARN"
            "ERROR" -> 17 to "ERROR"
            "FATAL" -> 21 to "FATAL"
            else -> 9 to "INFO"
        }

        val enrichedAttributes = mutableMapOf<String, Any>()
        enrichedAttributes["tag"] = tag
        enrichedAttributes["platform"] = platform
        enrichedAttributes["device.type"] = deviceType
        enrichedAttributes["distinct_id"] = PostHogAnalytics.getDistinctId()
        enrichedAttributes["session_id"] = PostHogAnalytics.getSessionId()

        if (attributes != null) {
            enrichedAttributes.putAll(attributes)
        }

        if (throwable != null) {
            enrichedAttributes["exception.type"] = throwable::class.simpleName ?: "Exception"
            enrichedAttributes["exception.message"] = throwable.message ?: ""
            enrichedAttributes["exception.stacktrace"] = throwable.stackTraceToString().take(4000)
        }

        val currentTimeMs = com.nuvio.app.features.watched.WatchedClock.nowEpochMs()
        val record = LogRecord(
            timestampNano = currentTimeMs * 1_000_000L,
            severityNumber = sevNum,
            severityText = sevText,
            message = message,
            tag = tag,
            attributes = enrichedAttributes
        )

        scope.launch {
            var shouldImmediateFlush = false
            queueMutex.withLock {
                if (pendingLogs.size >= MAX_QUEUE_CAPACITY) {
                    pendingLogs.removeAt(0) // Drop oldest
                }
                pendingLogs.add(record)
                shouldImmediateFlush = (sevNum >= 17 || pendingLogs.size >= MAX_BATCH_SIZE)
            }
            if (shouldImmediateFlush) {
                flushInternal()
            }
        }
    }

    fun flush() {
        scope.launch {
            flushInternal()
        }
    }

    private suspend fun flushInternal() {
        if (!flushMutex.tryLock()) return
        try {
            val batch = mutableListOf<LogRecord>()
            queueMutex.withLock {
                val takeCount = minOf(pendingLogs.size, MAX_BATCH_SIZE)
                for (i in 0 until takeCount) {
                    batch.add(pendingLogs.removeAt(0))
                }
            }
            if (batch.isEmpty()) return

            val payload = buildOtlpPayload(batch)
            val url = "$ENDPOINT?token=${PostHogAnalytics.API_KEY}"
            val headers = mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to "$serviceName/$appVersion"
            )

            val response = httpRequestRaw(
                method = "POST",
                url = url,
                headers = headers,
                body = payload.toString()
            )

            if (response.status !in 200..299) {
                log.w { "Failed to send logs to PostHog: ${response.status} ${response.body}" }
            }
        } catch (e: Throwable) {
            log.w { "Network error sending logs to PostHog: ${e.message}" }
        } finally {
            flushMutex.unlock()
        }
    }

    private fun buildOtlpPayload(records: List<LogRecord>): JsonObject {
        val resourceAttrs = buildJsonArray {
            add(buildAttr("service.name", serviceName))
            add(buildAttr("service.version", appVersion))
            add(buildAttr("deployment.environment", "production"))
            add(buildAttr("os.name", osName))
            add(buildAttr("os.version", osVersion))
            add(buildAttr("device.type", deviceType))
            add(buildAttr("device.platform", platform))
            add(buildAttr("device.model", deviceModel))
            add(buildAttr("device.brand", deviceBrand))
        }

        val logRecords = buildJsonArray {
            for (r in records) {
                val recObj = buildJsonObject {
                    put("timeUnixNano", r.timestampNano.toString())
                    put("observedTimeUnixNano", r.timestampNano.toString())
                    put("severityNumber", r.severityNumber)
                    put("severityText", r.severityText.lowercase())
                    put("body", buildJsonObject { put("stringValue", r.message) })
                    put("attributes", buildJsonArray {
                        for ((k, v) in r.attributes) {
                            add(buildAttr(k, v))
                        }
                    })
                }
                add(recObj)
            }
        }

        val scopeLogs = buildJsonArray {
            add(buildJsonObject {
                put("scope", buildJsonObject { put("name", "$serviceName-logger") })
                put("logRecords", logRecords)
            })
        }

        val resourceLogs = buildJsonArray {
            add(buildJsonObject {
                put("resource", buildJsonObject { put("attributes", resourceAttrs) })
                put("scopeLogs", scopeLogs)
            })
        }

        return buildJsonObject {
            put("resourceLogs", resourceLogs)
        }
    }

    private fun buildAttr(key: String, value: Any): JsonObject {
        return buildJsonObject {
            put("key", key)
            put("value", buildJsonObject {
                when (value) {
                    is Boolean -> put("boolValue", value)
                    is Number -> put("intValue", value.toLong())
                    else -> put("stringValue", value.toString())
                }
            })
        }
    }
}
