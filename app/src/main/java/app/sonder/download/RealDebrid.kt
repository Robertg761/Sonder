package app.sonder.download

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Real-Debrid REST API client. Uses a personal API token from real-debrid.com/apitoken. */
class RealDebrid(private val token:String) {
    data class User(val name:String,val premium:Boolean,val expiration:String)
    data class File(val id:Int,val path:String,val bytes:Long,val selected:Boolean=false)
    data class Torrent(val id:String,val status:String,val progress:Int,val seeders:Int,val speed:Long,val bytes:Long,val files:List<File>,val links:List<String>)
    data class Link(val name:String,val size:Long,val url:String)
    class Error(message:String,val status:Int,val code:Int):IOException(message)
    companion object {
        const val API="https://api.real-debrid.com/rest/1.0"
        /** Torrent states that will never finish without a new magnet. */
        val failed=setOf("magnet_error","error","virus","dead")
        fun describe(status:String)=when(status) {
            "magnet_conversion" -> "Real-Debrid is reading the magnet"
            "waiting_files_selection" -> "Choosing files"
            "queued" -> "Queued at Real-Debrid"
            "downloading" -> "Real-Debrid is downloading"
            "compressing","uploading" -> "Real-Debrid is preparing the files"
            "downloaded" -> "Ready on Real-Debrid"
            "magnet_error" -> "Real-Debrid couldn't read this magnet link."
            "virus" -> "Real-Debrid flagged this upload as a virus."
            "dead" -> "This upload has no seeders, so Real-Debrid can't fetch it."
            else -> "Real-Debrid couldn't download this upload."
        }
    }

    fun user():User = json(call("GET","/user")).let { User(it.optString("username"),it.optString("type")=="premium",it.optString("expiration")) }
    fun addMagnet(magnet:String):String = json(call("POST","/torrents/addMagnet",mapOf("magnet" to magnet))).getString("id")
    fun torrent(id:String):Torrent = json(call("GET","/torrents/info/${Http.encode(id)}")).let { t ->
        val files=t.optJSONArray("files") ?: JSONArray();val links=t.optJSONArray("links") ?: JSONArray()
        Torrent(t.getString("id"),t.optString("status"),t.optInt("progress"),t.optInt("seeders"),t.optLong("speed"),t.optLong("bytes"),
            (0 until files.length()).map { i -> files.getJSONObject(i).let { File(it.getInt("id"),it.optString("path"),it.optLong("bytes"),it.optInt("selected")==1) } },
            (0 until links.length()).map { links.getString(it) })
    }
    fun select(id:String,files:List<Int>) { call("POST","/torrents/selectFiles/${Http.encode(id)}",mapOf("files" to files.joinToString(","))) }
    fun unrestrict(link:String):Link = json(call("POST","/unrestrict/link",mapOf("link" to link))).let { Link(it.optString("filename"),it.optLong("filesize"),it.getString("download")) }

    private fun json(body:String)=try { JSONObject(body) } catch(e:Exception) { throw IOException("Real-Debrid sent an unexpected response.") }
    private fun call(method:String,path:String,form:Map<String,String>?=null):String {
        val response=try { Http.request(API+path,method,form,mapOf("Authorization" to "Bearer $token","Accept" to "application/json"),1_000_000) }
            catch(e:IOException) { throw IOException("Couldn't reach Real-Debrid. Check your connection.",e) }
        if(response.code in 200..299) return response.body
        val code=runCatching { JSONObject(response.body).optInt("error_code",0) }.getOrDefault(0)
        throw Error(when {
            response.code==401 || code==8 -> "Real-Debrid didn't accept your API token. Paste it again in Settings."
            code==14 -> "Your Real-Debrid account is locked."
            code==20 || code==9 || response.code==403 -> "Real-Debrid refused this request. Torrents need a premium account."
            code==21 -> "Real-Debrid has too many active downloads on your account. Try again later."
            code==23 || code==36 -> "Your Real-Debrid traffic limit has been reached."
            code==29 -> "This upload is too large for Real-Debrid."
            code==35 || response.code==451 -> "Real-Debrid won't download this upload."
            code==5 || code==34 || response.code==429 -> "Real-Debrid is limiting requests. Try again in a minute."
            response.code==404 -> "Real-Debrid no longer has this torrent. Remove the download and add it again."
            response.code>=500 || code==25 -> "Real-Debrid is temporarily unavailable. Try again later."
            else -> "Real-Debrid returned an error (${response.code})."
        },response.code,code)
    }
}
