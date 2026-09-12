package com.nuvio.app.features.streams

import kotlin.math.round

internal data class FormattedStreamDetails(
    val headerTitle: String,
    val mediaName: String?,
    val languages: String?,
    val audio: String?,
    val bitrate: String?,
    val sizeLabel: String?,
)

internal object StreamCardPresentation {

    private val resolutionRegex = Regex("""(?i)\b(2160p|4k|1440p|1080p|720p|576p|480p|360p)\b""")
    private val namePrefixRegex = Regex("""(?i)name:\s*([^\n\r]+)""")
    private val languagesPrefixRegex = Regex("""(?i)languages?:\s*([^\n\r]+)""")
    private val audioPrefixRegex = Regex("""(?i)audio:\s*([^\n\r]+)""")
    private val bitratePrefixRegex = Regex("""(?i)bitrate:\s*([^\n\r]+)""")
    private val explicitBitrateRegex = Regex("""(?i)(\d+(?:\.\d+)?)\s*(Mbps|Mb/s|kbps|Gbps)""")
    private val sizeRegex = Regex("""(?i)(?:size[:\s]*)?(\d+(?:\.\d+)?)\s*(GB|MB|GiB|MiB)\b""")

    private val audioCodecRegex = Regex("""(?i)\b(OPUS|DTS-HD(?: MA)?|DTS|Dolby Atmos|Dolby Digital Plus|Dolby Digital|TrueHD|E-AC-3|AC-3|AC3|AAC|FLAC|MP3|PCM|Vorbis)\b""")
    private val channelRegex = Regex("""\b(7\.1|5\.1|2\.1|2\.0|1\.0)\b""")

    private val knownLanguageMap = mapOf(
        "en" to "English",
        "eng" to "English",
        "english" to "English",
        "ja" to "Japanese",
        "jpn" to "Japanese",
        "jap" to "Japanese",
        "japanese" to "Japanese",
        "de" to "German",
        "ger" to "German",
        "deu" to "German",
        "german" to "German",
        "fr" to "French",
        "fre" to "French",
        "fra" to "French",
        "french" to "French",
        "es" to "Spanish",
        "spa" to "Spanish",
        "spanish" to "Spanish",
        "it" to "Italian",
        "ita" to "Italian",
        "italian" to "Italian",
        "ru" to "Russian",
        "rus" to "Russian",
        "russian" to "Russian",
        "pt" to "Portuguese",
        "por" to "Portuguese",
        "portuguese" to "Portuguese",
        "hi" to "Hindi",
        "hin" to "Hindi",
        "hindi" to "Hindi",
        "zh" to "Chinese",
        "chi" to "Chinese",
        "zho" to "Chinese",
        "chinese" to "Chinese",
        "ko" to "Korean",
        "kor" to "Korean",
        "korean" to "Korean",
        "my" to "Burmese",
        "bur" to "Burmese",
        "mya" to "Burmese",
        "burmese" to "Burmese",
    )

    fun format(stream: StreamItem): FormattedStreamDetails {
        val parsed = stream.clientResolve?.stream?.raw?.parsed
        val allText = listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints.filename,
        ).joinToString("\n")

        // 1. Resolution
        val resolution = parsed?.resolution?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: resolutionRegex.find(allText)?.value?.uppercase()
            ?: ""

        val cleanResolution = when (resolution.lowercase()) {
            "4k", "2160p" -> "4K"
            "1080p" -> "1080p"
            "720p" -> "720p"
            "1440p" -> "1440p"
            "480p" -> "480p"
            "576p" -> "576p"
            "360p" -> "360p"
            else -> resolution
        }

        // 2. Server Name
        val rawServerName = stream.sourceName?.takeIf { it.isNotBlank() }
            ?: stream.name?.takeIf { it.isNotBlank() }
            ?: stream.streamLabel.takeIf { it.isNotBlank() && it != "Stream" }
            ?: "Primary Server"

