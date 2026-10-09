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
}
