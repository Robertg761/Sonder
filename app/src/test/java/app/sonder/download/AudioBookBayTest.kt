package app.sonder.download

import org.junit.Assert.*
import org.junit.Test

class AudioBookBayTest {
    private val site="https://audiobookbay.lu"
    // Trimmed from the site's search markup, including its unquoted &nbsp spacing and a non-book "post".
    private val search="""
        <div class="post"><div class="postTitle"><h2><a href="/abss/the-long-way-jane-doe/" rel="bookmark">The Long Way &#8211; Jane Doe</a></h2></div><div class="postInfo">Category: Adventure&nbsp; Sci-Fi&nbsp; <br />Language: English<span style="margin-left:100px;">Keywords: Space&nbsp </span><br /></div><div class="postContent"><div class="center">
        <p class="center">Shared by:<a href="/member/users/index?&amp;mode=userinfo&amp;username=someone">someone</a></p>
        <p class="center"><a href="https://audiobookbay.lu/abss/the-long-way-jane-doe/"><img src="https://images.example.com/cover.jpg" alt="Jane Doe The Long Way" width="250" /></a></p>
        </div>
        <p style='text-align:center;'>Posted: 14 Nov 2021<br />Format: <span style='color:#a00;'>M4B</span> / Bitrate: <span style='color:#a00;'>128 Kbps</span><br />File Size: <span style='color:#00f;'>881.82</span> MBs</p>
        </div></div><div class="post"><div class="postTitle"><h2><a href="/abss/short-one/" rel="bookmark">Short One</a></h2></div><div class="postInfo">Category: Other&nbsp; <br />Language: English</div>
        <p style='text-align:center;'>Posted: 6 Oct 2026<br />Format: <span style='color:#a00;'>MP3</span> / Bitrate: <span style='color:#a00;'>?</span><br />File Size: <span style='color:#00f;'>1.2</span> GBs</p>
        </div><div class="post"><div class="postTitle"><h2><a href="/advertise/" rel="bookmark">Advertise here</a></h2></div></div>
        <div class="wp-pagenavi"><span class="pages"></span><span class="current">1</span><a href="/page/2/?s=jane&tt=1" title="2">2</a></div>
    """.trimIndent()
    private val details="""
        <title>The Long Way - Jane Doe Audiobook M4B </title>
        <div class="postTitle"><h1 itemprop="name">The Long Way - Jane Doe</h1></div>
        <p class="center"><a href="https://audiobookbay.lu/abss/the-long-way-jane-doe/"><img src="https://images.example.com/large.jpg" alt="cover" width="250" itemprop="image" /></a></p>
        <div class="desc" itemprop="description">
        <p style="left;">Written by <a href="/?s=jane+doe&#038;tt=1"><span class="author" itemprop="author" itemtype="https://schema.org/Person">Jane Doe</span></a> <br />Read by <span class="narrator" itemprop="author">Sam Reader</span><br />Format: <span class="format" itemprop="encodingFormat">M4B</span> <br />Bitrate: <span class="bitrate" itemprop="bitrate">128 Kbps</span> <br /><span class="is_abridged">Unabridged</span> </p>
        <p>  A long road home.<br />  It doesn&#8217;t end.</p>
        </div>
        <table border='1'>
        <tr><td style='width:150px;'>Announce URL:</td><td style='width:323px;'>http://tracker.example.com:1337/announce</td></tr>
        <tr><td colspan='2'>This Torrent also has several backup trackers</td></tr>
        <tr><td>Tracker:</td><td>http://tracker.example.com:1337/announce</td></tr>
        <tr><td>Tracker:</td><td>udp://open.example.org:6969/announce</td></tr>
        <tr><td colspan='2'>This is a Multifile Torrent</td></tr>
        <tr><td colspan='2'>The Long Way.cue 1.74 KBs</td></tr>
        <tr><td colspan='2'>.pad 87324 85.28 KBs</td></tr>
        <tr><td colspan='2'>The Long Way.m4b 881.76 MBs</td></tr>
        <tr><td>Combined File Size:</td><td><span style='color:#00f;'>881.82</span> MBs</td></tr>
        <tr><td>Info Hash:</td><td>AD5FAE5FFDA056F9F45131045D140326BBAFC4DC</td></tr>
        </table>
    """.trimIndent()

