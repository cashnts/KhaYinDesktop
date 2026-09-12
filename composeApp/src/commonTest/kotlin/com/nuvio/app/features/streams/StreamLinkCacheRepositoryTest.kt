package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StreamLinkCacheRepositoryTest {

    @Test
    fun `movie cache key keeps legacy type and video id shape`() {
        val key = StreamLinkCacheRepository.contentKey(
            type = "movie",
            videoId = "tt123",
        )

        assertEquals("movie|tt123", key)
    }

    @Test
    fun `episode cache key is scoped to parent show and episode`() {
        val firstEpisode = StreamLinkCacheRepository.contentKey(
            type = "series",
            videoId = "video-id",
            parentMetaId = "tt999",
            season = 1,
            episode = 1,
        )
        val secondEpisode = StreamLinkCacheRepository.contentKey(
            type = "series",
            videoId = "video-id",
            parentMetaId = "tt999",
            season = 1,
            episode = 2,
        )

        assertNotEquals(firstEpisode, secondEpisode)
        assertEquals("series|tt999|s1|e1|video-id", firstEpisode)
    }

    @Test
    fun `saves and retrieves cached direct stream link`() {
        val key = StreamLinkCacheRepository.contentKey("movie", "ttTest123")
        StreamLinkCacheRepository.save(
            contentKey = key,
            url = "https://example.com/stream.mkv",
            streamName = "1080p Stream",
            addonName = "TestAddon",
            addonId = "org.test.addon",
            sources = listOf("dht:source"),
        )

        val retrieved = StreamLinkCacheRepository.getValid(key, maxAgeMs = 60_000L)
        assertNotNull(retrieved)
        assertEquals("https://example.com/stream.mkv", retrieved.url)
        assertEquals("1080p Stream", retrieved.streamName)
        assertEquals("TestAddon", retrieved.addonName)

        StreamLinkCacheRepository.remove(key)
        assertNull(StreamLinkCacheRepository.getValid(key, maxAgeMs = 60_000L))
    }

    @Test
    fun `getValid returns null when maxAgeMs is zero or negative`() {
        val key = StreamLinkCacheRepository.contentKey("movie", "ttTestZeroTtl")
        StreamLinkCacheRepository.save(
            contentKey = key,
            url = "https://example.com/stream.mkv",
            streamName = "Stream",
            addonName = "Addon",
            addonId = "id",
        )

        assertNull(StreamLinkCacheRepository.getValid(key, maxAgeMs = 0L))
        assertNull(StreamLinkCacheRepository.getValid(key, maxAgeMs = -100L))

        StreamLinkCacheRepository.remove(key)
    }

    @Test
    fun `p2p stream link remains valid even when http maxAgeMs is small`() {
        val key = StreamLinkCacheRepository.contentKey("movie", "ttTestTorrent")
        StreamLinkCacheRepository.save(
            contentKey = key,
            url = "",
            streamName = "Torrent Stream",
            addonName = "TorrentAddon",
            addonId = "org.torrent.addon",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            fileIdx = 0,
        )

        // For P2P streams, effectiveMaxAgeMs is at least 30 days even if maxAgeMs is small
        val retrieved = StreamLinkCacheRepository.getValid(key, maxAgeMs = 1_000L)
        assertNotNull(retrieved)
        assertEquals("0123456789abcdef0123456789abcdef01234567", retrieved.infoHash)

        StreamLinkCacheRepository.remove(key)
    }
}
