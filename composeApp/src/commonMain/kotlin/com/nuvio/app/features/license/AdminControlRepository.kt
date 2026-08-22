package com.nuvio.app.features.license

import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseConfig
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SystemServiceConfig(
    val maintenanceMode: Boolean = false,
    val streamingDisabled: Boolean = false,
    val broadcastMessage: String = "",
    val broadcastTimestamp: Long = 0L,
    val presetAddons: List<String> = emptyList(),
)

object AdminControlRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val _config = MutableStateFlow(SystemServiceConfig())
    val config: StateFlow<SystemServiceConfig> = _config.asStateFlow()

    private val _dismissedBroadcastTimestamp = MutableStateFlow(0L)
    val dismissedBroadcastTimestamp: StateFlow<Long> = _dismissedBroadcastTimestamp.asStateFlow()

    fun dismissBroadcast(timestamp: Long) {
        _dismissedBroadcastTimestamp.value = timestamp
    }

    private fun supabaseRestUrl(): String {
        val custom = ServerConfigurationRepository.active.value.backendUrl.trimEnd('/')
        val base = if (custom.isNotBlank()) custom else SupabaseConfig.URL
        return "$base/rest/v1"
    }

    private fun supabaseHeaders(): Map<String, String> {
        val key = ServerConfigurationRepository.active.value.publishableKey.ifBlank { SupabaseConfig.ANON_KEY }
        val token = runCatching { SupabaseProvider.client.auth.currentAccessTokenOrNull() }.getOrNull()?.takeIf { it.isNotBlank() } ?: key
        return mapOf(
            "apikey" to key,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation,resolution=merge-duplicates",
        )
    }

    suspend fun fetchConfig(): SystemServiceConfig {
        return runCatching {
            val restUrl = supabaseRestUrl()
            val response = httpRequestRaw(
                method = "GET",
                url = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG&select=*",
                headers = supabaseHeaders(),
                body = "",
            )
            if (response.status in 200..299 && !response.body.startsWith("<")) {
                val records = json.decodeFromString<List<SupabaseLicenseRecord>>(response.body)
                if (records.isNotEmpty()) {
                    val notes = records.first().notes
                    if (!notes.isNullOrBlank()) {
                        val parsed = json.decodeFromString<SystemServiceConfig>(notes)
                        _config.value = parsed
                        return@runCatching parsed
                    }
                }
            }
            _config.value
        }.getOrElse { _config.value }
    }

    suspend fun updateConfig(newConfig: SystemServiceConfig): Result<SystemServiceConfig> {
        return runCatching {
            val restUrl = supabaseRestUrl()
            val payload = SupabaseLicenseRecord(
                key = "SYSTEM_CONFIG",
                status = "config",
                customerName = "System",
                tier = "system",
                notes = json.encodeToString(newConfig),
            )
            val body = json.encodeToString(payload)
            val response = httpRequestRaw(
                method = "POST",
                url = "$restUrl/license_keys",
                headers = supabaseHeaders(),
                body = body,
            )
            if (response.status !in 200..299) {
                httpRequestRaw(
                    method = "PATCH",
                    url = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG",
                    headers = supabaseHeaders(),
                    body = body,
                )
            }
            _config.value = newConfig
            newConfig
        }
    }
}
