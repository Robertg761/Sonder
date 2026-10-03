package app.sonder.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded streaming read compatible with Android 9; reads no more than [limit] bytes. */
fun InputStream.readBounded(limit:Int):ByteArray {
    require(limit>=0)
    val output=ByteArrayOutputStream(minOf(limit,8192));val buffer=ByteArray(8192)
    var remaining=limit
    while(remaining>0) {
        val read=read(buffer,0,minOf(buffer.size,remaining))
        if(read<0) break
        if(read==0) { val next=read();if(next<0) break;output.write(next);remaining-- }
        else { output.write(buffer,0,read);remaining-=read }
    }
    return output.toByteArray()
}
