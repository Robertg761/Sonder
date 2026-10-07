package app.sonder.download

import app.sonder.data.Importer

/** Decides which torrent files to download and what to call them in the download folder. */
object DownloadPlan {
    private val images=setOf("jpg","jpeg","png")
    /** Never downloaded. Audiobook uploads don't need these, and fakes often contain them. */
    val risky=setOf("exe","msi","bat","cmd","scr","com","pif","lnk","vbs","js","jar","apk","ps1","dmg")
    val archives=setOf("zip","rar","7z","tar","gz")
    fun extension(name:String)=name.substringAfterLast('/').substringAfterLast('.',"").lowercase()
    fun isAudio(name:String)=extension(name) in Importer.extensions

    /** Audio, cue sheets, and at most one cover image. Torrent padding and macOS metadata are skipped. */
    fun wanted(files:List<RealDebrid.File>):List<RealDebrid.File> {
        val clean=files.filter { f -> f.path.split('/').none { it.startsWith(".pad") || it=="__MACOSX" || it.startsWith("._") } }
        if(clean.none { isAudio(it.path) }) return emptyList()
        return (clean.filter { isAudio(it.path) || extension(it.path)=="cue" }+listOfNotNull(cover(clean))).sortedBy { it.id }
    }
    /** A named cover, or the largest of a few images. Many images usually means scanned pages, not art. */
    fun cover(files:List<RealDebrid.File>):RealDebrid.File? {
        val found=files.filter { extension(it.path) in images }
        return found.firstOrNull { it.path.substringAfterLast('/').substringBeforeLast('.').lowercase() in setOf("cover","folder") } ?: found.takeIf { it.size in 1..3 }?.maxByOrNull { it.bytes }
    }

    /**
     * Flattens the torrent into one book folder. Folders shared by every file are dropped and disc folders
     * become name prefixes ("CD1 - 01.mp3"), so the importer groups all tracks as one book in order.
     * The cover is saved as cover.jpg so the importer can use it for books without embedded art.
     */
    fun names(files:List<RealDebrid.File>):Map<Int,String> {
        val cover=cover(files)
        val parts=files.associate { f -> f.id to f.path.split('/').filter { it.isNotBlank() } }
        var common=0
        while(parts.values.all { it.size>common+1 } && parts.values.map { it[common] }.distinct().size==1) common++
        val used=mutableSetOf<String>()
        return files.sortedBy { it.id }.associate { f ->
            val name=if(f==cover) "cover.${extension(f.path).replace("jpeg","jpg")}" else sanitize(parts.getValue(f.id).drop(common).joinToString(" - "))
            f.id to unique(name,used)
        }
    }
    fun unique(name:String,used:MutableSet<String>):String {
        var candidate=name;var n=2
        val base=name.substringBeforeLast('.');val ext=name.substringAfterLast('.',"").let { if(it.isEmpty()) "" else ".$it" }
        while(!used.add(candidate.lowercase())) candidate="$base (${n++})$ext"
        return candidate
    }
    /** Removes characters that Android's shared storage (FAT-style names) rejects. */
    fun sanitize(name:String,max:Int=150):String {
        val clean=name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"),"_").replace(Regex("\\s+")," ").trim().trim('.',' ')
        if(clean.isEmpty()) return "file"
        if(clean.length<=max) return clean
        val ext=extension(clean).takeIf { it.isNotEmpty() && it.length<=5 }?.let { ".$it" }.orEmpty()
        return clean.dropLast(ext.length).take(max-ext.length).trimEnd('.',' ')+ext
    }
    fun folderName(title:String)=sanitize(title,120).takeIf { name -> name.any { it.isLetterOrDigit() } } ?: "Audiobook"
}
