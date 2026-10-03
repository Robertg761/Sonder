package app.sonder.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class LibraryStore(context: Context,databaseName:String="sonder-library.db") : SQLiteOpenHelper(context, databaseName, null, 4) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(Library())
    val library = state.asStateFlow()
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, narrator TEXT NOT NULL, description TEXT NOT NULL, genre TEXT NOT NULL, cover TEXT NOT NULL, source TEXT NOT NULL, duration INTEGER NOT NULL, position INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL, lastPlayed INTEGER NOT NULL DEFAULT 0, favorite INTEGER NOT NULL DEFAULT 0, finished INTEGER NOT NULL DEFAULT 0, speed REAL NOT NULL DEFAULT 1, collection TEXT NOT NULL DEFAULT '', format TEXT NOT NULL, size INTEGER NOT NULL, started INTEGER NOT NULL DEFAULT 0, progressRevision INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE tracks(id INTEGER PRIMARY KEY, bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE, uri TEXT NOT NULL UNIQUE, name TEXT NOT NULL, duration INTEGER NOT NULL, offset INTEGER NOT NULL, ordinal INTEGER NOT NULL, sortName TEXT NOT NULL)")
        db.execSQL("CREATE INDEX tracks_book ON tracks(bookId,ordinal)")
        db.execSQL("CREATE TABLE chapters(bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE, title TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX chapters_book ON chapters(bookId,start)")
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY, bookId INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE, position INTEGER NOT NULL, note TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE daily(day TEXT PRIMARY KEY, millis INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE folders(uri TEXT PRIMARY KEY)")
        createReadingTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2) { db.execSQL("ALTER TABLE tracks ADD COLUMN sortName TEXT NOT NULL DEFAULT ''");db.execSQL("UPDATE tracks SET sortName=name") }
        if(oldVersion<3) { db.execSQL("ALTER TABLE books ADD COLUMN started INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE books ADD COLUMN progressRevision INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE books SET started=1 WHERE position>0 OR finished=1") }
        if(oldVersion<4) createReadingTables(db)
        require(newVersion<=4) { "A database migration is required from $oldVersion to $newVersion" }
    }
    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { publish() } }
    private fun publish() {
        val db = readableDatabase
        state.value = Library(
            db.rawQuery("SELECT * FROM books ORDER BY added DESC", null).use { c -> buildList { while(c.moveToNext()) add(c.book()) } },
            db.rawQuery("SELECT * FROM bookmarks ORDER BY created DESC", null).use { c -> buildList { while(c.moveToNext()) add(Bookmark(c.long("id"),c.long("bookId"),c.long("position"),c.str("note"),c.long("created"))) } },
            db.rawQuery("SELECT * FROM daily ORDER BY day", null).use { c -> buildMap { while(c.moveToNext()) put(c.str("day"),c.long("millis")) } },
            db.rawQuery("SELECT uri FROM folders",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } },
            db.rawQuery("SELECT * FROM reading_history ORDER BY loggedOn DESC, rowid DESC",null).use { c -> buildList { while(c.moveToNext()) add(c.readingEntry()) } },
            db.rawQuery("SELECT * FROM reading_prompts ORDER BY created, rowid",null).use { c -> buildList { while(c.moveToNext()) add(CompletionPrompt(c.long("bookId"),c.readingEntry())) } }
        )
    }
    private suspend fun <T> change(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) { mutex.withLock { val result = block(writableDatabase); publish(); result } }
    suspend fun knownUris(): Set<String> = withContext(Dispatchers.IO) { readableDatabase.rawQuery("SELECT uri FROM tracks",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } } }
    suspend fun tracks(id: Long): List<Track> = withContext(Dispatchers.IO) { readableDatabase.rawQuery("SELECT * FROM tracks WHERE bookId=? ORDER BY ordinal",arrayOf("$id")).use { c -> buildList { while(c.moveToNext()) add(Track(c.long("id"),id,c.str("uri"),c.str("name"),c.long("duration"),c.long("offset"),c.int("ordinal"),c.str("sortName"))) } } }
    suspend fun chapters(id: Long): List<Chapter> = withContext(Dispatchers.IO) { readableDatabase.rawQuery("SELECT * FROM chapters WHERE bookId=? ORDER BY start",arrayOf("$id")).use { c -> buildList { while(c.moveToNext()) add(Chapter(c.str("title"),c.long("start"),c.long("end"))) } } }
    suspend fun add(book: Book, tracks: List<Track>, chapters: List<Chapter>): Long = change { db ->
        db.beginTransaction()
        try {
            val existing=db.rawQuery("SELECT * FROM books WHERE source=? LIMIT 1",arrayOf(book.source)).use { c -> if(c.moveToFirst()) c.book() else null }
            val id:Long
            var combinedTracks=tracks
            var combinedChapters=chapters
            var merged=book
            if(existing!=null) {
                id=existing.id
                val old=db.rawQuery("SELECT * FROM tracks WHERE bookId=? ORDER BY ordinal",arrayOf("$id")).use { c -> buildList { while(c.moveToNext()) add(Track(c.long("id"),id,c.str("uri"),c.str("name"),c.long("duration"),c.long("offset"),c.int("ordinal"),c.str("sortName"))) } }
                val oldChapters=db.rawQuery("SELECT * FROM chapters WHERE bookId=? ORDER BY start",arrayOf("$id")).use { c -> buildList { while(c.moveToNext()) add(Chapter(c.str("title"),c.long("start"),c.long("end"))) } }
                val currentTrack=old.lastOrNull { it.offset<=existing.position }
                var at=0L
                combinedTracks=(old+tracks).sortedWith { a,b -> app.sonder.media.ChapterParser.naturalComparator.compare(a.sortName,b.sortName) }.mapIndexed { i,t -> t.copy(offset=at,ordinal=i).also { at+=t.duration } }
                fun shift(list:List<Chapter>,original:List<Track>)=list.mapNotNull { c -> original.lastOrNull { it.offset<=c.start }?.let { oldTrack -> combinedTracks.firstOrNull { it.uri==oldTrack.uri }?.let { newTrack -> c.copy(start=c.start-oldTrack.offset+newTrack.offset,end=0) } } }
                combinedChapters=(shift(oldChapters,old)+shift(chapters,tracks)).sortedBy { it.start }.distinctBy { it.start }
                val position=if(!existing.inProgress && !existing.finished) 0L else currentTrack?.let { t -> combinedTracks.first { it.uri==t.uri }.offset+(existing.position-t.offset) } ?: existing.position
                merged=existing.copy(duration=at,position=position,finished=false,size=existing.size+book.size,format=(existing.format.split(" / ")+book.format.split(" / ")).distinct().joinToString(" / "),cover=existing.cover.ifBlank { book.cover })
                db.delete("tracks","bookId=?",arrayOf("$id"));db.delete("chapters","bookId=?",arrayOf("$id"))
                db.update("books",merged.values(),"id=?",arrayOf("$id"))
                // Bookmarks follow their original track when an earlier track is discovered on a rescan.
                val marks=db.rawQuery("SELECT id,position FROM bookmarks WHERE bookId=?",arrayOf("$id")).use { c -> buildList { while(c.moveToNext()) add(c.getLong(0) to c.getLong(1)) } };marks.forEach { (markId,pos) -> old.lastOrNull { it.offset<=pos }?.let { t -> val delta=combinedTracks.first { it.uri==t.uri }.offset-t.offset;db.execSQL("UPDATE bookmarks SET position=? WHERE id=?",arrayOf(pos+delta,markId)) } }
            } else id=db.insertOrThrow("books",null,merged.values())
            combinedTracks.forEach { t -> db.insertOrThrow("tracks",null, ContentValues().apply { put("bookId",id); put("uri",t.uri); put("name",t.name); put("duration",t.duration); put("offset",t.offset); put("ordinal",t.ordinal);put("sortName",t.sortName) }) }
            combinedChapters.forEachIndexed { i,c -> db.insertOrThrow("chapters",null,ContentValues().apply { put("bookId",id); put("title",c.title); put("start",c.start); put("end",combinedChapters.getOrNull(i+1)?.start ?: merged.duration) }) }
            db.setTransactionSuccessful(); id
        } finally { db.endTransaction() }
    }
    private fun createReadingTables(db:SQLiteDatabase) {
        val columns="id TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, percent INTEGER NOT NULL CHECK(percent BETWEEN 0 AND 100), loggedOn TEXT NOT NULL, notes TEXT NOT NULL, source TEXT NOT NULL, duration INTEGER NOT NULL"
        // History intentionally has no foreign key to the media library: deleting a book keeps its log.
        db.execSQL("CREATE TABLE reading_history($columns)")
        db.execSQL("CREATE TABLE reading_prompts($columns, bookId INTEGER NOT NULL UNIQUE REFERENCES books(id) ON DELETE CASCADE, created INTEGER NOT NULL)")
    }
    private fun enqueueCompletion(db:SQLiteDatabase,id:Long) {
        val book=db.rawQuery("SELECT * FROM books WHERE id=?",arrayOf("$id")).use { c -> if(c.moveToFirst()) c.book() else null } ?: return
        val entry=ReadingEntry.fromBook(book,complete=true)
        db.insertWithOnConflict("reading_prompts",null,entry.values().apply { put("bookId",id);put("created",System.currentTimeMillis()) },SQLiteDatabase.CONFLICT_IGNORE)
    }
    suspend fun saveReading(entry:ReadingEntry,promptId:String?=null) = change { db ->
        entry.validate()
        db.beginTransaction()
        try {
            db.insertWithOnConflict("reading_history",null,entry.values(),SQLiteDatabase.CONFLICT_REPLACE)
            if(promptId!=null) db.delete("reading_prompts","id=?",arrayOf(promptId))
            else if(entry.percent==100 && entry.source.isNotBlank()) db.delete("reading_prompts","source=?",arrayOf(entry.source))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun deleteReading(id:String) = change { db -> db.delete("reading_history","id=?",arrayOf(id)) }
    suspend fun dismissCompletion(id:String) = change { db -> db.delete("reading_prompts","id=?",arrayOf(id)) }
    suspend fun setSpeed(id:Long,speed:Float) = change { db -> db.execSQL("UPDATE books SET speed=? WHERE id=?",arrayOf<Any>(speed.coerceIn(.5f,3f),id)) }
    suspend fun update(book: Book) = change { db -> db.update("books",book.values().apply { remove("position");remove("lastPlayed");remove("speed");remove("started");remove("progressRevision");remove("finished") },"id=?",arrayOf("${book.id}")) }
    suspend fun progress(id: Long, position: Long, listened: Long, finished: Boolean = false) = change { db -> recordProgress(db,id,position,listened,finished) }
    suspend fun trackProgress(id:Long,uri:String,relative:Long,listened:Long,finished:Boolean=false,revision:Long?=null) = change { db ->
        val offset=db.rawQuery("SELECT offset FROM tracks WHERE bookId=? AND uri=?",arrayOf("$id",uri)).use { c -> if(c.moveToFirst()) c.getLong(0) else null }
        val currentRevision=db.rawQuery("SELECT progressRevision FROM books WHERE id=?",arrayOf("$id")).use { c -> if(c.moveToFirst()) c.getLong(0) else null }
        if(offset!=null && (revision==null || revision==currentRevision)) recordProgress(db,id,offset+relative,listened,finished)
    }
    private fun recordProgress(db:SQLiteDatabase,id:Long,position:Long,listened:Long,finished:Boolean) {
        db.beginTransaction()
        try {
            val needsPrompt=finished && db.rawQuery("SELECT finished FROM books WHERE id=?",arrayOf("$id")).use { c -> c.moveToFirst() && c.getInt(0)==0 }
            db.execSQL("UPDATE books SET started=1, position=MIN(duration,MAX(0,?)), lastPlayed=?, finished=CASE WHEN ? THEN 1 ELSE finished END WHERE id=?",arrayOf<Any>(position,System.currentTimeMillis(),if(finished) 1 else 0,id))
            if(needsPrompt) enqueueCompletion(db,id)
            if (listened > 0) { val day=LocalDate.now().toString(); db.execSQL("INSERT OR IGNORE INTO daily(day,millis) VALUES(?,0)",arrayOf(day)); db.execSQL("UPDATE daily SET millis=millis+? WHERE day=?",arrayOf<Any>(listened.coerceAtMost(10000),day)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun markStatus(id:Long,status:ListeningStatus) = change { db ->
        // Invalidate queued progress saves from the old playback session before changing state.
        val values=ContentValues().apply {
            put("finished",status==ListeningStatus.FINISHED);put("started",status!=ListeningStatus.NOT_STARTED)
            if(status==ListeningStatus.NOT_STARTED) { put("position",0L);put("lastPlayed",0L) }
            if(status==ListeningStatus.IN_PROGRESS) put("lastPlayed",System.currentTimeMillis())
        }
        db.beginTransaction()
        try {
            val wasFinished=db.rawQuery("SELECT finished FROM books WHERE id=?",arrayOf("$id")).use { c -> c.moveToFirst() && c.getInt(0)==1 }
            db.execSQL("UPDATE books SET progressRevision=progressRevision+1 WHERE id=?",arrayOf(id))
            db.update("books",values,"id=?",arrayOf("$id"))
            if(status==ListeningStatus.FINISHED && !wasFinished) enqueueCompletion(db,id)
            else if(status!=ListeningStatus.FINISHED) db.delete("reading_prompts","bookId=?",arrayOf("$id"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    // Puts a book back exactly as it was before a status change, for Undo. Queued progress saves from before are invalidated.
    suspend fun restoreStatus(book:Book) = change { db ->
        db.beginTransaction()
        try {
            db.execSQL("UPDATE books SET progressRevision=progressRevision+1, finished=?, started=?, position=?, lastPlayed=? WHERE id=?",arrayOf<Any>(if(book.finished) 1 else 0,if(book.started) 1 else 0,book.position,book.lastPlayed,book.id))
            if(!book.finished) db.delete("reading_prompts","bookId=?",arrayOf("${book.id}"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun startListening(id:Long) = change { db -> db.execSQL("UPDATE books SET started=1, finished=0 WHERE id=?",arrayOf(id));db.delete("reading_prompts","bookId=?",arrayOf("$id")) }
    suspend fun addBookmark(bookId: Long, position: Long, note: String) = change { db -> db.insertOrThrow("bookmarks",null,ContentValues().apply { put("bookId",bookId); put("position",position); put("note",note.take(10000)); put("created",System.currentTimeMillis()) }) }
    suspend fun editBookmark(id: Long, note: String) = change { db -> db.update("bookmarks",ContentValues().apply { put("note",note.take(10000)) },"id=?",arrayOf("$id")) }
    suspend fun restoreBookmark(mark: Bookmark) = change { db -> db.insertWithOnConflict("bookmarks",null,ContentValues().apply { put("id",mark.id); put("bookId",mark.bookId); put("position",mark.position); put("note",mark.note.take(10000)); put("created",mark.created) },SQLiteDatabase.CONFLICT_IGNORE) }
    suspend fun removeBookmark(id: Long) = change { db -> db.delete("bookmarks","id=?",arrayOf("$id")) }
    suspend fun remove(id: Long) = change { db -> db.delete("books","id=?",arrayOf("$id")) }
    suspend fun rememberFolder(uri: String) = change { db -> db.insertWithOnConflict("folders",null,ContentValues().apply { put("uri",uri) },SQLiteDatabase.CONFLICT_IGNORE) }
    suspend fun forgetFolder(uri: String) = change { db -> db.delete("folders","uri=?",arrayOf(uri)) }
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val root = JSONObject().put("version",1).put("exported",System.currentTimeMillis())
        val books = JSONArray()
        library.value.books.forEach { b ->
            val json = JSONObject().put("title",b.title).put("author",b.author).put("narrator",b.narrator).put("description",b.description).put("genre",b.genre).put("position",b.position).put("favorite",b.favorite).put("finished",b.finished).put("started",b.started).put("speed",b.speed).put("collection",b.collection)
            val trackList=tracks(b.id)
            json.put("uris",JSONArray(trackList.map { it.uri }))
            json.put("tracks",JSONArray(trackList.map { JSONObject().put("name",it.name).put("duration",it.duration) }))
            json.put("bookmarks",JSONArray(library.value.bookmarks.filter { it.bookId == b.id }.map { JSONObject().put("position",it.position).put("note",it.note).put("created",it.created) }))
            books.put(json)
        }
        root.put("books",books).put("readingHistory",JSONArray(library.value.readingHistory.map { it.json() })).toString(2)
    }
    // Backups restore metadata/progress only onto matching imported files. They never grant file access.
    suspend fun restore(text: String): Int {
        require(text.length <= 8_000_000) { "Backup exceeds the 8 MB limit" }
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported backup version" }
        val items = root.getJSONArray("books")
        require(items.length() <= 10000) { "Too many books in backup" }
        val history=root.optJSONArray("readingHistory")
        require(history==null || history.length()<=10000) { "Too many reading history entries" }
        val bookTracks=library.value.books.associateWith { tracks(it.id) }
        val uriMap = bookTracks.entries.associateBy({ it.value.firstOrNull()?.uri },{ it.key })
        fun signature(list:List<Track>)=list.joinToString("|") { "${it.name}:${it.duration/1000}" }
        val signatureMap=bookTracks.entries.groupBy { signature(it.value) }
        return change { db ->
            var restored = 0
            db.beginTransaction()
            try {
                for(i in 0 until items.length()) {
                    val j = items.getJSONObject(i)
                    val savedTracks=j.optJSONArray("tracks")
                    val savedSignature=if(savedTracks!=null) (0 until savedTracks.length()).joinToString("|") { index -> val t=savedTracks.getJSONObject(index);"${t.optString("name")}:${t.optLong("duration")/1000}" } else ""
                    val b = uriMap[j.getJSONArray("uris").optString(0)] ?: signatureMap[savedSignature]?.singleOrNull()?.key ?: continue
                    val updated = b.copy(title=j.optString("title",b.title).take(1000),author=j.optString("author",b.author).take(1000),narrator=j.optString("narrator").take(1000),description=j.optString("description").take(20000),genre=j.optString("genre").take(1000),position=j.optLong("position").coerceIn(0,b.duration),favorite=j.optBoolean("favorite"),finished=j.optBoolean("finished"),started=j.optBoolean("started",j.optLong("position")>0),progressRevision=b.progressRevision+1,speed=j.optDouble("speed",1.0).toFloat().let { if(it.isFinite()) it.coerceIn(.5f,3f) else 1f },collection=j.optString("collection").take(1000))
                    db.update("books",updated.values(),"id=?",arrayOf("${b.id}"))
                    db.delete("reading_prompts","bookId=?",arrayOf("${b.id}"))
                    val marks = j.optJSONArray("bookmarks") ?: JSONArray()
                    require(marks.length() <= 10000)
                    db.delete("bookmarks","bookId=?",arrayOf("${b.id}"))
                    for(m in 0 until marks.length()) { val mark=marks.getJSONObject(m); db.insertOrThrow("bookmarks",null,ContentValues().apply { put("bookId",b.id); put("position",mark.optLong("position").coerceIn(0,b.duration)); put("note",mark.optString("note").take(10000)); put("created",mark.optLong("created",System.currentTimeMillis())) }) }
                    restored++
                }
                if(history!=null) for(i in 0 until history.length()) {
                    val entry=ReadingEntry.fromJson(history.getJSONObject(i));entry.validate()
                    db.insertWithOnConflict("reading_history",null,entry.values(),SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful(); restored
            } finally { db.endTransaction() }
        }
    }
    private fun Cursor.str(key:String) = getString(getColumnIndexOrThrow(key)) ?: ""
    private fun Cursor.long(key:String) = getLong(getColumnIndexOrThrow(key))
    private fun Cursor.int(key:String) = getInt(getColumnIndexOrThrow(key))
    private fun Cursor.readingEntry()=ReadingEntry(str("id"),str("title"),str("author"),int("percent"),str("loggedOn"),str("notes"),str("source"),long("duration"))
    private fun ReadingEntry.values()=ContentValues().apply { put("id",id);put("title",title);put("author",author);put("percent",percent);put("loggedOn",loggedOn);put("notes",notes);put("source",source);put("duration",duration) }
    private fun Cursor.book() = Book(long("id"),str("title"),str("author"),str("narrator"),str("description"),str("genre"),str("cover"),str("source"),long("duration"),long("position"),long("added"),long("lastPlayed"),int("favorite")==1,int("finished")==1,getFloat(getColumnIndexOrThrow("speed")),str("collection"),str("format"),long("size"),int("started")==1,long("progressRevision"))
    private fun Book.values() = ContentValues().apply { put("title",title);put("author",author);put("narrator",narrator);put("description",description);put("genre",genre);put("cover",cover);put("source",source);put("duration",duration);put("position",position);put("added",added);put("lastPlayed",lastPlayed);put("favorite",favorite);put("finished",finished);put("speed",speed);put("collection",collection);put("format",format);put("size",size);put("started",started);put("progressRevision",progressRevision) }
}
