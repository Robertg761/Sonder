package app.sonder.download

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The fixtures in resources/archives were made with RARLAB's rar from a folder holding the files in [expected]. */
class ArchiveTest {
    private val first="first track ".repeat(40)
    private val second="second track ".repeat(40)
    private val expected=mapOf("Book/CD1/01 Größe.mp3" to first,"Book/CD2/01.mp3" to second,"Book/cover.jpg" to "JPEG","Book/info.nfo" to "notes\n")

    /** Unpacks [volumes] in order the way the download does, appending pieces a previous volume started. */
    private fun unpack(vararg volumes:String):Map<String,String> {
        val files=LinkedHashMap<String,ByteArrayOutputStream>()
        for(volume in volumes) javaClass.getResourceAsStream("/archives/$volume")!!.use { input ->
            Archive.read(input) { piece,data ->
                val out=if(piece.continued) files.getValue(piece.path) else ByteArrayOutputStream().also { files[piece.path]=it }
                val before=out.size()
                data.copyTo(out)
                if(piece.size>=0) assertEquals(piece.size,(out.size()-before).toLong())
            }
        }
        return files.mapValues { it.value.toString(Charsets.UTF_8) }
    }

    @Test fun readsStoredRar4AndRar5() {
        assertEquals(expected,unpack("stored4.rar"))
        assertEquals(expected,unpack("stored5.rar"))
    }
    @Test fun joinsFilesSplitAcrossVolumes() {
        assertEquals(expected,unpack("split4.part1.rar","split4.part2.rar"))
        assertEquals(expected,unpack("split5.part1.rar","split5.part2.rar"))
    }
    @Test fun skipsWhatTheVisitorLeavesUnread() {
        val seen=mutableListOf<String>()
        javaClass.getResourceAsStream("/archives/stored5.rar")!!.use { input -> Archive.read(input) { piece,data -> seen+=piece.path;data.read() } }
        assertEquals(expected.keys,seen.toSet())
    }
    @Test fun refusesCompressedAndLockedArchives() {
        val compressed=assertThrows(Archive.Unsupported::class.java) { unpack("compressed5.rar") }
        assertTrue(compressed.message!!.contains("compressed"))
        val locked=assertThrows(Archive.Unsupported::class.java) { unpack("locked5.rar") }
        assertTrue(locked.message!!.contains("password"))
    }
    @Test fun readsZip() {
        val bytes=ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("Book/"));zip.closeEntry()
            for((path,text) in expected) { zip.putNextEntry(ZipEntry(path.replace('/','\\')));zip.write(text.toByteArray());zip.closeEntry() }
        } }.toByteArray()
        val files=LinkedHashMap<String,String>()
        Archive.read(ByteArrayInputStream(bytes)) { piece,data -> files[piece.path]=data.readBytes().toString(Charsets.UTF_8) }
        assertEquals(expected,files)
    }
    @Test fun refusesOtherFiles() {
        assertThrows(Archive.Unsupported::class.java) { Archive.read(ByteArrayInputStream("ID3 not an archive".toByteArray())) { _,_ -> } }
    }
    @Test fun plansArchiveContentsLikeAnUpload() {
        val files=unpack("stored5.rar").keys.mapIndexed { i,path -> RealDebrid.File(i,path,1000) }
        val wanted=DownloadPlan.wanted(files)
        assertEquals(listOf("CD1 - 01 Größe.mp3","CD2 - 01.mp3","cover.jpg"),DownloadPlan.names(wanted).values.sorted())
    }
}
