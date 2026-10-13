package app.sonder.download

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads the archives Real-Debrid makes when it packs an upload into one file: ZIP, and RAR (versions 4 and 5) whose
 * files are stored without compression. Audio is already compressed, so packed uploads are stored, not squeezed.
 */
object Archive {
    /** Formats [read] understands, by file extension. */
    val formats=setOf("zip","rar")

    /**
     * One file's bytes inside one archive. [size] counts those bytes, or is -1 when the archive doesn't say. [continued]
     * is true when the bytes carry on a file the previous volume of a split RAR started, so they belong after it.
     */
    data class Piece(val path:String,val size:Long,val continued:Boolean=false)

    /** A failure with a message meant for the person. */
    class Unsupported(message:String):IOException(message)

    /**
     * Calls [visit] for each file in [input] with a stream over just that file's bytes. Folders are skipped, paths use
     * forward slashes, and whatever [visit] leaves unread is skipped.
     */
    fun read(input:InputStream,visit:(Piece,InputStream)->Unit) {
        val stream=BufferedInputStream(input,64*1024)
        stream.mark(16)
        val magic=stream.readUpTo(8)
        stream.reset()
        when {
            magic.startsWith(PK) -> zip(stream,visit)
            magic.startsWith(RAR5) -> { stream.skipFully(RAR5.size.toLong());rar5(stream,visit) }
            magic.startsWith(RAR4) -> { stream.skipFully(RAR4.size.toLong());rar4(stream,visit) }
            else -> throw Unsupported("Real-Debrid sent an archive Sonder can't open.")
        }
    }

