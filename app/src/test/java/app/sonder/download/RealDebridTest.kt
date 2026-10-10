package app.sonder.download

import org.junit.Assert.*
import org.junit.Test

class RealDebridTest {
    private fun torrent(status:String,seeders:Int=0,speed:Long=0,progress:Int=0)=RealDebrid.Torrent("id",status,progress,seeders,speed,1000,emptyList(),emptyList())

    @Test fun cachedAndQueuedUploadsCanFinish() {
        listOf("downloaded","queued","compressing","uploading").forEach { assertTrue(it,RealDebrid.alive(torrent(it))) }
    }
    @Test fun downloadingNeedsSomeoneSharing() {
        assertFalse(RealDebrid.alive(torrent("downloading")))
        assertTrue(RealDebrid.alive(torrent("downloading",seeders=1)))
        assertTrue(RealDebrid.alive(torrent("downloading",speed=2048)))
        assertTrue(RealDebrid.alive(torrent("downloading",progress=3)))
    }
    @Test fun unstartedAndFailedUploadsAreNotProven() {
        listOf("magnet_conversion","waiting_files_selection","dead","magnet_error","error","virus").forEach { assertFalse(it,RealDebrid.alive(torrent(it,seeders=5))) }
    }
    @Test fun dropsRateLimitsAndOutagesAreTemporary() {
        assertTrue(RealDebrid.temporary(java.io.IOException("Couldn't reach Real-Debrid.")))
        assertTrue(RealDebrid.temporary(RealDebrid.Error("busy",503,0)))
        assertTrue(RealDebrid.temporary(RealDebrid.Error("slow down",429,0)))
        assertTrue(RealDebrid.temporary(RealDebrid.Error("slow down",400,34)))
        assertFalse(RealDebrid.temporary(RealDebrid.Error("bad token",401,8)))
        assertFalse(RealDebrid.temporary(RealDebrid.Error("not premium",403,20)))
        assertFalse(RealDebrid.temporary(RealDebrid.Error("gone",404,0)))
        assertFalse(RealDebrid.temporary(IllegalStateException("no audio")))
    }
    @Test fun premiumDaysLeft() {
        val now=java.time.Instant.parse("2026-10-09T12:00:00Z").toEpochMilli()
        assertEquals(30L,RealDebrid.User("reader",true,"2026-11-08T12:00:00.000Z").daysLeft(now))
        assertEquals(0L,RealDebrid.User("reader",true,"2026-10-09T20:00:00.000Z").daysLeft(now))
        assertEquals(-1L,RealDebrid.User("reader",true,"2026-10-09T08:00:00.000Z").daysLeft(now))
        assertNull(RealDebrid.User("reader",false,"").daysLeft(now))
    }
}
