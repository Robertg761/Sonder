package app.sonder.media

import app.sonder.data.Chapter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Bounded metadata parsing; large media payloads are skipped. */
object ChapterParser {
    interface Source { val size: Long; fun read(offset:Long,count:Int):ByteArray }
    class ChannelSource(private val channel:FileChannel) : Source {
        override val size get()=channel.size()
        override fun read(offset:Long,count:Int):ByteArray {
            require(offset>=0 && count>=0 && count<=16*1024*1024 && offset<=size-count)
            val b=ByteBuffer.allocate(count); var at=offset
            while(b.hasRemaining()) { val n=channel.read(b,at); if(n<=0) error("Truncated metadata"); at+=n }
            return b.array()
        }
    }
    class BytesSource(private val bytes:ByteArray) : Source {
        override val size=bytes.size.toLong()
        override fun read(offset:Long,count:Int):ByteArray { require(offset>=0 && count>=0 && offset<=size-count);return bytes.copyOfRange(offset.toInt(),Math.addExact(offset.toInt(),count)) }
    }
    data class Atom(val type:String,val start:Long,val data:Long,val end:Long)
    private fun u32(b:ByteArray,p:Int)=ByteBuffer.wrap(b,p,4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
    private fun u64(b:ByteArray,p:Int)=ByteBuffer.wrap(b,p,8).order(ByteOrder.BIG_ENDIAN).long
    private fun atoms(s:Source,start:Long,end:Long):List<Atom> {
        val result=mutableListOf<Atom>();var p=start
        while(p<=end-8 && result.size<20000) {
            val h=s.read(p,8);var length=u32(h,0);var header=8L
            if(length==1L) { if(p>end-16) break;length=u64(s.read(p+8,8),0);header=16 }
            if(length==0L) length=end-p
            if(length<header || length>end-p) break
            result+=Atom(String(h,4,4,Charsets.ISO_8859_1),p,p+header,p+length);p+=length
        }
        return result
    }
    fun read(s:Source,extension:String):List<Chapter> = runCatching {
        val list=when(extension.lowercase()) {
            "m4b","m4a","mp4" -> mp4(s)
            "mp3" -> id3(s)
            "ogg","opus","flac" -> vorbis(s)
            else -> emptyList()
        }
        list.filter { it.start>=0 }.sortedBy { it.start }.distinctBy { it.start }
    }.getOrDefault(emptyList())
    private fun mp4(s:Source):List<Chapter> {
        val moov=atoms(s,0,s.size).firstOrNull { it.type=="moov" } ?: return emptyList()
        val children=atoms(s,moov.data,moov.end)
        val udta=children.firstOrNull { it.type=="udta" }
        val chpl=(if(udta!=null) atoms(s,udta.data,udta.end) else children).firstOrNull { it.type=="chpl" }
        if(chpl!=null) {
            val b=s.read(chpl.data,(chpl.end-chpl.data).toInt());var p=4
            if(b.size>0 && b[0].toInt()==1) p+=4
            if(p>=b.size) return emptyList()
            val count=b[p++].toInt() and 255;val chapters=mutableListOf<Chapter>()
            repeat(count) {
                if(p+9>b.size) return chapters
                val time=u64(b,p)/10000;p+=8;val n=b[p++].toInt() and 255
                if(p+n>b.size) return chapters
                chapters+=Chapter(String(b,p,n,Charsets.UTF_8).ifBlank { "Chapter ${it+1}" },time);p+=n
            }
            if(chapters.isNotEmpty()) return chapters
        }
        val traks=children.filter { it.type=="trak" }
        val referenced=mutableSetOf<Long>()
        traks.forEach { trak ->
            val tref=atoms(s,trak.data,trak.end).firstOrNull { it.type=="tref" }
            if(tref!=null) atoms(s,tref.data,tref.end).filter { it.type=="chap" }.forEach { a ->
                val bytes=s.read(a.data,(a.end-a.data).toInt().coerceAtMost(1024))
                for(i in 0..bytes.size-4 step 4) referenced+=u32(bytes,i)
            }
        }
        traks.forEach { trak ->
            val items=atoms(s,trak.data,trak.end)
            val tkhd=items.firstOrNull { it.type=="tkhd" } ?: return@forEach
            val tk=s.read(tkhd.data,minOf(40, (tkhd.end-tkhd.data).toInt()))
            val id=u32(tk,if(tk[0].toInt()==1) 20 else 12)
            if(id !in referenced) return@forEach
            val mdia=items.firstOrNull { it.type=="mdia" } ?: return@forEach
            val md=atoms(s,mdia.data,mdia.end)
            val mdhd=md.firstOrNull { it.type=="mdhd" } ?: return@forEach
            val mh=s.read(mdhd.data,minOf(40,(mdhd.end-mdhd.data).toInt()))
            val scale=u32(mh,if(mh[0].toInt()==1) 20 else 12)
            if(scale==0L) return@forEach
            val minf=md.firstOrNull { it.type=="minf" } ?: return@forEach
            val stbl=atoms(s,minf.data,minf.end).firstOrNull { it.type=="stbl" } ?: return@forEach
            val tables=atoms(s,stbl.data,stbl.end)
            fun table(type:String)=tables.firstOrNull { it.type==type }?.let { s.read(it.data,(it.end-it.data).toInt()) }
            val sz=table("stsz") ?: return@forEach
            val fixed=u32(sz,4);val count=u32(sz,8).toInt()
            if(count !in 1..10000) return@forEach
            val sizes=LongArray(count) { if(fixed>0) fixed else u32(sz,12+it*4) }
            val timing=table("stts") ?: return@forEach
            val starts=LongArray(count);var sample=0;var time=0L
            for(i in 0 until u32(timing,4).toInt().coerceAtMost(10000)) {
                val n=u32(timing,8+i*8).coerceAtMost(count.toLong()).toInt();val delta=u32(timing,12+i*8)
                repeat(n) { if(sample<count) { starts[sample++]=time*1000/scale;time+=delta } }
            }
            val stsc=table("stsc") ?: return@forEach
            val runs=(0 until u32(stsc,4).toInt().coerceAtMost(10000)).map { u32(stsc,8+it*12).toInt() to u32(stsc,12+it*12).toInt() }
            val co=table("stco");val c64=if(co==null) table("co64") else null
            val offsets=co ?: c64 ?: return@forEach
            val chunks=u32(offsets,4).toInt().coerceAtMost(10000);sample=0
            val result=mutableListOf<Chapter>()
            for(chunk in 1..chunks) {
                var pos=if(co!=null) u32(offsets,8+(chunk-1)*4) else u64(offsets,8+(chunk-1)*8)
                val perChunk=runs.lastOrNull { it.first<=chunk }?.second?.coerceIn(0,10000) ?: 0
                repeat(perChunk) {
                    if(sample<count) {
                        val size=sizes[sample]
                        if(size in 2..65536 && pos>=0 && pos<=s.size-size) {
                            val bytes=s.read(pos,size.toInt());val n=((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
                            if(n in 1..bytes.size-2) result+=Chapter(String(bytes,2,n,Charsets.UTF_8),starts[sample])
                        }
                        pos+=size;sample++
                    }
                }
            }
            if(result.isNotEmpty()) return result
        }
        return emptyList()
    }
    private fun sync(b:ByteArray,p:Int):Int { var n=0; repeat(4) { n=(n shl 7) or (b[p+it].toInt() and 127) };return n }
    private fun text(b:ByteArray):String {
        if(b.isEmpty()) return ""
        val charset=when(b[0].toInt()) { 1 -> Charsets.UTF_16;2 -> Charsets.UTF_16BE;3 -> Charsets.UTF_8;else -> Charsets.ISO_8859_1 }
        return String(b,1,b.size-1,charset).trim('\u0000',' ')
    }
    private fun id3(s:Source):List<Chapter> {
        if(s.size<10) return emptyList()
        val header=s.read(0,10);if(String(header,0,3)!="ID3") return emptyList()
        val version=header[3].toInt();if(version !in 3..4) return emptyList()
        val size=sync(header,6);if(size>8*1024*1024 || size+10>s.size) return emptyList()
        val data=s.read(10,size);var p=0
        if(header[5].toInt() and 0x40!=0) { if(data.size<4) return emptyList(); p=if(version==4) sync(data,0) else u32(data,0).toInt()+4 }
        val chapters=mutableListOf<Chapter>()
        while(p<=data.size-10) {
            val frame=String(data,p,4);val n=if(version==4) sync(data,p+4) else u32(data,p+4).toInt()
            if(n<=0 || n>data.size-p-10) break
            val start=p+10;val end=start+n
            if(frame=="CHAP") {
                var q=start;while(q<end && data[q]!=0.toByte()) q++
                q++
                if(q+16<=end) {
                    val at=u32(data,q);val until=u32(data,q+4);q+=16
                    var title="Chapter ${chapters.size+1}"
                    while(q<=end-10) {
                        val tag=String(data,q,4);val len=if(version==4) sync(data,q+4) else u32(data,q+4).toInt()
                        if(len<=0 || len>end-q-10) break
                        if(tag=="TIT2") title=text(data.copyOfRange(q+10,q+10+len))
                        q+=10+len
                    }
                    chapters+=Chapter(title,at,if(until==0xffffffffL) 0 else until)
                }
            }
            p=end
        }
        return chapters
    }
    private const val COMMENT_LIMIT=2*1024*1024
    private fun comments(bytes:ByteArray,start:Int):Map<String,String> {
        var p=start
        fun length():Int {
            require(p<=bytes.size-4)
            val n=ByteBuffer.wrap(bytes,p,4).order(ByteOrder.LITTLE_ENDIAN).int
            p+=4;require(n>=0 && n<=bytes.size-p);return n
        }
        val vendorLength=length();p+=vendorLength
        require(p<=bytes.size-4)
        val count=ByteBuffer.wrap(bytes,p,4).order(ByteOrder.LITTLE_ENDIAN).int;p+=4
        require(count in 0..10000)
        return buildMap {
            repeat(count) {
                val n=length();val comment=String(bytes,p,n,Charsets.UTF_8);p+=n
                val separator=comment.indexOf('=')
                if(separator>0) put(comment.substring(0,separator).uppercase(java.util.Locale.ROOT),comment.substring(separator+1))
            }
        }
    }
    private fun vorbisComments(s:Source):Map<String,String> {
        if(s.size<4) return emptyMap()
        if(String(s.read(0,4),Charsets.US_ASCII)=="fLaC") {
            var p=4L
            repeat(128) {
                if(p>s.size-4) return emptyMap()
                val header=s.read(p,4);val type=header[0].toInt() and 127
                val length=((header[1].toInt() and 255) shl 16) or ((header[2].toInt() and 255) shl 8) or (header[3].toInt() and 255)
                p+=4;if(length>s.size-p) return emptyMap()
                if(type==4) return if(length<=COMMENT_LIMIT) comments(s.read(p,length),0) else emptyMap()
                p+=length;if(header[0].toInt() and 128!=0) return emptyMap()
            }
            return emptyMap()
        }
        val bytes=s.read(0,minOf(s.size,COMMENT_LIMIT.toLong()).toInt())
        val packets=mutableMapOf<Long,ByteArrayOutputStream>();var p=0
        while(p<=bytes.size-27) {
            if(String(bytes,p,4,Charsets.US_ASCII)!="OggS" || bytes[p+4]!=0.toByte()) break
            val serial=ByteBuffer.wrap(bytes,p+14,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            val segments=bytes[p+26].toInt() and 255;val headerEnd=p+27+segments
            if(headerEnd>bytes.size) break
            var data=headerEnd
            val packet=packets.getOrPut(serial) { ByteArrayOutputStream() }
            for(i in 0 until segments) {
                val n=bytes[p+27+i].toInt() and 255
                if(n>bytes.size-data || packet.size()>COMMENT_LIMIT-n) return emptyMap()
                packet.write(bytes,data,n);data+=n
                if(n<255) {
                    val complete=packet.toByteArray();packet.reset()
                    val offset=when {
                        complete.size>=7 && complete[0]==3.toByte() && String(complete,1,6,Charsets.US_ASCII)=="vorbis" -> 7
                        complete.size>=8 && String(complete,0,8,Charsets.US_ASCII)=="OpusTags" -> 8
                        else -> -1
                    }
                    if(offset>=0) return comments(complete,offset)
                }
            }
            p=data
        }
        return emptyMap()
    }
    private fun vorbis(s:Source):List<Chapter> {
        val tags=vorbisComments(s)
        return tags.mapNotNull { (key,value) ->
            val index=Regex("CHAPTER(\\d{3})").matchEntire(key)?.groupValues?.get(1) ?: return@mapNotNull null
            val g=Regex("(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?").matchEntire(value)?.groupValues ?: return@mapNotNull null
            if(g[2].toInt()>59 || g[3].toInt()>59) return@mapNotNull null
            val ms=Math.addExact(Math.multiplyExact(g[1].toLong(),3600000),g[2].toLong()*60000+g[3].toLong()*1000+g[4].padEnd(3,'0').ifEmpty { "0" }.toLong())
            Chapter(tags["CHAPTER${index}NAME"]?.take(1000) ?: "Chapter ${index.toInt()}",ms)
        }
    }
    fun tags(s:Source,extension:String):Map<String,String> = runCatching {
        when(extension.lowercase()) {
            "m4b","m4a","mp4" -> {
                val moov=atoms(s,0,s.size).firstOrNull { it.type=="moov" } ?: return@runCatching emptyMap()
                val child=atoms(s,moov.data,moov.end)
                val udta=child.firstOrNull { it.type=="udta" }
                val containers=if(udta!=null) child+atoms(s,udta.data,udta.end) else child
                val meta=containers.firstOrNull { it.type=="meta" } ?: return@runCatching emptyMap()
                val ilst=atoms(s,meta.data+4,meta.end).firstOrNull { it.type=="ilst" } ?: return@runCatching emptyMap()
                buildMap {
                    atoms(s,ilst.data,ilst.end).forEach { tag ->
                        val fields=atoms(s,tag.data,tag.end)
                        val value=fields.firstOrNull { it.type=="data" }?.let { data -> if(data.end-data.data in 9..262144) String(s.read(data.data+8,(data.end-data.data-8).toInt()),Charsets.UTF_8).trim('\u0000') else "" }.orEmpty()
                        val key=if(tag.type=="----") fields.firstOrNull { it.type=="name" }?.let { name -> if(name.end-name.data in 5..1024) String(s.read(name.data+4,(name.end-name.data-4).toInt()),Charsets.UTF_8).lowercase() else "" }.orEmpty() else tag.type
                        if(value.isNotBlank()) when(key) {
                            "desc","ldes","©cmt","description" -> put("description",value.take(20000))
                            "narrator","©nrt","narrated by" -> put("narrator",value.take(1000))
                            "author" -> put("author",value.take(1000))
                            "series","series_name" -> put("series",value.take(1000))
                        }
                    }
                }
            }
            "mp3" -> {
                if(s.size<10) return@runCatching emptyMap()
                val h=s.read(0,10);val version=h[3].toInt();val length=sync(h,6)
                if(String(h,0,3)!="ID3" || version !in 3..4 || length>8*1024*1024 || length>s.size-10) return@runCatching emptyMap()
                val data=s.read(10,length);var p=0
                if(h[5].toInt() and 0x40!=0) p=if(version==4) sync(data,0) else u32(data,0).toInt()+4
                buildMap {
                    while(p<=data.size-10) {
                        val frame=String(data,p,4);val n=if(version==4) sync(data,p+4) else u32(data,p+4).toInt()
                        if(n<=0 || n>data.size-p-10) break
                        if(frame=="TXXX") {
                            val v=text(data.copyOfRange(p+10,p+10+n));val parts=v.split('\u0000',limit=2)
                            if(parts.size==2) when(parts[0].lowercase()) { "narrator" -> put("narrator",parts[1].take(1000));"author" -> put("author",parts[1].take(1000));"series" -> put("series",parts[1].take(1000));"description" -> put("description",parts[1].take(20000)) }
                        }
                        p+=10+n
                    }
                }
            }
            "flac","ogg","opus" -> {
                val values=vorbisComments(s)
                buildMap {
                    (values["AUTHOR"] ?: values["ARTIST"])?.let { put("author",it.take(1000)) }
                    (values["NARRATOR"] ?: values["PERFORMER"])?.let { put("narrator",it.take(1000)) }
                    values["DESCRIPTION"]?.let { put("description",it.take(20000)) }
                    values["SERIES"]?.let { put("series",it.take(1000)) }
                }
            }
            else -> emptyMap()
        }
    }.getOrDefault(emptyMap())
    data class CueChapter(val file:String,val title:String,val position:Long)
    fun cue(text:String):List<CueChapter> {
        var file="";var title="";var track=0;val chapters=mutableListOf<CueChapter>()
        text.lineSequence().forEach { raw ->
            val line=raw.trim()
            Regex("FILE\\s+\"([^\"]+)\"",RegexOption.IGNORE_CASE).find(line)?.let { file=it.groupValues[1] }
            Regex("TRACK\\s+(\\d+)",RegexOption.IGNORE_CASE).find(line)?.let { track=it.groupValues[1].toInt();title="Chapter $track" }
            if(track>0) Regex("TITLE\\s+\"([^\"]+)\"",RegexOption.IGNORE_CASE).find(line)?.let { title=it.groupValues[1] }
            Regex("INDEX\\s+01\\s+(\\d+):(\\d+):(\\d+)",RegexOption.IGNORE_CASE).find(line)?.let { val g=it.groupValues;chapters+=CueChapter(file,title,g[1].toLong()*60000+g[2].toLong()*1000+g[3].toLong()*1000/75) }
        }
        return chapters
    }
    val naturalComparator=Comparator<String> { a,b ->
        val aa=Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
        val bb=Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
        var result=0
        for(i in 0 until minOf(aa.size,bb.size)) {
            val x=aa[i].toBigIntegerOrNull();val y=bb[i].toBigIntegerOrNull()
            result=if(x!=null && y!=null) x.compareTo(y) else aa[i].compareTo(bb[i]);if(result!=0) break
        }
        if(result==0) aa.size.compareTo(bb.size) else result
    }
}
