package com.nuvio.app.features.license

import com.nuvio.app.features.player.AddonSubtitle
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.buildSubtitleLanguageItems
import com.nuvio.app.features.player.buildSubtitleSelectionOptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyanmarSubLimiterTest {

    @BeforeTest
    fun setup() {
        LicenseStorage.clearLicensePayload()
        LicenseStorage.saveFreeMode(true)
        LicenseStorage.saveMmsubQuotaData("")
        LicenseRepository.initialize()
        MyanmarSubLimiter.activeContentId = null
    }

    @AfterTest
    fun tearDown() {
        LicenseStorage.clearLicensePayload()
        LicenseStorage.saveFreeMode(true)
        LicenseStorage.saveMmsubQuotaData("")
        LicenseRepository.initialize()
        MyanmarSubLimiter.activeContentId = null
    }

    @Test
    fun normalizesContentIdsCorrectly() {
        assertEquals("tt1234567", MyanmarSubLimiter.normalizeId("tt1234567"))
        assertEquals("tt1234567", MyanmarSubLimiter.normalizeId("movie:tt1234567"))
        assertEquals("tt1234567", MyanmarSubLimiter.normalizeId("tt1234567:1:5"))
        assertEquals("tt1234567", MyanmarSubLimiter.normalizeId("series:tt1234567:2:10"))
        assertEquals("tt1234567", MyanmarSubLimiter.normalizeId("tt1234567?extra=foo"))
        assertEquals("kitsu:1234", MyanmarSubLimiter.normalizeId("kitsu:1234:1:2"))
    }

    @Test
    fun secondMovieAllowsAccessAndRetainsSubtitlesAfterRecording() {
        val movie1 = "tt1111111"
        val movie2 = "tt2222222"
        val movie3 = "tt3333333"

        // 1. First movie
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(movie1))
        MyanmarSubLimiter.recordMyanmarSubUsed(movie1)
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(movie1))
        assertEquals(1, MyanmarSubLimiter.getRemainingMoviesToday())

        // 2. Second movie before recording
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(movie2))

        // Record second movie (simulating user selecting mm sub on 2nd movie)
        MyanmarSubLimiter.activeContentId = movie2
        MyanmarSubLimiter.recordMyanmarSubUsed(movie2)
        assertEquals(0, MyanmarSubLimiter.getRemainingMoviesToday())

        // Both movies must still have access
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(movie1))
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(movie2))
        // Calling with null falls back to activeContentId (movie2) and must remain true
        assertTrue(MyanmarSubLimiter.canAccessMyanmarSub(null))

        // 3. Third movie is blocked
        assertFalse(MyanmarSubLimiter.canAccessMyanmarSub(movie3))
    }

    @Test
    fun subtitleSelectionOptionsRetainMyanmarSubtitlesOnSecondMovie() {
        val movie1 = "tt1111111"
        val movie2 = "tt2222222"

        // Record movie 1
        MyanmarSubLimiter.recordMyanmarSubUsed(movie1)

        // Movie 2 is selected as active
        MyanmarSubLimiter.activeContentId = movie2
        MyanmarSubLimiter.recordMyanmarSubUsed(movie2)

        val mmSub = AddonSubtitle(
            id = "mm-sub-1",
            url = "https://stream.khayin.net/subtitles/vtt/movie/tt2222222.vtt",
            language = "my",
            display = "Burmese (MMSub)",
            addonName = "MMSub",
        )
        val enSub = AddonSubtitle(
            id = "en-sub-1",
            url = "https://example.com/en.vtt",
            language = "en",
            display = "English",
            addonName = "OpenSubtitles",
        )

        val languageItems = buildSubtitleLanguageItems(
            subtitleTracks = emptyList(),
            addonSubtitles = listOf(mmSub, enSub),
            preferredLanguage = "en",
            secondaryPreferredLanguage = "my",
            showOnlyPreferredLanguages = false,
            selectedLanguageKey = "my",
            filterByLicense = true,
            contentId = movie2,
        )

        assertTrue(
            languageItems.any { it.key == "my" },
            "Myanmar language item must not disappear on the second movie",
        )

        val options = buildSubtitleSelectionOptions(
            languageKey = "my",
            subtitleTracks = emptyList(),
            addonSubtitles = listOf(mmSub, enSub),
            contentId = movie2,
        )

        assertEquals(1, options.size)
        assertEquals("addon:MMSub:mm-sub-1:https://stream.khayin.net/subtitles/vtt/movie/tt2222222.vtt", options.first().id)
    }
}
