package com.nuvio.app.features.addons

import com.nuvio.app.features.license.LicenseStorage

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
    headers: Map<String, String> = emptyMap(),
): String {
    val mergedHeaders = buildMap {
        if (forceRefresh) {
            put("Cache-Control", "no-cache")
        }
        val licenseKey = runCatching { LicenseStorage.loadLastKnownKey()?.trim() }.getOrNull()
        if (!licenseKey.isNullOrBlank()) {
            put("X-License-Key", licenseKey)
            put("X-User-Key", licenseKey)
        }
        putAll(headers)
    }
    return if (mergedHeaders.isNotEmpty()) {
        httpGetTextWithHeaders(
            url = url,
            headers = mergedHeaders,
        )
    } else {
        httpGetText(url)
    }
}
