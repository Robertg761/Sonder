package app.sonder.download

import java.io.IOException
import java.net.URI

/** Reads AudioBookBay search and detail pages. The site has no API, so this follows its page markup. */
object AudioBookBay {
    const val DEFAULT_SITE="https://audiobookbay.lu"
    data class Listing(val title:String,val url:String,val cover:String="",val language:String="",val category:String="",val format:String="",val bitrate:String="",val size:String="",val posted:String="")
    data class Page(val results:List<Listing>,val page:Int,val next:Boolean)
    data class TorrentFile(val name:String,val size:String)
    data class Details(
        val url:String,val title:String,val author:String="",val narrator:String="",val format:String="",val bitrate:String="",
        val size:String="",val cover:String="",val description:String="",val hash:String,val trackers:List<String> = emptyList(),val files:List<TorrentFile> = emptyList()
    ) {
        val magnet:String get()=magnet(hash,title,trackers)
        /** Files that are worth downloading, judged from the page's file list. */
        val playable:Boolean get()=files.isEmpty() || files.any { DownloadPlan.isAudio(it.name) }
        /** Files Sonder never downloads and that suggest a fake or bundled upload. */
        val suspicious:List<String> get()=files.map { it.name }.filter { DownloadPlan.extension(it) in DownloadPlan.risky }
    }

    /** Accepts "audiobookbay.lu", a full URL, or a mirror address and returns "https://host". */
    fun site(value:String):String {
        val text=value.trim().ifBlank { DEFAULT_SITE }.let { if("://" in it) it else "https://$it" }
        val uri=runCatching { URI(text) }.getOrNull() ?: throw IllegalArgumentException("Enter a web address like audiobookbay.lu")
        require(uri.scheme.equals("https",true)) { "Use an https:// address." }
        val host=uri.host?.lowercase()?.takeIf { it.contains('.') } ?: throw IllegalArgumentException("Enter a web address like audiobookbay.lu")
        return "https://$host"+if(uri.port>0 && uri.port!=443) ":${uri.port}" else ""
    }
    fun searchUrl(site:String,query:String,page:Int=1):String {
        val q=Http.encode(query.trim().lowercase())
        return if(page<=1) "$site/?s=$q&tt=1" else "$site/page/$page/?s=$q&tt=1"
    }
    fun search(site:String,query:String,page:Int=1):Page = parseSearch(fetch(searchUrl(site,query,page)),site,page)
    fun details(site:String,listing:Listing):Details = parseDetails(fetch(listing.url),listing.url,site).let { d -> d.copy(cover=d.cover.ifBlank { listing.cover }) }
    private fun fetch(url:String):String {
        val response=try { Http.request(url) } catch(e:IOException) { throw IOException("Couldn't reach AudioBookBay. Check your connection or change its address in Settings.",e) }
        if(response.code!=200) throw IOException(if(response.code==403 || response.code==503) "AudioBookBay is blocking requests right now. Try again later or use another address in Settings." else "AudioBookBay returned an error (${response.code}).")
        return response.body
    }

    fun parseSearch(html:String,site:String,page:Int=1):Page {
        val results=html.split("<div class=\"post\">").drop(1).mapNotNull { post ->
            val link=Regex("<div class=\"postTitle\">\\s*<h2>\\s*<a href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",RegexOption.DOT_MATCHES_ALL).find(post) ?: return@mapNotNull null
            val url=absolute(link.groupValues[1],site)?.takeIf { "/abss/" in it } ?: return@mapNotNull null
            Listing(
                title=text(link.groupValues[2]).ifBlank { return@mapNotNull null },url=url,
                cover=Regex("<img[^>]+src=[\"']([^\"']+)[\"']").find(post)?.groupValues?.get(1)?.let { absolute(it,site) }.orEmpty(),
                language=first(post,"Language:\\s*([^<]+)"),
                category=Regex("Category:(.*?)<br",RegexOption.DOT_MATCHES_ALL).find(post)?.groupValues?.get(1)?.split("&nbsp;")?.map(::text)?.filter { it.isNotEmpty() }?.joinToString(", ").orEmpty(),
                format=first(post,"Format:\\s*<span[^>]*>([^<]*)</span>"),
                bitrate=first(post,"Bitrate:\\s*<span[^>]*>([^<]*)</span>").takeIf { it!="?" }.orEmpty(),
                size=Regex("File Size:\\s*<span[^>]*>([^<]*)</span>\\s*([A-Za-z]+)").find(post)?.let { size(it.groupValues[1],it.groupValues[2]) }.orEmpty(),
                posted=first(post,"Posted:\\s*([^<]+)")
            )
        }
        val current=Regex("<span class=['\"]current['\"]>(\\d+)</span>").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: page
        return Page(results,current,html.contains("/page/${current+1}/"))
    }

