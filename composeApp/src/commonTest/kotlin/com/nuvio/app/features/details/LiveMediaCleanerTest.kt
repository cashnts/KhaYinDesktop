package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveMediaCleanerTest {

    @Test
    fun testCleanTitleRemovesNowAndRawPrefixSuffix() {
        val input = "NOW: SKY SPORTS PREMIER LEAGUE RAW"
        val expected = "Sky Sports Premier League"
        assertEquals(expected, LiveMediaCleaner.cleanTitle(input))
    }

    @Test
    fun testCleanTitleRemovesFhdAndParentheses() {
        val input = "(FHD) : SKY SPORTS F1 RAW"
        val expected = "Sky Sports F1"
        assertEquals(expected, LiveMediaCleaner.cleanTitle(input))
    }

    @Test
    fun testCleanTitleWithBracketsAndAcronyms() {
        val input = "[4K] BBC ONE (RAW)"
        val expected = "BBC One"
        assertEquals(expected, LiveMediaCleaner.cleanTitle(input))

        val input2 = "[FHD] SKY SPORTS MAIN EVENT"
        val expected2 = "Sky Sports Main Event"
        assertEquals(expected2, LiveMediaCleaner.cleanTitle(input2))

        val input3 = "(HD) : TNT SPORTS 1"
        val expected3 = "TNT Sports 1"
        assertEquals(expected3, LiveMediaCleaner.cleanTitle(input3))
    }

    @Test
    fun testCleanStreamLabelWithPipe() {
        val input = "NOW: SKY SPORTS PREMIER LEA... | football"
        val expected = "Sky Sports Premier Lea... | Football"
        assertEquals(expected, LiveMediaCleaner.cleanStreamLabel(input))
    }

    @Test
    fun testCleanDescriptionFiltersBoilerplate() {
        val input = """
            Live IPTV Recording
            Live on NOW-SKY-SPORTS-PREMIER-LEAGUE
            LIVE NOW
        """.trimIndent()
        val cleaned = LiveMediaCleaner.cleanDescription(input, "NOW: SKY SPORTS PREMIER LEAGUE RAW", isLive = true)
        assertEquals("Live broadcast on Sky Sports Premier League.", cleaned)
    }

    @Test
    fun testCleanGenresFiltersSlugs() {
        val input1 = listOf("Football", "NOW-SKY-SPORTS-PREMIER-LEAGUE")
        assertEquals(listOf("Football"), LiveMediaCleaner.cleanGenres(input1))

        val input2 = listOf("Motor Sports", "F1-3949409")
        assertEquals(listOf("Motor Sports"), LiveMediaCleaner.cleanGenres(input2))
    }

    @Test
    fun testIsLiveDetection() {
        assertTrue(LiveMediaCleaner.isLive(type = "tv"))
        assertTrue(LiveMediaCleaner.isLive(type = "channel"))
        assertTrue(LiveMediaCleaner.isLive(title = "NOW: SKY SPORTS PREMIER LEAGUE RAW"))
        assertTrue(LiveMediaCleaner.isLive(title = "SKY SPORTS F1"))
        assertTrue(LiveMediaCleaner.isLive(streamTitle = "(FHD) : SKY SPORTS F1 RAW"))
        assertTrue(LiveMediaCleaner.isLive(description = "Live IPTV Recording\nLive on F1-3949409"))
        assertTrue(LiveMediaCleaner.isLive(sourceUrl = "http://stream.example/live/channel.m3u8"))
        assertTrue(LiveMediaCleaner.isLive(title = "F1 Live Stream", durationMs = 65_000L))
    }
}
