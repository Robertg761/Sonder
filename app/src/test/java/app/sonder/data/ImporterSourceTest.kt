package app.sonder.data

import org.junit.Assert.*
import org.junit.Test

class ImporterSourceTest {
    private fun source(scope:String="",album:String="Onyx Storm",container:Boolean=false,folder:String?="storage:primary:Audiobooks/Onyx Storm",file:String="storage:primary:Audiobooks/Onyx Storm/01.mp3")=
        Importer.source(scope,album,"Rebecca Yarros",container,folder?.let { path -> { path } }) { file }

    @Test fun albumTagsJoinFilesAcrossPickerMethods() {
        assertEquals("album:onyx storm:rebecca yarros",source())
        assertEquals(source(),source(folder="storage:primary:Music/Onyx Storm",file="storage:primary:Music/Onyx Storm/02.mp3"))
    }
    @Test fun aNewDownloadOfTheSameBookIsItsOwnBook() {
        val first=source("download:storage:primary:Audiobooks/Onyx Storm|")
        val second=source("download:storage:primary:Audiobooks/Onyx Storm (2)|",folder="storage:primary:Audiobooks/Onyx Storm (2)",file="storage:primary:Audiobooks/Onyx Storm (2)/01.mp3")
        assertNotEquals(first,second)
        assertNotEquals("Nor does it join a book imported another way",source(),second)
        // Files of one download still make one book.
        assertEquals(second,source("download:storage:primary:Audiobooks/Onyx Storm (2)|",folder="storage:primary:Audiobooks/Onyx Storm (2)",file="storage:primary:Audiobooks/Onyx Storm (2)/02.mp3"))
    }
    @Test fun untaggedFilesGroupByFolderUnlessTheyHoldAWholeBook() {
        assertEquals("folder:storage:primary:Audiobooks/Onyx Storm",source(album=""))
        assertEquals("file:storage:primary:Audiobooks/Onyx Storm/01.mp3",source(album="",container=true))
        assertEquals("file:storage:primary:Audiobooks/Onyx Storm/01.mp3",source(album="",folder=null))
    }
}
