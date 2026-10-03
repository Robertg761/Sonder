package app.sonder

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonder.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHistoryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun withStore(name:String,block:suspend (LibraryStore)->Unit) {
        context.deleteDatabase(name);val store=LibraryStore(context,name)
        try { block(store) } finally { store.close();context.deleteDatabase(name) }
    }
    private suspend fun book(store:LibraryStore)=store.add(Book(title="A reading record",author="Writer",source="history-source",duration=10000,format="MP3"),listOf(Track(uri="content://test/history",name="Track",duration=10000)),listOf(Chapter("Track",0,10000)))
    @Test fun historyRemainsIndependentOfPlaybackAndMediaDeletion() = runBlocking {
        withStore("history-independent.db") { store ->
            val id=book(store);store.progress(id,4000,0)
            val entry=ReadingEntry.fromBook(store.library.value.books.single()).copy(notes="Stopped at chapter four")
            store.saveReading(entry);assertEquals(40,store.library.value.readingHistory.single().percent)
            assertFalse(store.library.value.books.single().finished);assertEquals(4000,store.library.value.books.single().position)
            store.markStatus(id,ListeningStatus.NOT_STARTED);assertEquals(40,store.library.value.readingHistory.single().percent)
            store.saveReading(entry.copy(percent=100));assertFalse(store.library.value.books.single().finished)
            store.remove(id);assertTrue(store.library.value.books.isEmpty());assertEquals(1,store.library.value.readingHistory.size)
            store.saveReading(entry.copy(id=java.util.UUID.randomUUID().toString(),percent=70,notes="Read again"));assertEquals(2,store.library.value.readingHistory.size)
            store.deleteReading(entry.id);assertEquals(70,store.library.value.readingHistory.single().percent)
        }
    }
    @Test fun completionPromptsPersistAndLoggingIsOptInOncePerRead() = runBlocking {
        val name="history-prompts.db"
        withStore(name) { store ->
            val id=book(store);store.progress(id,10000,0,finished=true)
            assertTrue(store.library.value.readingHistory.isEmpty())
            val prompt=store.library.value.completionPrompts.single()
            store.progress(id,10000,0,finished=true);assertEquals(prompt.entry.id,store.library.value.completionPrompts.single().entry.id)
            val reopened=LibraryStore(context,name);reopened.refresh();assertEquals(prompt.entry.id,reopened.library.value.completionPrompts.single().entry.id);reopened.close()
            store.dismissCompletion(prompt.entry.id);store.progress(id,10000,0,finished=true);assertTrue(store.library.value.completionPrompts.isEmpty())
            store.startListening(id);store.progress(id,10000,0,finished=true)
            val reread=store.library.value.completionPrompts.single();assertNotEquals(prompt.entry.id,reread.entry.id)
            store.saveReading(reread.entry,reread.entry.id);store.saveReading(reread.entry,reread.entry.id)
            assertEquals(1,store.library.value.readingHistory.size);assertTrue(store.library.value.completionPrompts.isEmpty())
            store.markStatus(id,ListeningStatus.NOT_STARTED);store.markStatus(id,ListeningStatus.FINISHED)
            assertEquals(1,store.library.value.completionPrompts.size);assertEquals(1,store.library.value.readingHistory.size)
        }
    }
    @Test fun historyBackupRestoresWithoutMediaAndMalformedHistoryRollsBack() = runBlocking {
        withStore("history-backup.db") { store ->
            val id=book(store);store.progress(id,4000,0)
            val entry=ReadingEntry(title="Manual paper book",author="Writer",percent=70,notes="A separate record")
            store.saveReading(entry);val backup=store.export()
            store.deleteReading(entry.id);store.remove(id)
            assertEquals(0,store.restore(backup));assertEquals(entry,store.library.value.readingHistory.single())
            store.restore(backup);assertEquals(1,store.library.value.readingHistory.size)
            val legacy=JSONObject(backup).apply { remove("readingHistory") }.toString();store.restore(legacy);assertEquals(1,store.library.value.readingHistory.size)
            val bookId=book(store);store.progress(bookId,2000,0);val before=store.library.value.books.single()
            val malformed=JSONObject(store.export())
            malformed.getJSONArray("books").getJSONObject(0).put("title","Should roll back")
            malformed.getJSONArray("readingHistory").getJSONObject(0).put("percent",101)
            assertTrue(runCatching { store.restore(malformed.toString()) }.isFailure)
            assertEquals(before.title,store.library.value.books.single().title);assertEquals(70,store.library.value.readingHistory.single().percent)
        }
    }
    @Test fun upgradingVersionThreeDoesNotBackfillOrPromptOldCompletions() = runBlocking {
        val name="history-migration.db";context.deleteDatabase(name)
        val schemaName="history-schema.db";context.deleteDatabase(schemaName)
        val fresh=LibraryStore(context,schemaName)
        val schema=fresh.readableDatabase.rawQuery("SELECT sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name!='android_metadata' AND name NOT LIKE 'reading_%'",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        fresh.close();context.deleteDatabase(schemaName)
        val legacy=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null)
        try { schema.forEach(legacy::execSQL);legacy.execSQL("INSERT INTO books(title,author,narrator,description,genre,cover,source,duration,position,added,format,size,finished,started) VALUES('Previously finished','Writer','','','','','legacy',10000,8000,1,'MP3',100,1,1)");legacy.version=3 } finally { legacy.close() }
        val upgraded=LibraryStore(context,name)
        try { upgraded.refresh();assertEquals(8000,upgraded.library.value.books.single().position);assertTrue(upgraded.library.value.books.single().finished);assertTrue(upgraded.library.value.readingHistory.isEmpty());assertTrue(upgraded.library.value.completionPrompts.isEmpty());assertEquals(4,upgraded.readableDatabase.version) } finally { upgraded.close();context.deleteDatabase(name) }
    }
}