    @Test fun readsSearchResultsAndSkipsNonBookPosts() {
        val page=AudioBookBay.parseSearch(search,site)
        assertEquals(2,page.results.size)
        val first=page.results[0]
        assertEquals("The Long Way – Jane Doe",first.title)
        assertEquals("https://audiobookbay.lu/abss/the-long-way-jane-doe/",first.url)
        assertEquals("https://images.example.com/cover.jpg",first.cover)
        assertEquals("Adventure, Sci-Fi",first.category)
        assertEquals("English",first.language)
        assertEquals(listOf("M4B","128 Kbps","881.82 MB","14 Nov 2021"),listOf(first.format,first.bitrate,first.size,first.posted))
        assertEquals("",page.results[1].bitrate)
        assertEquals("1.2 GB",page.results[1].size)
        assertTrue(page.next)
        assertFalse(AudioBookBay.parseSearch(search.replace("/page/2/",""),site).next)
    }
    @Test fun readsDetailsHashTrackersAndFiles() {
        val d=AudioBookBay.parseDetails(details,"https://audiobookbay.lu/abss/the-long-way-jane-doe/",site)
        assertEquals("The Long Way - Jane Doe",d.title)
        assertEquals("Jane Doe",d.author)
        assertEquals("Sam Reader",d.narrator)
        assertEquals("M4B",d.format)
        assertEquals("881.82 MB",d.size)
        assertEquals("https://images.example.com/large.jpg",d.cover)
        assertEquals("ad5fae5ffda056f9f45131045d140326bbafc4dc",d.hash)
        assertEquals(listOf("http://tracker.example.com:1337/announce","udp://open.example.org:6969/announce"),d.trackers)
        assertEquals(listOf("The Long Way.cue","The Long Way.m4b"),d.files.map { it.name })
        assertTrue(d.playable);assertTrue(d.suspicious.isEmpty())
        assertTrue(d.description.contains("It doesn’t end."))
    }
    @Test fun flagsUploadsWithoutAudioOrWithPrograms() {
        val fake=details.replace("The Long Way.m4b 881.76 MBs","The Long Way.exe 2.10 MBs")
        val d=AudioBookBay.parseDetails(fake,"https://audiobookbay.lu/abss/x/",site)
        assertFalse(d.playable)
        assertEquals(listOf("The Long Way.exe"),d.suspicious)
    }
    @Test fun buildsMagnetWithEncodedNameAndTrackers() {
        val magnet=AudioBookBay.magnet("abc123","The Long Way & Back",listOf("udp://t.example.org:6969/announce"))
        assertEquals("magnet:?xt=urn:btih:abc123&dn=The%20Long%20Way%20%26%20Back&tr=udp%3A%2F%2Ft.example.org%3A6969%2Fannounce",magnet)
    }
    @Test fun missingHashIsAnError() {
        assertThrows(java.io.IOException::class.java) { AudioBookBay.parseDetails("<h1>Removed</h1>","https://audiobookbay.lu/abss/x/",site) }
    }
    @Test fun normalizesSearchesTheWaySiteMatchesTitles() {
        assertEquals("project hail mary andy weir",AudioBookBay.normalize("  Project Hail Mary - Andy Weir! "))
        assertEquals("ender's game",AudioBookBay.normalize("Ender’s Game:"))
        assertEquals("mistborn 1 final empire",AudioBookBay.normalize("Mistborn #1: (Final Empire)"))
    }
    @Test fun plansLoosenTheSearchStepByStep() {
        val plans=AudioBookBay.plans("The Project Hail Mary by Andy Weir")
        assertEquals(AudioBookBay.Plan("the project hail mary by andy weir"),plans[0])
        assertEquals(AudioBookBay.Plan("project hail mary andy weir"),plans[1])
        assertEquals(AudioBookBay.Plan("project hail mary andy weir",titles=false),plans[2])
        assertEquals(AudioBookBay.Plan("project hail"),plans[3])
        assertEquals(listOf("project","hail"),plans.drop(4).map { it.query })
        assertEquals(listOf(AudioBookBay.Plan("sanderson"),AudioBookBay.Plan("sanderson",titles=false)),AudioBookBay.plans("Sanderson"))
        assertTrue(AudioBookBay.plans(" - ").isEmpty())
    }
    @Test fun ranksCloseTitlesFirstAndDropsUnrelatedOnes() {
        fun listing(title:String)=AudioBookBay.Listing(title,"https://audiobookbay.lu/abss/${title.hashCode()}/")
        val results=listOf(listing("Entire Audiobooks Collection - Various"),listing("Dungeon Crawler Carl Books 1-8 - Matt Dinniman"),listing("Carl Sagan's Cosmos"),listing("Dungeon Crawler Carl - Matt Dinniman"))
        val ranked=AudioBookBay.rank(results,"dungen crawler carl").map { it.title }
        assertEquals(listOf("Dungeon Crawler Carl Books 1-8 - Matt Dinniman","Dungeon Crawler Carl - Matt Dinniman","Carl Sagan's Cosmos"),ranked)
        assertEquals("Ender’s Game - Orson Scott Card",AudioBookBay.rank(listOf(listing("Game of Thrones"),listing("Ender’s Game - Orson Scott Card")),"enders game").first().title)
    }
    @Test fun normalizesSiteAddresses() {
        assertEquals("https://audiobookbay.lu",AudioBookBay.site("audiobookbay.lu"))
        assertEquals("https://audiobookbay.is",AudioBookBay.site(" https://AudioBookBay.is/some/page "))
        assertEquals(AudioBookBay.DEFAULT_SITE,AudioBookBay.site(""))
        assertThrows(IllegalArgumentException::class.java) { AudioBookBay.site("http://audiobookbay.lu") }
        assertThrows(IllegalArgumentException::class.java) { AudioBookBay.site("not a site") }
        assertEquals("https://audiobookbay.lu/page/3/?s=jane+doe&tt=1",AudioBookBay.searchUrl("https://audiobookbay.lu"," Jane Doe ",3))
        assertEquals("https://audiobookbay.lu/?s=jane+doe",AudioBookBay.searchUrl("https://audiobookbay.lu","Jane Doe",titles=false))
    }
}
