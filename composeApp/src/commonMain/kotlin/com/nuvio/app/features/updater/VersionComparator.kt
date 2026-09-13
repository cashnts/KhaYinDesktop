package com.nuvio.app.features.updater

object VersionComparator {
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    fun parseVersionParts(raw: String?): List<Int>? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val parts = normalized.split('.', '-', '_')
            .filter { it.isNotBlank() }
            .mapNotNull { token -> token.takeWhile { it.isDigit() }.toIntOrNull() }

        return parts.takeIf { it.isNotEmpty() }
    }

    fun isRemoteNewer(remote: String?, local: String?): Boolean {
        val remoteParts = parseVersionParts(remote)
        val localParts = parseVersionParts(local)

        if (remoteParts == null || localParts == null) {
            val r = normalize(remote)
            val l = normalize(local)
            return r.isNotBlank() && l.isNotBlank() && r != l
        }

        val max = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until max) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /**
     * Compares two semantic version strings.
     * Returns a negative integer if v1 < v2, zero if v1 == v2, or a positive integer if v1 > v2.
     */
    fun compare(v1: String?, v2: String?): Int {
        val p1 = parseVersionParts(v1)
        val p2 = parseVersionParts(v2)

        if (p1 == null || p2 == null) {
            val s1 = normalize(v1)
            val s2 = normalize(v2)
            return s1.compareTo(s2)
        }

        val max = maxOf(p1.size, p2.size)
        for (i in 0 until max) {
            val n1 = p1.getOrElse(i) { 0 }
            val n2 = p2.getOrElse(i) { 0 }
            if (n1 != n2) return n1.compareTo(n2)
        }
        return 0
    }

    /**
     * Returns true if the clientVersion is unsupported given the threshold
     * (unsupported if clientVersion <= unsupportedVersionThreshold) or
     * minimum requirement (unsupported if clientVersion < minSupportedVersion).
     */
    fun isUnsupported(
        clientVersion: String?,
        unsupportedVersionThreshold: String?,
        minSupportedVersion: String? = null,
    ): Boolean {
        val current = normalize(clientVersion)
        if (current.isBlank()) return false

        val threshold = normalize(unsupportedVersionThreshold)
        if (threshold.isNotBlank()) {
            if (compare(current, threshold) <= 0) {
                return true
            }
        }

        val minVer = normalize(minSupportedVersion)
        if (minVer.isNotBlank()) {
            if (compare(current, minVer) < 0) {
                return true
            }
        }

        return false
    }
}
