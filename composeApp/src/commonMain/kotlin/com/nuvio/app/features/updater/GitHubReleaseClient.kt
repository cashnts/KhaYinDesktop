package com.nuvio.app.features.updater

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

class GitHubReleaseClient(
    private val owner: String,
    private val repo: String,
    private val userAgent: String = "KhaYin-Updater",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 30_000L
        }
    }

    suspend fun getLatestRelease(includePrereleases: Boolean = false): GitHubReleaseDto? {
        val baseUrl = "https://api.github.com/repos/$owner/$repo"

        // 1. Try /releases/latest first
        val latestResult = runCatching {
            val response = httpClient.get("$baseUrl/releases/latest") {
                header("User-Agent", userAgent)
                header("Accept", "application/vnd.github+json")
            }
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<GitHubReleaseDto>(response.bodyAsText())
            } else {
                null
            }
        }.getOrNull()

        if (latestResult != null && (!latestResult.prerelease || includePrereleases) && !latestResult.draft) {
            return latestResult
        }

        // 2. Fallback to /releases list
        return runCatching {
            val response = httpClient.get("$baseUrl/releases") {
                header("User-Agent", userAgent)
                header("Accept", "application/vnd.github+json")
            }
            if (response.status == HttpStatusCode.OK) {
                val releases = json.decodeFromString<List<GitHubReleaseDto>>(response.bodyAsText())
                releases.firstOrNull { release ->
                    !release.draft && (includePrereleases || !release.prerelease) && release.assets.isNotEmpty()
                }
            } else {
                null
            }
        }.getOrNull()
    }
}
