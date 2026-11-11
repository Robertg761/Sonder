package app.sonder.data

/**
 * Audiobooks often open and close with short tracks that aren't chapters of the book: credits, a content warning, a dedication, a prologue.
 * These titles mark such tracks so they don't count toward the chapter number and total shown to the listener.
 */
private val matterTitle=Regex(
    "^(?:(?:opening|closing|end|beginning)\\s+credits?|credits?|content\\s+(?:warnings?|notes?)|trigger\\s+warnings?|dedications?|preamble|epigraphs?|" +
        "fore(?:word|ward)|prefaces?|prologue|epilogue|afterword|introduction|intro|outro|acknowledge?ments?|about\\s+the\\s+(?:author|authors|narrator|narrators|publisher)|" +
        "(?:a\\s+)?(?:note|notes)\\s+from\\s+the\\s+(?:author|publisher)|author'?s?\\s+notes?|copyright(?:\\s+page)?|title\\s+page|(?:table\\s+of\\s+)?contents|" +
        "also\\s+by\\s+.*|praise\\s+for\\s+.*|maps?|glossary|pronunciation\\s+guide|dramatis\\s+personae|cast(?:\\s+of\\s+characters)?|bonus(?:\\s+.*)?|teaser|excerpt(?:\\s+.*)?)$",
    RegexOption.IGNORE_CASE,
)

private val trackNumber=Regex("^\\d+\\s*[-–—._:)]?\\s*")
private val separator=Regex("\\s+[-–—]\\s+|:\\s+")

/**
 * True when [title] names front or back matter rather than a chapter of the book.
 * A leading track number is ignored, and so is a book-title prefix such as "Fourth Wing - Opening Credits", which is how track files are often named.
 */
fun isBookMatter(title:String):Boolean {
    fun clean(s:String)=s.replace('_',' ').trim().replace(trackNumber,"").trim().trimEnd('.',':',' ')
    val name=clean(title).replace(Regex("\\s+")," ")
    return matterTitle.matches(name) || matterTitle.matches(clean(name.split(separator).last()))
}

/**
 * The listener-facing number of each chapter, or null for front or back matter that doesn't count.
 * If every entry looks like matter, nothing is excluded, so a book always has numbered chapters.
 */
fun chapterNumbers(chapters:List<Chapter>):List<Int?> {
    val matter=chapters.map { isBookMatter(it.title) }
    if(matter.all { it }) return chapters.indices.map { it+1 }
    var n=0
    return matter.map { if(it) null else ++n }
}