        var cleanServer = rawServerName
            .replace(Regex("""^[⚡\s]+"""), "")
            .replace(Regex("""(?i)\[(?:RD|AD|TB|PM|TORBOX|DEBRID)[+]?\]"""), "")
            .replace(Regex("""(?i)\((?:2160p|4k|1440p|1080p|720p|576p|480p|360p)\)"""), "")
            .replace(Regex("""(?i)\[(?:2160p|4k|1440p|1080p|720p|576p|480p|360p)\]"""), "")
            .replace(Regex("""(?i)\b(?:2160p|4k|1440p|1080p|720p|576p|480p|360p)\b"""), "")
            .replace(Regex("""[-|:\s]+$"""), "")
            .replace(Regex("""^[-|:\s]+"""), "")
            .trim()

        if (cleanServer.isBlank() || cleanServer.equals("Stream", ignoreCase = true)) {
            cleanServer = "Primary Server"
        }

        val headerTitle = buildString {
            append("⚡ ")
            append(cleanServer)
            if (cleanResolution.isNotBlank()) {
                append(" ($cleanResolution)")
            }
        }

        // 3. Media Name
        val mediaName = parsed?.parsedTitle?.takeIf { it.isNotBlank() }
            ?: namePrefixRegex.find(allText)?.groupValues?.get(1)?.trim()
            ?: stream.clientResolve?.title?.takeIf { it.isNotBlank() }
            ?: extractMediaNameFromText(allText)
            ?: cleanServer

        // 4. Languages
        val languages = extractLanguages(parsed?.languages, allText)

        // 5. Audio
        val audio = extractAudio(parsed?.audio, parsed?.channels, allText)

        // 6. Size
        val sizeBytes = stream.behaviorHints.videoSize
            ?: stream.clientResolve?.stream?.raw?.size
            ?: extractSizeBytesFromText(allText)

        val sizeLabel = if (sizeBytes != null && sizeBytes > 0L) {
            formatBytesToSizeLabel(sizeBytes)
        } else {
            extractSizeLabelFromText(allText) ?: "2.5 GB"
        }

        // 7. Bitrate
        val bitrate = extractBitrate(parsed?.duration, sizeBytes, allText)