    fun parseDetails(html:String,url:String,site:String=DEFAULT_SITE):Details {
        val hash=Regex("Info Hash:\\s*</td>\\s*<td[^>]*>\\s*([0-9A-Fa-f]{40}|[A-Za-z2-7]{32})\\s*</td>").find(html)?.groupValues?.get(1)
            ?: throw IOException("This page has no torrent information. It may have been removed.")
        val title=text(Regex("<h1[^>]*>(.*?)</h1>",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: Regex("<title>(.*?)</title>",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1).orEmpty()).ifBlank { "Audiobook" }
        val authors=Regex("<span class=['\"]author['\"][^>]*>(.*?)</span>").findAll(html).map { text(it.groupValues[1]) }.filter { it.isNotBlank() }.distinct().toList()
        val narrators=Regex("<span class=['\"]narrator['\"][^>]*>(.*?)</span>").findAll(html).map { text(it.groupValues[1]) }.filter { it.isNotBlank() }.distinct().toList()
        val description=Regex("<div class=['\"]desc['\"][^>]*>(.*?)</div>",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1).orEmpty()
        val narrator=narrators.joinToString(", ").ifBlank { Regex("(?:Narrated by|Narrator|Read by)\\s*:?\\s*([^<\\n]+)").find(description)?.groupValues?.get(1)?.let(::text)?.substringBefore(" Format:").orEmpty() }
        val trackers=Regex("(?:Tracker|Announce URL):\\s*</td>\\s*<td[^>]*>\\s*([^<\\s]+)\\s*</td>").findAll(html).map { text(it.groupValues[1]) }
            .filter { t -> listOf("udp://","http://","https://","wss://").any { t.startsWith(it) } }.distinct().take(20).toList()
        val files=Regex("<td colspan=['\"]2['\"]>\\s*([^<]+?)\\s+([\\d.,]+)\\s*(Bytes|bytes|KBs|MBs|GBs|TBs)\\s*</td>").findAll(html)
            .map { TorrentFile(text(it.groupValues[1]),size(it.groupValues[2],it.groupValues[3])) }
            .filterNot { it.name.startsWith(".pad") || it.name.startsWith("This is") }.toList()
        val total=Regex("(?:Combined )?File Size:\\s*</td>\\s*<td[^>]*>(.*?)</td>",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::text)?.let { t -> Regex("([\\d.,]+)\\s*([A-Za-z]+)").find(t)?.let { size(it.groupValues[1],it.groupValues[2]) } ?: t }.orEmpty()
        val cover=(Regex("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*itemprop=[\"']image[\"']").find(html) ?: Regex("<img[^>]*itemprop=[\"']image[\"'][^>]*src=[\"']([^\"']+)[\"']").find(html))?.groupValues?.get(1)?.let { absolute(it,site) }.orEmpty()
        return Details(url,title,authors.joinToString(", "),narrator,first(html,"<span class=['\"]format['\"][^>]*>([^<]*)</span>"),first(html,"<span class=['\"]bitrate['\"][^>]*>([^<]*)</span>").takeIf { it!="?" }.orEmpty(),total,cover,paragraphs(description).lines().filterNot { meta.containsMatchIn(it) }.joinToString("\n").trim().take(6000),hash.lowercase(),trackers,files)
    }

    fun magnet(hash:String,title:String,trackers:List<String>):String {
        fun enc(s:String)=Http.encode(s).replace("+","%20")
        return "magnet:?xt=urn:btih:$hash&dn=${enc(title)}"+trackers.joinToString("") { "&tr=${enc(it)}" }
    }

    // The description repeats the page's metadata in its first lines.
    private val meta=Regex("^(Written by|Read by|Narrated by|Narrator|Format:|Bitrate:|Unabridged|Abridged)",RegexOption.IGNORE_CASE)
    private fun first(html:String,pattern:String)=Regex(pattern).find(html)?.groupValues?.get(1)?.let(::text).orEmpty()
    private fun size(number:String,unit:String):String {
        val label=when(unit.lowercase()) { "kbs" -> "KB";"mbs" -> "MB";"gbs" -> "GB";"tbs" -> "TB";"bytes" -> "bytes";else -> unit }
        return "${number.trim()} $label"
    }
    private fun absolute(href:String,site:String):String? {
        val value=decode(href.trim())
        return when {
            value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> site+value
            else -> null
        }
    }
    private fun paragraphs(html:String):String = decode(html.replace(Regex("(?i)<br\\s*/?>"),"\n").replace(Regex("(?i)</p>"),"\n\n").replace(Regex("<[^>]+>"),""))
        .lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"),"\n\n").trim()
    /** Strips tags and entities and collapses whitespace. */
    fun text(html:String):String = decode(html.replace(Regex("<[^>]+>")," ")).replace(Regex("[\\s\\u00a0]+")," ").trim()
    private val named=mapOf("amp" to "&","lt" to "<","gt" to ">","quot" to "\"","apos" to "'","nbsp" to " ","ndash" to "–","mdash" to "—","lsquo" to "‘","rsquo" to "’","ldquo" to "“","rdquo" to "”","hellip" to "…")
    fun decode(text:String):String = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(text) { m ->
        val key=m.groupValues[1]
        when {
            key.startsWith("#x",true) -> key.drop(2).toIntOrNull(16)?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
            key.startsWith("#") -> key.drop(1).toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
            else -> named[key.lowercase()]
        } ?: m.value
    }
}
