package app.sonder.download

import app.sonder.data.readBounded
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small HTTPS client for page and API requests. Bodies are bounded; large media goes through [Downloads]. */
internal object Http {
    // AudioBookBay serves a reduced page to unknown agents, so requests look like a mobile browser.
    const val AGENT="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    data class Response(val code:Int,val body:String)
    fun encode(value:String):String=URLEncoder.encode(value,"UTF-8")
    fun open(url:String,method:String="GET",headers:Map<String,String> = emptyMap()):HttpURLConnection {
        val target=URL(url)
        if(target.protocol!="https") throw IOException("Sonder only connects over HTTPS.")
        return (target.openConnection() as HttpURLConnection).apply {
            requestMethod=method;connectTimeout=20000;readTimeout=30000;instanceFollowRedirects=true
            setRequestProperty("User-Agent",AGENT)
            headers.forEach { (k,v) -> setRequestProperty(k,v) }
        }
    }
    fun request(url:String,method:String="GET",form:Map<String,String>?=null,headers:Map<String,String> = emptyMap(),limit:Int=4_000_000):Response {
        val connection=open(url,method,headers)
        try {
            if(form!=null) {
                val body=form.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.toByteArray()
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code=connection.responseCode
            val stream=if(code>=400) connection.errorStream else connection.inputStream
            val bytes=stream?.use { it.readBounded(limit+1) } ?: ByteArray(0)
            if(bytes.size>limit) throw IOException("The response was too large.")
            return Response(code,bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
