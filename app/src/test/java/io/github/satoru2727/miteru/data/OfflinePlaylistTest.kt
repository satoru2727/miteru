package io.github.satoru2727.miteru.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePlaylistTest {

    private val playlist = """
        #EXTM3U
        #EXT-X-VERSION:6
        #EXT-X-PLAYLIST-TYPE:VOD
        #EXT-X-TARGETDURATION:7
        #EXTINF:6.006000,
        segment?session_id=abc&sequence=0&cache_key=xyz
        #EXTINF:6.006000,
        segment?session_id=abc&sequence=1&cache_key=xyz
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun segmentUrisAreExtracted() {
        val uris = playlistSegmentUris(playlist)
        assertEquals(2, uris.size)
        assertEquals("segment?session_id=abc&sequence=0&cache_key=xyz", uris[0])
        assertEquals("segment?session_id=abc&sequence=1&cache_key=xyz", uris[1])
    }

    @Test
    fun localPlaylistRewritesUrisAndKeepsTags() {
        val (local, count) = rewriteLocalPlaylist(playlist)
        assertEquals(2, count)
        val lines = local.trim().lines()
        assertEquals("#EXTM3U", lines[0])
        assertEquals("#EXT-X-TARGETDURATION:7", lines[3])
        assertEquals("#EXTINF:6.006000,", lines[4])
        assertEquals("seg_00000.ts", lines[5])
        assertEquals("#EXTINF:6.006000,", lines[6])
        assertEquals("seg_00001.ts", lines[7])
        assertEquals("#EXT-X-ENDLIST", lines[8])
    }

    @Test
    fun segmentFileNameIsZeroPadded() {
        assertEquals("seg_00000.ts", segmentFileName(0))
        assertEquals("seg_00123.ts", segmentFileName(123))
    }
}
