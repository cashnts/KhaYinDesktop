package com.nuvio.app.features.details

import com.nuvio.app.features.streams.StreamItem

/**
 * Utilities for cleaning live stream / IPTV channel titles, descriptions,
 * genres, and stream source labels.
 */
object LiveMediaCleaner {

    private val KNOWN_ACRONYMS = setOf(
        "F1", "HD", "FHD", "4K", "UHD", "SD", "TV", "BBC", "ITV", "TNT", "ESPN",
        "NBC", "CBS", "ABC", "FOX", "WWE", "UFC", "NFL", "NBA", "MLB", "NHL",
        "MLS", "PGA", "DAZN", "MUTV", "LFCTV", "TSN", "SN", "BT", "USA", "UK",
        "US", "CNBC", "MSNBC", "CNN", "HBO", "MTV", "VH1", "TLC", "TBS", "HGTV",
        "AXS", "BEIN", "FS1", "FS2", "SEC", "ACC", "BIG10", "B1G", "IPL", "ICC",
        "EPL", "WTA", "ATP", "PPV", "NASCAR", "INDYCAR", "MOTOGP", "WRC", "RTE",
        "TF1", "M6", "RTL", "ZDF", "ARD", "ORF", "SRF", "RAI",
    )

    private val LEADING_TAG_REGEX = Regex(
        pattern = """^(?:(?:\((?:FHD|HD|SD|4K|UHD|1080p|720p|480p|HEVC|H\.?264|H\.?265|60FPS|RAW)\)|\[(?:FHD|HD|SD|4K|UHD|1080p|720p|480p|HEVC|H\.?264|H\.?265|60FPS|RAW)\]|NOW|LIVE)\s*[:\-\|]?\s*)+""",
        option = RegexOption.IGNORE_CASE,
    )

