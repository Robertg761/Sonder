package app.sonder.media

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class ChapterParserTest {
    private fun bytes(block:DataOutputStream.()->Unit):ByteArray { val out=ByteArrayOutputStream();DataOutputStream(out).use { it.block() };return out.toByteArray() }
    private fun atom(type:String,payload:ByteArray)=bytes { writeInt(payload.size+8);writeBytes(type);write(payload) }
    private fun source(b:ByteArray)=ChapterParser.BytesSource(b)
    @Test fun readsNeroMp4ChaptersWithoutReadingMdat() {
        val chpl=bytes { writeInt(0x01000000);writeInt(0);writeByte(2);writeLong(0);writeByte(7);writeBytes("Opening");writeLong(65_000L*10000);writeByte(8);writeBytes("Part two") }
        val mp4=atom("ftyp",ByteArray(8))+atom("mdat",ByteArray(100))+atom("moov",atom("udta",atom("chpl",chpl)))
        val chapters=ChapterParser.read(source(mp4),"m4b")
        assertEquals(2,chapters.size);assertEquals("Opening",chapters[0].title);assertEquals(65000L,chapters[1].start)
    }
    @Test fun readsRealQuickTimeChapterTrackWithoutNeroChpl() {
        val bytes=javaClass.classLoader!!.getResourceAsStream("quicktime.m4b")!!.use { it.readBytes() }
        val chapters=ChapterParser.read(source(bytes),"m4b")
        assertEquals(listOf("Opening","The middle","Closing"),chapters.map { it.title })
        assertEquals(listOf(0L,10000L,20000L),chapters.map { it.start })
    }
    @Test fun readsId3ChapterTitleAndMilliseconds() {
        val title=byteArrayOf(3)+"Arrival".toByteArray()
        val titleFrame=bytes { writeBytes("TIT2");writeInt(title.size);writeShort(0);write(title) }
        val chap=bytes { writeBytes("ch01");writeByte(0);writeInt(42000);writeInt(70000);writeInt(-1);writeInt(-1);write(titleFrame) }
        val frame=bytes { writeBytes("CHAP");writeInt(chap.size);writeShort(0);write(chap) }
        val id3=bytes { writeBytes("ID3");writeByte(3);writeByte(0);writeByte(0);writeByte(frame.size shr 21 and 127);writeByte(frame.size shr 14 and 127);writeByte(frame.size shr 7 and 127);writeByte(frame.size and 127);write(frame) }
        val chapters=ChapterParser.read(source(id3),"mp3")
        assertEquals("Arrival",chapters.single().title);assertEquals(42000L,chapters.single().start);assertEquals(70000L,chapters.single().end)
    }
    @Test fun parsesCueMultipleFilesAndFramePrecision() {
        val cue="""FILE "Disc 1.mp3" MP3
 TRACK 01 AUDIO
 TITLE "Opening"
 INDEX 01 00:00:00
 TRACK 02 AUDIO
 TITLE "The road"
 INDEX 01 03:42:37
 FILE "Disc 2.mp3" MP3
 TRACK 03 AUDIO
 TITLE "Home"
 INDEX 01 00:00:00"""
        val chapters=ChapterParser.cue(cue)
        assertEquals(3,chapters.size);assertEquals(222493L,chapters[1].position);assertEquals("Disc 2.mp3",chapters[2].file)
    }
    @Test fun rejectsTruncatedAndOversizedAtoms() {
        listOf(ByteArray(0),ByteArray(7),bytes { writeInt(-1);writeBytes("moov") },bytes { writeInt(1);writeBytes("moov");writeLong(Long.MAX_VALUE) },atom("moov",atom("udta",atom("chpl",byteArrayOf(1,0,0))))).forEach { assertTrue(ChapterParser.read(source(it),"mp4").isEmpty()) }
    }
    @Test fun usesNaturalOrderWithArbitrarilyLargeTrackNumbers() {
        val names=listOf("Track 10.mp3","Track 2.mp3","Track 1.mp3","Track 999999999999999999999.mp3")
        assertEquals(listOf(names[2],names[1],names[0],names[3]),names.sortedWith(ChapterParser.naturalComparator))
    }
    @Test fun parsesRealLengthPrefixedVorbisChapterComments() {
        listOf("flac","ogg","opus").forEach { extension ->
            val data=javaClass.classLoader!!.getResourceAsStream("vorbis-chapters.$extension")!!.use { it.readBytes() }
            val chapters=ChapterParser.read(source(data),extension)
            assertEquals(extension,listOf("Introduction with a long and clear chapter title","A second chapter with a title that crosses binary length boundaries"),chapters.map { it.title })
            assertEquals(extension,listOf(0L,2500L),chapters.map { it.start })
            assertEquals("Test narrator",ChapterParser.tags(source(data),extension)["narrator"])
        }
    }
}
