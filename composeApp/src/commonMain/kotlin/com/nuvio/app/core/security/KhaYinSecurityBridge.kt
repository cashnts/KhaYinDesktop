package com.nuvio.app.core.security

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.profiles.ProfilePinCrypto
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
object KhaYinSecurityBridge {
    private const val APP_HMAC_SECRET = "khayin_sec_k98_2026_m39_v1_live"
    private val json = Json { ignoreUnknownKeys = true }

    fun generateNonce(): String =
        Uuid.random().toString().replace("-", "")

    fun generateTimestamp(): Long =
        WatchProgressClock.nowEpochMs()

    fun computeSignature(
        method: String,
        url: String,
        body: String,
        nonce: String,
        timestamp: Long,
    ): String {
        val canonicalPayload = "$nonce:$timestamp:${method.uppercase()}:$url:$body:$APP_HMAC_SECRET"
        return ProfilePinCrypto.sha256Hex(canonicalPayload)
    }

    /**
     * Encrypts plain text payload using dynamic keystream derived from SHA-256(secret + nonce).
     */
    fun encryptPayload(plainText: String, nonce: String): String {
        if (plainText.isEmpty()) return ""
        val keyHash = ProfilePinCrypto.sha256Hex("$APP_HMAC_SECRET:$nonce")
        val keyBytes = keyHash.encodeToByteArray()
        val plainBytes = plainText.encodeToByteArray()
        val cipherBytes = ByteArray(plainBytes.size)

        for (i in plainBytes.indices) {
            cipherBytes[i] = (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        return Base64.encode(cipherBytes)
    }

    /**
     * Decrypts cipher text payload using dynamic keystream derived from SHA-256(secret + nonce).
     */
    fun decryptPayload(cipherBase64: String, nonce: String): String {
        if (cipherBase64.isBlank()) return ""
        return runCatching {
            val keyHash = ProfilePinCrypto.sha256Hex("$APP_HMAC_SECRET:$nonce")
            val keyBytes = keyHash.encodeToByteArray()
            val cipherBytes = Base64.decode(cipherBase64.trim())
            val plainBytes = ByteArray(cipherBytes.size)

            for (i in cipherBytes.indices) {
                plainBytes[i] = (cipherBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }

            plainBytes.decodeToString()
        }.getOrElse { cipherBase64 }
    }

    /**
     * Generates headers for user client network requests with nonce, timestamp, signature, and encryption flag.
     */
    fun buildSecureHeaders(
        method: String,
        url: String,
        body: String = "",
        baseHeaders: Map<String, String> = emptyMap(),
    ): Pair<Map<String, String>, String> {
        val headers = baseHeaders.toMutableMap()
        if (!AppFeaturePolicy.isUserClient) {
            return Pair(headers, body)
        }

        val nonce = generateNonce()
        val timestamp = generateTimestamp()
        val signature = computeSignature(method, url, body, nonce, timestamp)

        headers["X-KhaYin-Nonce"] = nonce
        headers["X-KhaYin-Timestamp"] = timestamp.toString()
        headers["X-KhaYin-Signature"] = signature
        headers["X-KhaYin-Client"] = "user"
        headers["X-KhaYin-Encrypted"] = "1"

        return Pair(headers, body)
    }
}
