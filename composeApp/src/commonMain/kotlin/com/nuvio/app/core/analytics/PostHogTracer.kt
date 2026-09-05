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
import kotlin.random.Random

/**
 * Multiplatform OpenTelemetry (OTLP) HTTP trace forwarder for PostHog Tracing.
 * Batches structured spans and exports them to PostHog's `/i/v1/traces` endpoint.
 */
object PostHogTracer {
    private val log = Logger.withTag("PostHogTracer")
    private const val ENDPOINT = "https://us.i.posthog.com/i/v1/traces"
    private const val FLUSH_INTERVAL_MS = 3_000L
    private const val MAX_BATCH_SIZE = 50
    private const val MAX_QUEUE_CAPACITY = 300

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueMutex = Mutex()
    private val pendingSpans = mutableListOf<SpanRecord>()
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

    enum class SpanKind(val value: Int) {
        INTERNAL(1),
        SERVER(2),
        CLIENT(3),
        PRODUCER(4),
        CONSUMER(5)
    }

    enum class StatusCode(val value: Int) {
        UNSET(0),
        OK(1),
        ERROR(2)
    }

    data class SpanRecord(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String? = null,
        val name: String,
        val kind: SpanKind = SpanKind.INTERNAL,
        val startTimeNano: Long,
        val endTimeNano: Long,
        val attributes: Map<String, Any> = emptyMap(),
        val statusCode: StatusCode = StatusCode.OK,
        val statusMessage: String? = null,
        val exception: Throwable? = null
    )

