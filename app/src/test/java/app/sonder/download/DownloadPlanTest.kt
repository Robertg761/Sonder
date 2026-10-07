package app.sonder.download

import org.junit.Assert.*
import org.junit.Test

class DownloadPlanTest {
    private fun file(id:Int,path:String,bytes:Long=1000)=RealDebrid.File(id,path,bytes)

    @Test fun keepsAudioCueAndOneCoverOnly() {
        val files=listOf(file(1,"/Book/Book.m4b"),file(2,"/Book/Book.cue"),file(3,"/Book/Book.jpg"),file(4,"/Book/Book.nfo"),file(5,"/Book/Setup.exe"),file(6,"/Book/.pad/87324"),file(7,"/__MACOSX/Book/._Book.m4b"))
        assertEquals(listOf(1,2,3),DownloadPlan.wanted(files).map { it.id })
    }
    @Test fun nothingWantedWithoutAudio() {
        assertTrue(DownloadPlan.wanted(listOf(file(1,"/Book/Book.rar"),file(2,"/Book/cover.jpg"))).isEmpty())
    }
    @Test fun manyImagesAreScansNotCovers() {
        val scans=(1..5).map { file(10+it,"/Book/page$it.jpg") }
        assertEquals(listOf(1),DownloadPlan.wanted(listOf(file(1,"/Book/01.mp3"))+scans).map { it.id })
        assertEquals(15,DownloadPlan.wanted(listOf(file(1,"/Book/01.mp3"))+scans+file(15,"/Book/Folder.JPG")).last().id)
    }
    @Test fun skipsCueSheetsWhenDiscFoldersAreFlattened() {
        val files=listOf(file(1,"/Book/CD1/01.mp3"),file(2,"/Book/CD1/CD1.cue"),file(3,"/Book/CD2/01.mp3"),file(4,"/Book/CD2/CD2.cue"))
        assertEquals(listOf(1,3),DownloadPlan.wanted(files).map { it.id })
        assertEquals(listOf(1,2),DownloadPlan.wanted(listOf(file(1,"/Book/01.mp3"),file(2,"/Book/Book.cue"))).map { it.id })
    }
    @Test fun flattensDiscFoldersInOrder() {
        val files=listOf(file(1,"/Book/CD1/01.mp3"),file(2,"/Book/CD1/02.mp3"),file(3,"/Book/CD2/01.mp3"),file(4,"/Book/Art/front.jpeg"))
        assertEquals(mapOf(1 to "CD1 - 01.mp3",2 to "CD1 - 02.mp3",3 to "CD2 - 01.mp3",4 to "cover.jpg"),DownloadPlan.names(files))
    }
    @Test fun coverInAnotherFolderDoesNotRenameTracks() {
        val files=listOf(file(1,"/Book/Audio/01.mp3"),file(2,"/Book/Audio/book.cue"),file(3,"/Book/cover.jpg"))
        assertEquals(listOf(1,2,3),DownloadPlan.wanted(files).map { it.id })
        assertEquals(mapOf(1 to "01.mp3",2 to "book.cue",3 to "cover.jpg"),DownloadPlan.names(files))
    }
    @Test fun singleFolderKeepsFileNames() {
        assertEquals(mapOf(1 to "Book.m4b"),DownloadPlan.names(listOf(file(1,"/Some Book/Book.m4b"))))
        assertEquals(mapOf(1 to "Book.m4b"),DownloadPlan.names(listOf(file(1,"/Book.m4b"))))
    }
    @Test fun sanitizesAndDeduplicatesNames() {
        assertEquals("Who_ What_ - Part 1_2.mp3",DownloadPlan.sanitize("Who? What: - Part 1/2.mp3"))
        assertEquals("file",DownloadPlan.sanitize(" ... "))
        val used=mutableSetOf<String>()
        assertEquals(listOf("a.mp3","A (2).mp3","a (3).mp3"),listOf("a.mp3","A.mp3","a.mp3").map { DownloadPlan.unique(it,used) })
        val long=DownloadPlan.sanitize("x".repeat(300)+".m4b")
        assertEquals(150,long.length);assertTrue(long.endsWith(".m4b"))
        assertEquals("Audiobook",DownloadPlan.folderName("???"))
    }
}
