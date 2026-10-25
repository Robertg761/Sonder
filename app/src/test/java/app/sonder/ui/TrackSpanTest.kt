package app.sonder.ui

import app.sonder.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSpanTest {
    private val chapters=listOf(Chapter("One",0),Chapter("Two",60_000),Chapter("Three",150_000))
    @Test fun wholeBookModeCoversTheBook() { assertEquals(0L to 200_000L,trackSpan(chapters,90_000,200_000,chapterMode=false)) }
    @Test fun chapterModeCoversTheChapterPlaying() {
        assertEquals(0L to 60_000L,trackSpan(chapters,0,200_000,chapterMode=true))
        assertEquals(60_000L to 150_000L,trackSpan(chapters,90_000,200_000,chapterMode=true))
        assertEquals(60_000L to 150_000L,trackSpan(chapters,60_000,200_000,chapterMode=true))
    }
    @Test fun lastChapterRunsToTheEndOfTheBook() { assertEquals(150_000L to 200_000L,trackSpan(chapters,180_000,200_000,chapterMode=true)) }
    @Test fun fallsBackToTheBookWithoutChapters() { assertEquals(0L to 200_000L,trackSpan(emptyList(),5_000,200_000,chapterMode=true)) }
}
