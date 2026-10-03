package app.sonder.update

import org.json.JSONObject

data class ReleaseInfo(val version:String,val url:String,val size:Long,val sha256:String,val notes:String) {
    fun json()=JSONObject().put("version",version).put("url",url).put("size",size).put("sha256",sha256).put("notes",notes).toString()
    companion object {
        const val REPO="Robertg761/Sonder"
        const val API="https://api.github.com/repos/$REPO/releases/latest"
        fun versionParts(value:String):List<Int>? {
            if(!Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+").matches(value)) return null
            return value.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
        }
        fun newer(candidate:String,current:String):Boolean {
            val a=versionParts(candidate) ?: return false;val b=versionParts(current) ?: return false
            for(i in 0..2) { if(a[i]!=b[i]) return a[i]>b[i] };return false
        }
        fun stored(text:String):ReleaseInfo {
            val o=JSONObject(text)
            return ReleaseInfo(o.getString("version"),o.getString("url"),o.getLong("size"),o.getString("sha256"),o.optString("notes")).also { validate(it) }
        }
        fun validate(info:ReleaseInfo) {
            require(versionParts(info.version)!=null) { "Invalid release version" }
            require(info.url=="https://github.com/$REPO/releases/download/v${info.version}/Sonder-${info.version}.apk") { "Invalid release URL" }
            require(info.size in 1..100_000_000) { "Invalid APK size" }
            require(Regex("[a-f0-9]{64}").matches(info.sha256)) { "Release has no valid checksum" }
        }
        fun parse(text:String,current:String):ReleaseInfo? {
            val o=JSONObject(text)
            if(o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val tag=o.getString("tag_name")
            if(!newer(tag,current)) return null
            val version=tag.removePrefix("v")
            require(tag=="v$version") { "Invalid release tag" }
            val assets=o.getJSONArray("assets")
            val apk=(0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name")=="Sonder-$version.apk" } ?: error("Release APK unavailable")
            val digest=apk.getString("digest")
            require(digest.startsWith("sha256:")) { "Release checksum unavailable" }
            return ReleaseInfo(version,apk.getString("browser_download_url"),apk.getLong("size"),digest.removePrefix("sha256:"),o.optString("body").take(12000)).also { validate(it) }
        }
    }
}
