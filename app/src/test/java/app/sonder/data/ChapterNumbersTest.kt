package app.sonder.data

import org.junit.Assert.*
import org.junit.Test

class ChapterNumbersTest {
    private fun book(vararg titles:String)=titles.mapIndexed { i,t -> Chapter(t,i*1000L) }

    @Test fun frontMatterDoesNotCountTowardChapterNumbers() {
        // The first tracks of Fourth Wing (Unabridged), from issue #9.
        val titles=listOf("Opening Credits","Content Warning","Dedication","Preamble")+(1..40).map { "Chapter $it" }
        val numbers=chapterNumbers(book(*titles.toTypedArray()))
        assertEquals(listOf(null,null,null,null),numbers.take(4))
        assertEquals(37,numbers[titles.indexOf("Chapter 37")])
        assertEquals(40,numbers.count { it!=null })
    }

    @Test fun backMatterDoesNotCount() {
        assertEquals(listOf(1,2,null,null),chapterNumbers(book("Chapter 1","Chapter 2","Acknowledgments","End Credits")))
    }

    @Test fun recognisesTrackFileNames() {
        listOf("01 - Opening Credits","Fourth Wing - Dedication","02_Content_Warning","Fourth Wing: Preamble","Prologue.","About the Author","A Note from the Author").forEach {
            assertTrue(it,isBookMatter(it))
        }
    }

    @Test fun ordinaryChaptersCount() {
        listOf("Chapter 1","1","Part One","The Dedication of Brennan","Credits Where Due","Fourth Wing - 05","The Title","Introductions Are Overrated").forEach {
            assertFalse(it,isBookMatter(it))
        }
    }

    @Test fun booksWithOnlyMatterTitlesStillGetNumbers() {
        assertEquals(listOf(1,2),chapterNumbers(book("Introduction","Epilogue")))
        assertEquals(emptyList<Int?>(),chapterNumbers(emptyList()))
    }
}
