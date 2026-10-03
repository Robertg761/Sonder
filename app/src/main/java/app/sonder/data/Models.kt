package app.sonder.data

data class Book(
    val id: Long = 0, val title: String, val author: String = "Unknown author",
    val narrator: String = "", val description: String = "", val genre: String = "",
    val cover: String = "", val source: String = "", val duration: Long = 0,
    val position: Long = 0, val added: Long = System.currentTimeMillis(), val lastPlayed: Long = 0,
    val favorite: Boolean = false, val finished: Boolean = false, val speed: Float = 1f,
    val collection: String = "", val format: String = "", val size: Long = 0, val started:Boolean = false, val progressRevision:Long = 0
) { val inProgress:Boolean get() = !finished && (started || position>0)
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }
data class Track(val id: Long = 0, val bookId: Long = 0, val uri: String, val name: String, val duration: Long, val offset: Long = 0, val ordinal: Int = 0, val sortName:String = name)
data class Chapter(val title: String, val start: Long, val end: Long = 0)
data class Bookmark(val id: Long = 0, val bookId: Long, val position: Long, val note: String, val created: Long = System.currentTimeMillis())
data class Library(val books: List<Book> = emptyList(), val bookmarks: List<Bookmark> = emptyList(), val daily: Map<String, Long> = emptyMap(), val folders: List<String> = emptyList(), val readingHistory:List<ReadingEntry> = emptyList(), val completionPrompts:List<CompletionPrompt> = emptyList())
data class ImportProgress(val running: Boolean = false, val current: String = "", val done: Int = 0, val total: Int = 0, val errors: List<String> = emptyList())

enum class ListeningStatus(val label:String) { NOT_STARTED("Not started"), IN_PROGRESS("In progress"), FINISHED("Finished") }


data class ReadingEntry(
    val id:String=java.util.UUID.randomUUID().toString(), val title:String="", val author:String="",
    val percent:Int=100, val loggedOn:String=java.time.LocalDate.now().toString(), val notes:String="",
    val source:String="", val duration:Long=0
) {
    val position:Long get()=duration/100*percent+(duration%100)*percent/100
    fun json()=org.json.JSONObject().put("id",id).put("title",title).put("author",author).put("percent",percent).put("loggedOn",loggedOn).put("notes",notes).put("source",source).put("duration",duration)
    fun validate() {
        require(java.util.UUID.fromString(id).toString()==id) { "Invalid reading entry ID" }
        require(title.isNotBlank() && title.length<=1000 && author.length<=1000 && notes.length<=10000 && source.length<=4000) { "Reading entry text is invalid or too long" }
        require(percent in 0..100 && duration in 0..31_536_000_000L) { "Invalid reading progress" }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(loggedOn) && !java.time.LocalDate.parse(loggedOn).isAfter(java.time.LocalDate.now())) { "Choose a valid date, today or earlier" }
    }
    companion object {
        fun fromBook(book:Book,complete:Boolean=book.finished)=ReadingEntry(title=book.title.take(1000),author=book.author.take(1000),percent=if(complete) 100 else (book.progress*100).toInt(),source=book.source.take(4000),duration=book.duration.coerceIn(0,31_536_000_000L))
        fun fromJson(j:org.json.JSONObject)=ReadingEntry(j.getString("id"),j.getString("title"),j.optString("author"),j.getInt("percent"),j.getString("loggedOn"),j.optString("notes"),j.optString("source"),j.optLong("duration"))
    }
}
data class CompletionPrompt(val bookId:Long,val entry:ReadingEntry)