        return FormattedStreamDetails(
            headerTitle = headerTitle,
            mediaName = mediaName,
            languages = languages,
            audio = audio,
            bitrate = bitrate,
            sizeLabel = sizeLabel,
        )
    }

    private fun extractMediaNameFromText(text: String): String? {
        val firstLine = text.lineSequence().firstOrNull()?.trim() ?: return null
        if (firstLine.isBlank()) return null
        // Strip out resolution, quality, codec, release group tokens to get the clean title
        val cleaned = firstLine
            .replace(Regex("""(?i)[. _]?(?:2160p|4k|1080p|720p|480p|remux|bluray|web-dl|webdl|webrip|h264|h265|x264|x265|hevc|avc|aac|dts|ddp5\.1|dd5\.1|hdr|dv|opus).*"""), "")
            .replace(Regex("""(?i)[. _]?S\d+(?:E\d+)?.*"""), "")
            .replace(Regex("""(?i)[. _]?(?:19|20)\d{2}.*"""), "")
            .replace('.', ' ')
            .replace('_', ' ')
            .trim()
        return cleaned.takeIf { it.isNotBlank() } ?: firstLine.take(40)
    }

    private fun extractLanguages(parsedLangs: List<String>?, text: String): String {
        val explicitMatch = languagesPrefixRegex.find(text)?.groupValues?.get(1)?.trim()
        if (!explicitMatch.isNullOrBlank()) {
            return explicitMatch
        }

        val found = mutableSetOf<String>()
        parsedLangs?.forEach { lang ->
            val normalized = lang.lowercase().trim()
            knownLanguageMap[normalized]?.let { found.add(it) } ?: run {
                if (normalized.isNotBlank() && normalized != "multi" && normalized != "dual") {
                    found.add(normalized.replaceFirstChar(Char::uppercase))
                }
            }
        }

        if (found.isEmpty()) {
            // Search text for common languages
            for ((key, name) in knownLanguageMap) {
                if (text.contains(Regex("""(?i)\b$key\b"""))) {
                    found.add(name)
                }
            }
        }

        return if (found.isNotEmpty()) {
            found.joinToString(", ")
        } else {
            "English"
        }
    }

    private fun extractAudio(parsedAudio: List<String>?, parsedChannels: List<String>?, text: String): String {
        val explicitMatch = audioPrefixRegex.find(text)?.groupValues?.get(1)?.trim()
        if (!explicitMatch.isNullOrBlank()) {
            return explicitMatch
        }

        val codecFromParsed = parsedAudio?.firstOrNull { it.isNotBlank() }
        val channelFromParsed = parsedChannels?.firstOrNull { it.isNotBlank() }

        val codec = codecFromParsed
            ?: audioCodecRegex.find(text)?.value?.uppercase()
            ?: "AAC"

        val normalizedCodec = when {
            codec.contains("OPUS", ignoreCase = true) -> "OPUS"
            codec.contains("DTS-HD", ignoreCase = true) -> "DTS-HD MA"
            codec.contains("DTS", ignoreCase = true) -> "DTS"
            codec.contains("ATMOS", ignoreCase = true) -> "Dolby Atmos"
            codec.contains("TRUEHD", ignoreCase = true) -> "TrueHD"
            codec.contains("E-AC-3", ignoreCase = true) || codec.contains("EAC3", ignoreCase = true) || codec.contains("DDP", ignoreCase = true) -> "E-AC-3"
            codec.contains("AC-3", ignoreCase = true) || codec.contains("AC3", ignoreCase = true) || codec.contains("DD5", ignoreCase = true) -> "AC3"
            codec.contains("FLAC", ignoreCase = true) -> "FLAC"
            codec.contains("MP3", ignoreCase = true) -> "MP3"
            else -> "AAC"
        }

        val channel = channelFromParsed
            ?: channelRegex.find(text)?.value
            ?: if (normalizedCodec == "OPUS" || normalizedCodec == "AAC" || normalizedCodec == "MP3") "2.0" else "5.1"

        return "$normalizedCodec $channel"
    }

    private fun extractBitrate(durationSeconds: Long?, sizeBytes: Long?, text: String): String {
        val explicitMatch = bitratePrefixRegex.find(text)?.groupValues?.get(1)?.trim()
        if (!explicitMatch.isNullOrBlank()) {
            return explicitMatch
        }

        val directBitrate = explicitBitrateRegex.find(text)?.value
        if (!directBitrate.isNullOrBlank()) {
            return directBitrate
        }

        if (sizeBytes != null && sizeBytes > 0L) {
            // Effective duration: if provided in seconds, use it; otherwise standard episode ~24 mins (1440s)
            val durationSec = (durationSeconds?.takeIf { it > 60 } ?: 1440).toDouble()
            val mbps = (sizeBytes.toDouble() * 8.0) / (durationSec * 1_000_000.0)
            val rounded = round(mbps).toInt().coerceAtLeast(1)
            return "$rounded Mbps"
        }

        return "15 Mbps"
    }

    private fun formatBytesToSizeLabel(bytes: Long): String {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gib >= 1.0) {
            val roundedGiB = round(gib * 10.0) / 10.0
            "$roundedGiB GB"
        } else {
            val mib = bytes.toDouble() / (1024.0 * 1024.0)
            "${round(mib).toInt()} MB"
        }
    }

    private fun extractSizeBytesFromText(text: String): Long? {
        val match = sizeRegex.find(text) ?: return null
        val num = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        return when {
            unit.startsWith("G") -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            unit.startsWith("M") -> (num * 1024.0 * 1024.0).toLong()
            else -> null
        }
    }

    private fun extractSizeLabelFromText(text: String): String? {
        val match = sizeRegex.find(text) ?: return null
        val num = match.groupValues[1]
        val unit = match.groupValues[2].uppercase().replace("IB", "B")
        return "$num $unit"
    }
}
