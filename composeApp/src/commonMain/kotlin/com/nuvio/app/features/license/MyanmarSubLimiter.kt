package com.nuvio.app.features.license

import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Tracks and enforces Myanmar subtitle (MMSub) quotas:
 * - Plus License: Unlimited access
 * - Standard License: Excluded (no MMSub)
 * - Free Tier: Capped at 2 distinct movies per calendar day
 */
object MyanmarSubLimiter {
    const val MAX_DAILY_MOVIES = 2
    private val lock = SynchronizedObject()

    private fun loadTodayMovies(): Pair<String, MutableSet<String>> {
        val today = CurrentDateProvider.todayIsoDate()
        val raw = LicenseStorage.loadMmsubQuotaData() ?: return today to mutableSetOf()
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2 || parts[0] != today) {
            return today to mutableSetOf()
        }
        val ids = parts[1].split(",").filter { it.isNotBlank() }.toMutableSet()
        return today to ids
    }

    private fun saveTodayMovies(today: String, ids: Set<String>) {
        val payload = "$today|${ids.joinToString(",")}"
        LicenseStorage.saveMmsubQuotaData(payload)
    }

    fun canAccessMyanmarSub(contentId: String?): Boolean = synchronized(lock) {
        // Plus license has unlimited access
        if (LicenseRepository.isPlusMember) return true

        // Standard license does NOT include MMSub
        if (LicenseRepository.isLicensed) return false

        // Free tier: allow up to MAX_DAILY_MOVIES per day
        val (today, currentSet) = loadTodayMovies()

        if (!contentId.isNullOrBlank() && currentSet.contains(contentId)) {
            return true
        }

        return currentSet.size < MAX_DAILY_MOVIES
    }

    fun recordMyanmarSubUsed(contentId: String?) = synchronized(lock) {
        if (contentId.isNullOrBlank()) return
        if (LicenseRepository.isLicensed) return

        val (today, currentSet) = loadTodayMovies()
        if (!currentSet.contains(contentId)) {
            currentSet.add(contentId)
            saveTodayMovies(today, currentSet)
        }
    }

    fun recordMovieAccess(contentId: String?) {
        recordMyanmarSubUsed(contentId)
    }

    fun getRemainingMoviesToday(): Int = synchronized(lock) {
        if (LicenseRepository.isPlusMember) return Int.MAX_VALUE
        if (LicenseRepository.isLicensed) return 0

        val (_, currentSet) = loadTodayMovies()
        return (MAX_DAILY_MOVIES - currentSet.size).coerceAtLeast(0)
    }

    fun isMyanmarSubtitle(
        code: String?,
        label: String? = null,
        addon: String? = null,
        url: String? = null,
    ): Boolean {
        val cleanCode = code?.trim()?.lowercase().orEmpty()
        val cleanLabel = label?.trim()?.lowercase().orEmpty()
        val cleanAddon = addon?.trim()?.lowercase().orEmpty()
        val cleanUrl = url?.trim()?.lowercase().orEmpty()
        val combined = "$cleanCode $cleanLabel $cleanAddon $cleanUrl"

        return cleanCode == "my" || cleanCode == "mya" || cleanCode == "bur" ||
            cleanLabel.contains("myanmar") || cleanLabel.contains("burmese") ||
            cleanLabel.contains("mmsub") || cleanLabel.contains("မြန်မာ") ||
            combined.contains("burmese") || combined.contains("myanmar") ||
            combined.contains("mmsub") || combined.contains("မြန်မာ") ||
            cleanUrl.contains("stream.khayin.net")
    }
}
