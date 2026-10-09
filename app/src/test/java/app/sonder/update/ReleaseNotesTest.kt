package app.sonder.update

import app.sonder.update.ReleaseNotes.Block
import app.sonder.update.ReleaseNotes.Kind
import app.sonder.update.ReleaseNotes.Run
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun headingsBulletsAndParagraphs() {
        val notes="Sonder 1.7.3 checks books first.\r\n\r\n## What's new\r\n- Check for **seeders** before\r\n  downloading.\r\n  - Nested detail\r\n* Cancel from the [notification](https://example.com).\r\n1. Numbered step\r\n\r\n---\r\nInstall over your `existing` app."
        assertEquals(listOf(
            Block(Kind.PARAGRAPH,"Sonder 1.7.3 checks books first."),
            Block(Kind.HEADING,"What's new"),
            Block(Kind.BULLET,"Check for **seeders** before downloading."),
            Block(Kind.BULLET,"Nested detail",1),
            Block(Kind.BULLET,"Cancel from the notification."),
            Block(Kind.BULLET,"Numbered step"),
            Block(Kind.PARAGRAPH,"Install over your existing app.")
        ),ReleaseNotes.parse(notes))
    }
    @Test fun boldRuns() {
        assertEquals(listOf(Run("Check for ",false),Run("seeders",true),Run(" first",false)),ReleaseNotes.inline("Check for **seeders** first"))
        assertEquals(listOf(Run("2 ** 3",false)),ReleaseNotes.inline("2 ** 3"))
        assertEquals(listOf(Run("New",true)),ReleaseNotes.inline("**New**"))
    }
}