    class Span(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String? = null,
        val name: String,
        val kind: SpanKind = SpanKind.INTERNAL,
        val startTimeNano: Long = platformCurrentTimeMillis() * 1_000_000L
    ) {
        private val attributes = mutableMapOf<String, Any>()
        private var statusCode = StatusCode.OK
        private var statusMessage: String? = null
        private var exception: Throwable? = null
        private var ended = false

        fun setAttribute(key: String, value: Any): Span {
            attributes[key] = value
            return this
        }

        fun setAttributes(map: Map<String, Any>): Span {
            attributes.putAll(map)
            return this
        }

        fun recordException(throwable: Throwable): Span {
            this.exception = throwable
            this.statusCode = StatusCode.ERROR
            this.statusMessage = throwable.message ?: throwable::class.simpleName
            attributes["exception.type"] = throwable::class.qualifiedName ?: "Exception"
            attributes["exception.message"] = throwable.message ?: ""
            return this
        }

        fun setStatus(code: StatusCode, message: String? = null): Span {
            this.statusCode = code
            this.statusMessage = message
            return this
        }

        fun end() {
            if (ended) return
            ended = true
            val endTimeNano = platformCurrentTimeMillis() * 1_000_000L
            val record = SpanRecord(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                name = name,
                kind = kind,
                startTimeNano = startTimeNano,
                endTimeNano = endTimeNano,
                attributes = attributes.toMap(),
                statusCode = statusCode,
                statusMessage = statusMessage,
                exception = exception
            )
            enqueue(record)
        }
    }

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
                var hasSpans = false
                queueMutex.withLock {
                    hasSpans = pendingSpans.isNotEmpty()
                }
                if (hasSpans) {
                    flushInternal()
                }
            }
        }
    }

    fun generateTraceId(): String {
        val bytes = Random.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun generateSpanId(): String {
        val bytes = Random.nextBytes(8)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun startSpan(
        name: String,
        traceId: String = generateTraceId(),
        parentSpanId: String? = null,
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, Any> = emptyMap()
    ): Span {
        val span = Span(
            traceId = traceId,
            spanId = generateSpanId(),
            parentSpanId = parentSpanId,
            name = name,
            kind = kind
        )
        if (attributes.isNotEmpty()) {
            span.setAttributes(attributes)
        }
        return span
    }

    inline fun <T> withSpan(
        name: String,
        parentSpanId: String? = null,
        traceId: String = generateTraceId(),
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, Any> = emptyMap(),
        block: (Span) -> T
    ): T {
        val span = startSpan(name = name, traceId = traceId, parentSpanId = parentSpanId, kind = kind, attributes = attributes)
        return try {
            block(span)
        } catch (e: Throwable) {
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }

    private fun enqueue(record: SpanRecord) {
        if (!isEnabled) return
        scope.launch {
            queueMutex.withLock {
                if (pendingSpans.size >= MAX_QUEUE_CAPACITY) {
                    pendingSpans.removeAt(0)
                }
                pendingSpans.add(record)
            }
        }
    }

    fun flush() {
        if (!isEnabled) return
        scope.launch {
            flushInternal()
        }
    }

    private suspend fun flushInternal() {
        if (!flushMutex.tryLock()) return
        try {
            val batch = mutableListOf<SpanRecord>()
            queueMutex.withLock {
                while (batch.size < MAX_BATCH_SIZE && pendingSpans.isNotEmpty()) {
                    batch.add(pendingSpans.removeAt(0))
                }
            }
            if (batch.isEmpty()) return

            val payload = buildOtlpTracesJson(batch)
            val jsonString = payload.toString()
            val url = "$ENDPOINT?token=${PostHogAnalytics.API_KEY}"
            val headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${PostHogAnalytics.API_KEY}",
                "User-Agent" to "KhaYin-Client/$appVersion ($platform; $osName $osVersion)"
            )

            val result = httpRequestRaw(
                url = url,
                method = "POST",
                headers = headers,
                body = jsonString.encodeToByteArray()
            )
            if (result.statusCode !in 200..299) {
                log.w { "PostHog traces export returned HTTP ${result.statusCode}: ${result.statusText}" }
            }
        } catch (e: Throwable) {
            log.w(e) { "Failed to flush PostHog traces batch" }
        } finally {
            flushMutex.unlock()
        }
    }

    private fun buildOtlpTracesJson(records: List<SpanRecord>): JsonObject {
        val resourceAttrs = buildJsonArray {
            add(buildJsonObject {
                put("key", "service.name")
                put("value", buildJsonObject { put("stringValue", serviceName) })
            })
            add(buildJsonObject {
                put("key", "platform")
                put("value", buildJsonObject { put("stringValue", platform) })
            })
            add(buildJsonObject {
                put("key", "device.type")
                put("value", buildJsonObject { put("stringValue", deviceType) })
            })
            add(buildJsonObject {
                put("key", "device.model")
                put("value", buildJsonObject { put("stringValue", deviceModel) })
            })
            add(buildJsonObject {
                put("key", "device.brand")
                put("value", buildJsonObject { put("stringValue", deviceBrand) })
            })
            add(buildJsonObject {
                put("key", "os.name")
                put("value", buildJsonObject { put("stringValue", osName) })
            })
            add(buildJsonObject {
                put("key", "os.version")
                put("value", buildJsonObject { put("stringValue", osVersion) })
            })
            add(buildJsonObject {
                put("key", "app.version")
                put("value", buildJsonObject { put("stringValue", appVersion) })
            })
            PostHogAnalytics.getDistinctId().takeIf { it.isNotBlank() }?.let { distinctId ->
                add(buildJsonObject {
                    put("key", "distinct_id")
                    put("value", buildJsonObject { put("stringValue", distinctId) })
                })
            }
        }

        val spansArray = buildJsonArray {
            records.forEach { record ->
                add(buildJsonObject {
                    put("traceId", record.traceId)
                    put("spanId", record.spanId)
                    if (record.parentSpanId != null) {
                        put("parentSpanId", record.parentSpanId)
                    }
                    put("name", record.name)
                    put("kind", record.kind.value)
                    put("startTimeUnixNano", record.startTimeNano.toString())
                    put("endTimeUnixNano", record.endTimeNano.toString())

                    put("attributes", buildJsonArray {
                        record.attributes.forEach { (key, value) ->
                            add(buildJsonObject {
                                put("key", key)
                                put("value", anyToJsonValue(value))
                            })
                        }
                    })

                    put("status", buildJsonObject {
                        put("code", record.statusCode.value)
                        if (record.statusMessage != null) {
                            put("message", record.statusMessage)
                        }
                    })

                    if (record.exception != null) {
                        put("events", buildJsonArray {
                            add(buildJsonObject {
                                put("timeUnixNano", record.endTimeNano.toString())
                                put("name", "exception")
                                put("attributes", buildJsonArray {
                                    add(buildJsonObject {
                                        put("key", "exception.type")
                                        put("value", buildJsonObject {
                                            put("stringValue", record.exception::class.qualifiedName ?: "Exception")
                                        })
                                    })
                                    add(buildJsonObject {
                                        put("key", "exception.message")
                                        put("value", buildJsonObject {
                                            put("stringValue", record.exception.message ?: "")
                                        })
                                    })
                                    add(buildJsonObject {
                                        put("key", "exception.stacktrace")
                                        put("value", buildJsonObject {
                                            put("stringValue", record.exception.stackTraceToString())
                                        })
                                    })
                                })
                            })
                        })
                    }
                })
            }
        }

        return buildJsonObject {
            put("resourceSpans", buildJsonArray {
                add(buildJsonObject {
                    put("resource", buildJsonObject {
                        put("attributes", resourceAttrs)
                    })
                    put("scopeSpans", buildJsonArray {
                        add(buildJsonObject {
                            put("scope", buildJsonObject {
                                put("name", "khayin-tracer")
                                put("version", appVersion)
                            })
                            put("spans", spansArray)
                        })
                    })
                })
            })
        }
    }

    private fun anyToJsonValue(value: Any?): JsonObject = buildJsonObject {
        when (value) {
            null -> put("stringValue", "")
            is Boolean -> put("boolValue", value)
            is Number -> put("intValue", value.toLong())
            is JsonElement -> {
                when (value) {
                    is JsonPrimitive -> {
                        value.booleanOrNull?.let { put("boolValue", it) }
                            ?: value.longOrNull?.let { put("intValue", it) }
                            ?: put("stringValue", value.content)
                    }
                    is JsonNull -> put("stringValue", "")
                    else -> put("stringValue", value.toString())
                }
            }
            else -> put("stringValue", value.toString())
        }
    }
}
