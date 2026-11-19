package app.sonder.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSkipTest {
    // A long single-file book: chapters at 0, 1h, 2h and 3h.
    private val starts=listOf(0L,3_600_000L,7_200_000L,10_800_000L)

    @Test fun previousRestartsTheChapterPlayingNotTheBook() {
        assertEquals(7_200_000L,ChapterSkip.previous(starts,9_000_000))
        assertEquals(10_800_000L,ChapterSkip.previous(starts,14_000_000))
    }
    @Test fun previousRightAfterAChapterStartsGoesOneChapterBack() {
        assertEquals(3_600_000L,ChapterSkip.previous(starts,7_201_000))
        assertEquals(3_600_000L,ChapterSkip.previous(starts,7_200_000))
    }
    @Test fun previousNeverGoesBeforeTheFirstChapter() {
        assertEquals(0L,ChapterSkip.previous(starts,1_000))
        assertEquals(0L,ChapterSkip.previous(starts,60_000))
    }
    @Test fun audioBeforeTheFirstMarkedChapterIsItsOwnChapter() {
        assertEquals(30_000L,ChapterSkip.previous(listOf(30_000L,90_000L),60_000))
        assertEquals(0L,ChapterSkip.previous(listOf(30_000L,90_000L),20_000))
    }
    @Test fun withoutChaptersPreviousRestartsFromTheBeginningOnly() { assertEquals(0L,ChapterSkip.previous(emptyList(),50_000)) }
    @Test fun restartStaysInTheChapterPlayingEvenRightAfterItStarts() {
        assertEquals(7_200_000L,ChapterSkip.restart(starts,9_000_000))
        assertEquals(7_200_000L,ChapterSkip.restart(starts,7_201_000))
        assertEquals(7_200_000L,ChapterSkip.restart(starts,7_200_000))
        assertEquals(0L,ChapterSkip.restart(starts,500))
        assertEquals(0L,ChapterSkip.restart(listOf(30_000L),10_000))
        assertEquals(0L,ChapterSkip.restart(emptyList(),50_000))
    }
    @Test fun nextGoesToTheNextChapterAndStopsAtTheLast() {
        assertEquals(7_200_000L,ChapterSkip.next(starts,3_600_000))
        assertEquals(3_600_000L,ChapterSkip.next(starts,0))
        assertNull(ChapterSkip.next(starts,11_000_000))
    }
}
