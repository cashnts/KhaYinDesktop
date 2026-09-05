package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.analytics.PostHogAnalytics
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor that logs network errors (HTTP 4xx/5xx) and slow requests (> 3s)
 * directly into PostHog Logs.
 */
class PostHogNetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAtNs = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
            if (response.code >= 400 || elapsedMs > 3_000L) {
                record(
                    url = request.url,
                    method = request.method,
                    statusCode = response.code,
                    elapsedMs = elapsedMs,
                    error = null
                )
            }
            return response
        } catch (error: IOException) {
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
            record(
                url = request.url,
                method = request.method,
                statusCode = null,
                elapsedMs = elapsedMs,
                error = error
            )
            throw error
        } catch (error: RuntimeException) {
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
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
