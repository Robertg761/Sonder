package app.sonder.update

/**
 * The small part of GitHub Markdown that release notes use, so the update sheet can show headings and bullets
 * instead of raw `##`, `-` and `**`. Links keep their text. Bold stays marked with `**` for [inline] runs.
 */
object ReleaseNotes {
    enum class Kind { HEADING,BULLET,PARAGRAPH }
    /** One block of notes. [level] is a bullet's nesting depth, from 0. */
    data class Block(val kind:Kind,val text:String,val level:Int=0)
    /** A stretch of a block's text, bold or not. */
    data class Run(val text:String,val bold:Boolean)

    private val bullet=Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val numbered=Regex("^(\\s*)\\d+[.)]\\s+(.*)$")
    private val heading=Regex("^#{1,6}\\s+(.*?)\\s*#*$")
    private val link=Regex("!?\\[([^\\]]*)]\\([^)]*\\)")

    fun parse(markdown:String):List<Block> {
        val blocks=mutableListOf<Block>()
        var open:Block?=null
        fun close() { open?.let { blocks+=it };open=null }
        for(raw in markdown.replace("\r\n","\n").lines()) {
            val line=raw.trimEnd()
            val text=line.trim()
            when {
                text.isEmpty() -> close()
                // Horizontal rules separate sections; the spacing between blocks already does that.
                text.matches(Regex("([-*_])\\s*(\\1\\s*){2,}")) -> close()
                heading.matches(text) -> { close();blocks+=Block(Kind.HEADING,clean(heading.find(text)!!.groupValues[1])) }
                bullet.matches(line) || numbered.matches(line) -> {
                    close()
                    val match=(bullet.find(line) ?: numbered.find(line))!!
                    open=Block(Kind.BULLET,clean(match.groupValues[2]),(match.groupValues[1].length/2).coerceAtMost(2))
                }
                // A wrapped line continues the bullet or paragraph above it.
                open!=null -> open=open!!.copy(text=open!!.text+" "+clean(text))
                else -> open=Block(Kind.PARAGRAPH,clean(text))
            }
        }
        close()
        return blocks
    }
    private fun clean(text:String)=text.replace(link) { it.groupValues[1] }.replace("`","").replace("__","**")

    /** Splits text on `**` pairs. An unpaired `**` is shown as written. */
    fun inline(text:String):List<Run> {
        val parts=text.split("**")
        if(parts.size%2==0) return listOf(Run(text,false))
        return parts.mapIndexedNotNull { i,part -> part.takeIf { it.isNotEmpty() }?.let { Run(it,i%2==1) } }
    }
}
