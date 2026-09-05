package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.analytics.PostHogAnalytics
import com.nuvio.app.core.analytics.PostHogTracer
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor that creates OpenTelemetry trace spans and logs network errors / slow requests
 * directly into PostHog.
 */
class PostHogNetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val span = PostHogTracer.startSpan(
            name = "HTTP ${originalRequest.method}",
            kind = PostHogTracer.SpanKind.CLIENT,
            attributes = mapOf(
                "http.method" to originalRequest.method,
                "http.url" to scrubbedUrl(originalRequest.url)
            )
        )

        val request = if (originalRequest.header("traceparent") == null) {
            originalRequest.newBuilder()
                .header("traceparent", "00-${span.traceId}-${span.spanId}-01")
                .build()
        } else {
            originalRequest
        }

        val startedAtNs = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
            span.setAttribute("http.status_code", response.code)
            span.setAttribute("http.duration_ms", elapsedMs)

            if (response.code >= 400) {
                span.setStatus(PostHogTracer.StatusCode.ERROR, "HTTP ${response.code}")
                record(
                    url = request.url,
                    method = request.method,
                    statusCode = response.code,
                    elapsedMs = elapsedMs,
                    error = null
                )
            } else {
                span.setStatus(PostHogTracer.StatusCode.OK)
                if (elapsedMs > 3_000L) {
                    record(
                        url = request.url,
                        method = request.method,
                        statusCode = response.code,
                        elapsedMs = elapsedMs,
                        error = null
                    )
                }
            }
            span.end()
            return response
        } catch (error: Throwable) {
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
            span.setAttribute("http.duration_ms", elapsedMs)
            span.recordException(error)
            span.end()

            record(
                url = request.url,
                method = request.method,
                statusCode = null,
                elapsedMs = elapsedMs,
                error = error
            )
            throw error
        }
    }

    private fun record(
        url: HttpUrl,
        method: String,
        statusCode: Int?,
        elapsedMs: Long,
        error: Throwable?
    ) {
        val cleanUrl = scrubbedUrl(url)
        val level = when {
            error != null -> "ERROR"
            (statusCode ?: 200) >= 500 -> "ERROR"
            (statusCode ?: 200) >= 400 -> "WARN"
            elapsedMs > 3_000L -> "WARN"
            else -> "INFO"
        }

        val statusStr = statusCode?.toString() ?: "FAILED"
        val message = if (error != null) {
            "HTTP $method $cleanUrl failed after ${elapsedMs}ms: ${error.javaClass.simpleName} - ${error.message}"
        } else {
            "HTTP $method $cleanUrl returned $statusStr in ${elapsedMs}ms"
        }

        val attributes = buildMap<String, Any> {
            put("http.url", cleanUrl)
            put("http.method", method)
            put("http.host", url.host)
            put("http.path", url.encodedPath)
            put("http.elapsed_ms", elapsedMs)
            if (statusCode != null) put("http.status_code", statusCode)
            if (error != null) {
                put("error.type", error.javaClass.name)
                error.message?.let { put("error.message", it.take(300)) }
            }
        }

        PostHogAnalytics.log(
            level = level,
            tag = "HttpClient",
            message = message,
            throwable = error,
            properties = attributes
        )
    }

    private fun scrubbedUrl(url: HttpUrl): String {
        return url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }
}