    private val PK=byteArrayOf(0x50,0x4b,0x03,0x04)
    private val RAR4=byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x00)
    private val RAR5=byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x01,0x00)
    private fun ByteArray.startsWith(prefix:ByteArray)=size>=prefix.size && prefix.indices.all { this[it]==prefix[it] }
    private fun compressed()=Unsupported("This archive is compressed in a way Sonder can't unpack. Try another upload.")
    private fun locked()=Unsupported("This archive is password protected, so Sonder can't unpack it. Try another upload.")

    private fun zip(input:InputStream,visit:(Piece,InputStream)->Unit) {
        val zip=ZipInputStream(input)
        while(true) {
            val entry=try { zip.nextEntry } catch(e:java.util.zip.ZipException) { throw unreadable() } ?: break
            if(!entry.isDirectory) visit(Piece(path(entry.name),entry.size),Shield(zip))
            zip.closeEntry()
        }
    }

    /** RAR 4: blocks of a 7-byte header (CRC, type, flags, size) plus, when flag 0x8000 is set, a data area. */
    private fun rar4(input:InputStream,visit:(Piece,InputStream)->Unit) {
        while(true) {
            val base=input.readUpTo(7)
            if(base.size<7) return
            val type=base[2].toInt() and 0xff;val flags=u16(base,3);val headSize=u16(base,5)
            if(headSize<7) throw unreadable()
            val head=base+input.readExactly(headSize-7)
            var data=if(flags and 0x8000!=0 && headSize>=11) u32(head,7) else 0L
            when(type) {
                0x73 -> if(flags and 0x0080!=0) throw locked()
                0x74 -> {
                    if(headSize<32) throw unreadable()
                    val large=flags and 0x0100!=0
                    data=u32(head,7)+if(large) u32(head,32) shl 32 else 0L
                    val nameAt=if(large) 40 else 32
                    val name=rar4Name(head.copyOfRange(nameAt,(nameAt+u16(head,26)).coerceAtMost(head.size)),flags and 0x0200!=0)
                    val folder=flags and 0x00e0==0x00e0
                    if(!folder) {
                        if(flags and 0x0004!=0) throw locked()
                        if(head[25].toInt() and 0xff!=0x30) throw compressed()
                        val part=Window(input,data)
                        visit(Piece(path(name),data,flags and 0x0001!=0),Shield(part))
                        part.drain();data=0
                    }
                }
                0x7b -> return
            }
            input.skipFully(data)
        }
    }

    /**
     * RAR 4 stores names outside ASCII as an ASCII name, a zero, then a compact Unicode form. Without the zero the
     * name is UTF-8. This follows DecodeFileName in RARLAB's unrar.
     */
    private fun rar4Name(bytes:ByteArray,unicode:Boolean):String {
        val zero=bytes.indexOf(0)
        if(!unicode || zero<0) return String(bytes,Charsets.UTF_8)
        val name=bytes.copyOf(zero);val enc=bytes.copyOfRange(zero+1,bytes.size)
        fun b(i:Int)=if(i<enc.size) enc[i].toInt() and 0xff else 0
        val out=StringBuilder();var pos=0
        val high=b(pos++);var flags=0;var bits=0
        while(pos<enc.size) {
            if(bits==0) { flags=b(pos++);bits=8 }
            when(flags shr 6 and 3) {
                0 -> out.append(b(pos++).toChar())
                1 -> out.append((b(pos++)+(high shl 8)).toChar())
                2 -> { out.append((b(pos)+(b(pos+1) shl 8)).toChar());pos+=2 }
                3 -> {
                    val length=b(pos++)
                    if(length and 0x80!=0) {
                        val correction=b(pos++)
                        repeat((length and 0x7f)+2) { out.append((((name.getOrElse(out.length) { 0 }.toInt() and 0xff)+correction and 0xff)+(high shl 8)).toChar()) }
                    } else repeat(length+2) { out.append((name.getOrElse(out.length) { 0 }.toInt() and 0xff).toChar()) }
                }
            }
            flags=flags shl 2 and 0xff;bits-=2
        }
        return out.toString()
    }

    /** RAR 5: headers of CRC32, a variable-length size, then fields in the same variable-length integers. */
    private fun rar5(input:InputStream,visit:(Piece,InputStream)->Unit) {
        while(true) {
            if(input.readUpTo(4).size<4) return
            val head=Fields(input.readExactly(vint(input).toIntChecked()))
            val type=head.vint();val flags=head.vint()
            val extra=if(flags and 0x0001L!=0L) head.vint() else 0L
            var data=if(flags and 0x0002L!=0L) head.vint() else 0L
            when(type) {
                4L -> throw locked()
                5L -> return
                2L -> {
                    val fileFlags=head.vint();head.vint();head.vint()
                    if(fileFlags and 0x0002L!=0L) head.skip(4)
                    if(fileFlags and 0x0004L!=0L) head.skip(4)
                    val method=head.vint();head.vint()
                    val name=String(head.bytes(head.vint().toIntChecked()),Charsets.UTF_8)
                    if(fileFlags and 0x0001L==0L) {
                        if(head.records(extra).any { it==1L }) throw locked()
                        if(method shr 7 and 7L!=0L) throw compressed()
                        val part=Window(input,data)
                        visit(Piece(path(name),data,flags and 0x0008L!=0L),Shield(part))
                        part.drain();data=0
                    }
                }
            }
            input.skipFully(data)
        }
    }

    /** The fields of one RAR 5 header. */
    private class Fields(private val bytes:ByteArray) {
        private var pos=0
        fun vint():Long {
            var value=0L;var shift=0
            while(true) {
                if(pos>=bytes.size || shift>63) throw unreadable()
                val b=bytes[pos++].toInt() and 0xff
                value=value or ((b and 0x7f).toLong() shl shift);shift+=7
                if(b and 0x80==0) return value
            }
        }
        fun skip(count:Int) { pos+=count }
        fun bytes(count:Int):ByteArray { val end=(pos+count).coerceAtMost(bytes.size);return bytes.copyOfRange(pos.coerceAtMost(end),end).also { pos=end } }
        /** The types of the records in the extra area, which fills the last [size] bytes of the header. */
        fun records(size:Long):List<Long> {
            pos=(bytes.size-size.toInt()).coerceIn(0,bytes.size)
            return buildList { while(pos<bytes.size) { val length=vint().toIntChecked();val start=pos;add(vint());pos=start+length } }
        }
    }

    private fun unreadable()=Unsupported("Sonder couldn't read this archive. Tap Retry to download it again.")
    private fun vint(input:InputStream):Long {
        var value=0L;var shift=0
        while(shift<=63) {
            val b=input.read();if(b<0) throw EOFException()
            value=value or ((b and 0x7f).toLong() shl shift);shift+=7
            if(b and 0x80==0) return value
        }
        throw unreadable()
    }
    private fun Long.toIntChecked():Int { if(this<0 || this>1_000_000) throw unreadable();return toInt() }
    private fun u16(b:ByteArray,at:Int)=(b[at].toInt() and 0xff) or (b[at+1].toInt() and 0xff shl 8)
    private fun u32(b:ByteArray,at:Int)=(u16(b,at).toLong()) or (u16(b,at+2).toLong() shl 16)
    /** Up to [count] bytes, fewer only where the stream ends. InputStream.readNBytes needs Android 13. */
    private fun InputStream.readUpTo(count:Int):ByteArray {
        val bytes=ByteArray(count);var filled=0
        while(filled<count) { val n=read(bytes,filled,count-filled);if(n<0) break;filled+=n }
        return if(filled==count) bytes else bytes.copyOf(filled)
    }
    private fun InputStream.readExactly(count:Int)=readUpTo(count).also { if(it.size<count) throw EOFException("The archive ended early.") }
    private fun InputStream.skipFully(count:Long) {
        var left=count
        while(left>0) {
            val skipped=skip(left)
            if(skipped>0) { left-=skipped;continue }
            if(read()<0) throw EOFException("The archive ended early.")
            left--
        }
    }
    private fun path(name:String)=name.replace('\\','/').split('/').filter { it.isNotBlank() && it!="." && it!=".." }.joinToString("/")

    /** The next [left] bytes of [input]. */
    private class Window(input:InputStream,private var left:Long):FilterInputStream(input) {
        override fun read():Int { if(left<=0) return -1;val b=`in`.read();if(b<0) throw EOFException("The archive ended early.");left--;return b }
        override fun read(b:ByteArray,off:Int,len:Int):Int {
            if(len==0) return 0
            if(left<=0) return -1
            val count=`in`.read(b,off,minOf(len.toLong(),left).toInt())
            if(count<0) throw EOFException("The archive ended early.")
            left-=count;return count
        }
        override fun skip(n:Long):Long { val skipped=`in`.skip(minOf(n,left));left-=skipped;return skipped }
        override fun available()=minOf(`in`.available().toLong(),left).toInt()
        override fun markSupported()=false
        override fun close() {}
        fun drain() { skipFully(left) }
    }
    /** Keeps [visit] from closing the archive itself. */
    private class Shield(input:InputStream):FilterInputStream(input) { override fun close() {} }
}