    private val TRAILING_TAG_REGEX = Regex(
        pattern = """(?:\s*(?:\[|\()?(?:RAW|FHD|HD|SD|4K|UHD|1080p|720p|480p|HEVC|H\.?264|H\.?265|60FPS)(?:\]|\))?|\s*[:\-\|]\s*)+$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val JUNK_GENRE_SLUG_REGEX = Regex(
        pattern = """^(?:(?:NOW|F1|CH|STREAM|IPTV|EVENT)-[A-Za-z0-9\-]+|[A-Za-z0-9]+-[0-9]{4,})$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val JUNK_DESC_LINE_REGEX = Regex(
        pattern = """^(?:Live\s+IPTV\s+Recording|Live\s+on\s+[A-Za-z0-9\-_]+|LIVE\s+NOW|Live\s+stream\s+recorded.*|Channel\s*ID:?.*|Stream\s*ID:?.*|Recorded\s*by.*)$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val LIVE_CHANNEL_KEYWORDS_REGEX = Regex(
        pattern = """\b(?:SKY\s+SPORTS?|TNT\s+SPORTS?|ESPN|FOX\s+SPORTS?|BEIN|PREMIER\s+LEAGUE|EUROSPORT|BT\s+SPORT|DAZN|MUTV|LFCTV|NBC\s+SPORTS?|CBS\s+SPORTS?|SUPERSPORT|SONY\s+LIV|SONY\s+TEN|STAR\s+SPORTS?|CANAL\+|RMC\s+SPORT|SETANTA|WILLOW|F1\s+TV|FORMULA\s*1|IPTV|CHANNELS?|LIVE\s+STREAM)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    private val SPORTS_CATALOG_KEYWORDS = Regex(
        pattern = """\b(?:SPORTS?|LIVE\s+SPORTS?|FOOTBALL|SOCCER|F1|FORMULA\s*1|MOTORSPORTS?|BASKETBALL|NBA|CRICKET|TENNIS|UFC|MMA|BOXING|RACING|IPTV|CHANNELS?|LIVE\s+TV)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    /**
     * Determines whether the given media metadata or playback context corresponds
     * to a live stream / IPTV source.
     */
    fun isLive(
        type: String? = null,
        parentMetaType: String? = null,
        streamType: String? = null,
        releaseInfo: String? = null,
        status: String? = null,
        title: String? = null,
        streamTitle: String? = null,
        description: String? = null,
        pauseDescription: String? = null,
        sourceUrl: String? = null,
        durationMs: Long = 0L,
    ): Boolean {
        val t = type?.trim()?.lowercase()
        val pmt = parentMetaType?.trim()?.lowercase()
        val st = streamType?.trim()?.lowercase()
        val rel = releaseInfo?.trim()?.lowercase()
        val stat = status?.trim()?.lowercase()

        val liveTypes = setOf("tv", "channel", "live", "stream", "iptv", "sports")
        if (t in liveTypes || pmt in liveTypes || st in liveTypes || rel == "live" || stat == "live" || rel == "live now" || stat == "live now") {
            return true
        }

        val desc = listOfNotNull(description, pauseDescription).joinToString("\n")
        if (desc.lines().any { it.trim().matches(JUNK_DESC_LINE_REGEX) } ||
            desc.contains("live iptv", ignoreCase = true) ||
            desc.contains("live now", ignoreCase = true) ||
            desc.contains("live on ", ignoreCase = true)
        ) {
            return true
        }

        val allTitles = listOfNotNull(title, streamTitle).joinToString(" ")
        if (allTitles.isNotBlank()) {
            val titLower = allTitles.lowercase().trim()
            if (titLower.startsWith("now:") || titLower.startsWith("live:") || titLower.startsWith("now :") || titLower.startsWith("live :") || titLower.contains("now: sky") || titLower.contains("live:")) {
                return true
            }
            if (allTitles.contains(LIVE_CHANNEL_KEYWORDS_REGEX)) {
                return true
            }
        }

        val url = sourceUrl?.lowercase().orEmpty()
        if (url.contains("/live/") || url.contains("/iptv/") || url.contains("/channels/") || url.contains("livestream") || url.endsWith("live.m3u8")) {
            return true
        }

        // HLS sliding window check: duration <= 3 minutes (180s) and non-zero
        if (durationMs in 1L..180_000L && (url.contains(".m3u8") || url.contains("/hls/") || allTitles.contains(LIVE_CHANNEL_KEYWORDS_REGEX))) {
            return true
        }

        return false
    }

    /**
     * Determines whether a catalog is a sports or live stream catalog.
     */
    fun isSportsCatalog(
        type: String? = null,
        name: String? = null,
        catalogId: String? = null,
        addonName: String? = null,
    ): Boolean {
        val t = type?.trim()?.lowercase().orEmpty()
        if (t in setOf("sports", "sport", "live", "tv", "channel", "channels", "iptv")) {
            return true
        }
        val combined = listOfNotNull(name, catalogId, addonName).joinToString(" ")
        return combined.contains(SPORTS_CATALOG_KEYWORDS) || combined.contains(LIVE_CHANNEL_KEYWORDS_REGEX)
    }

    /**
     * Determines whether a media item or stream is sports/live content.
     */
    fun isSportsItem(
        type: String? = null,
        title: String? = null,
        genres: List<String> = emptyList(),
        description: String? = null,
    ): Boolean {
        if (isLive(type = type, title = title, description = description)) return true
        val combined = listOfNotNull(title, type).plus(genres).joinToString(" ")
        return combined.contains(SPORTS_CATALOG_KEYWORDS) || combined.contains(LIVE_CHANNEL_KEYWORDS_REGEX)
    }

    /**
     * Cleans titles by removing tags such as `NOW:`, `(FHD) :`, `[RAW]`, trailing `RAW`,
     * and applies clean title capitalization when necessary.
     */
    fun cleanTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        var cleaned = rawTitle.trim()

        // Repeatedly strip leading tags
        var previous: String
        do {
            previous = cleaned
            cleaned = cleaned.replace(LEADING_TAG_REGEX, "").trim()
        } while (cleaned != previous && cleaned.isNotBlank())

        // Repeatedly strip trailing tags
        do {
            previous = cleaned
            cleaned = cleaned.replace(TRAILING_TAG_REGEX, "").trim()
        } while (cleaned != previous && cleaned.isNotBlank())

        if (cleaned.isBlank()) return rawTitle.trim()

        val letters = cleaned.filter { it.isLetter() }
        val isAllUpper = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        val isAllLower = letters.isNotEmpty() && letters.all { it.isLowerCase() }

        return if (isAllUpper || isAllLower) {
            smartTitleCase(cleaned)
        } else {
            cleaned
        }
    }

    /**
     * Cleans stream source names (e.g. `NOW: SKY SPORTS PREMIER LEA... | football` -> `Sky Sports Premier Lea... | Football`).
     */
    fun cleanStreamLabel(rawLabel: String?): String {
        if (rawLabel.isNullOrBlank()) return "Stream"
        val trimmed = rawLabel.trim()

        if (trimmed.contains('|')) {
            val parts = trimmed.split('|')
            return parts.map { cleanTitle(it.trim()) }.filter { it.isNotBlank() }.joinToString(" | ")
        }

        return cleanTitle(trimmed)
    }

    /**
     * Cleans descriptions by removing automated scraper / IPTV boilerplate lines
     * (e.g. `Live IPTV Recording`, `Live on NOW-SKY-SPORTS...`, `LIVE NOW`).
     */
    fun cleanDescription(rawDescription: String?, title: String?, isLive: Boolean = false): String? {
        if (rawDescription.isNullOrBlank()) {
            return if (isLive && !title.isNullOrBlank()) "Live broadcast on ${cleanTitle(title)}." else null
        }

        val usefulLines = rawDescription.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() && !line.matches(JUNK_DESC_LINE_REGEX)
            }

        if (usefulLines.isEmpty()) {
            return if (isLive && !title.isNullOrBlank()) "Live broadcast on ${cleanTitle(title)}." else null
        }

        return usefulLines.joinToString("\n")
    }

    /**
     * Filters out raw channel slugs / IDs accidentally placed in the genres list
     * (e.g. `NOW-SKY-SPORTS-PREMIER-LEAGUE`, `F1-3949409`).
     */
    fun cleanGenres(genres: List<String>): List<String> {
        return genres
            .map { it.trim() }
            .filter { genre ->
                genre.isNotBlank() &&
                    !genre.matches(JUNK_GENRE_SLUG_REGEX) &&
                    !(genre.contains('-') && genre.any { it.isDigit() } && genre.length > 6)
            }
            .map { smartTitleCase(it) }
            .distinct()
    }

    /**
     * Capitalizes words nicely while preserving acronyms like F1, HD, ESPN, BBC, etc.
     */
    fun smartTitleCase(input: String): String {
        if (input.isBlank()) return ""

        val words = input.split(Regex("\\s+"))
        return words.joinToString(" ") { word ->
            val cleanWord = word.trim()
            val upper = cleanWord.uppercase()

            if (upper in KNOWN_ACRONYMS) {
                upper
            } else if (cleanWord.all { it.isLetter() }) {
                cleanWord.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else if (cleanWord.contains('-')) {
                cleanWord.split('-').joinToString("-") { part ->
                    val partUpper = part.uppercase()
                    if (partUpper in KNOWN_ACRONYMS) partUpper
                    else part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            } else {
                val lettersOnly = cleanWord.filter { it.isLetter() }
                if (lettersOnly.isNotEmpty() && lettersOnly.all { it.isUpperCase() }) {
                    cleanWord.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    cleanWord
                }
            }
        }
    }
}
