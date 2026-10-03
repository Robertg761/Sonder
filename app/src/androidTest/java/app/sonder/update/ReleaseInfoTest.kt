package app.sonder.update

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

class ReleaseInfoTest {
    private fun release(version:String="1.4.0")=JSONObject().put("tag_name","v$version").put("draft",false).put("prerelease",false).put("body","Release notes").put("assets",JSONArray().put(JSONObject().put("name","Sonder-$version.apk").put("size",1234).put("digest","sha256:"+"a".repeat(64)).put("browser_download_url","https://github.com/Robertg761/Sonder/releases/download/v$version/Sonder-$version.apk")))
    @Test fun selectsStableNewerReleaseAndRestoresDownloadMetadata() {
        assertNull(ReleaseInfo.parse(release("1.3.0").toString(),"1.3.0"))
        assertNull(ReleaseInfo.parse(release("1.2.0").toString(),"1.3.0"))
        assertNull(ReleaseInfo.parse(release().put("prerelease",true).toString(),"1.3.0"))
        assertNull(ReleaseInfo.parse(release().put("draft",true).toString(),"1.3.0"))
        val info=ReleaseInfo.parse(release().toString(),"1.3.0")!!
        assertEquals("1.4.0",info.version);assertEquals(info,ReleaseInfo.stored(info.json()))
        assertTrue(ReleaseInfo.newer("1.10.0","1.9.9"));assertFalse(ReleaseInfo.newer("1.3.0-beta","1.2.0"))
        assertNull(ReleaseInfo.versionParts("1.3.99999999999999999999"))
    }
    @Test fun rejectsMissingChecksumsUnexpectedHostsAndInvalidSizes() {
        fun invalid(edit:(JSONObject)->Unit) {
            val r=release();edit(r.getJSONArray("assets").getJSONObject(0))
            assertThrows(Exception::class.java) { ReleaseInfo.parse(r.toString(),"1.3.0") }
        }
        invalid { it.remove("digest") }
        invalid { it.put("digest","sha256:not-a-hash") }
        invalid { it.put("browser_download_url","https://example.com/update.apk") }
        invalid { it.put("size",100_000_001) }
        invalid { it.put("size",0) }
        invalid { it.put("name","Sonder-debug.apk") }
    }
    @Test fun rejectsCorruptionAndAnAlreadyInstalledApk() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.applicationInfo.sourceDir)
        val hash=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val info=ReleaseInfo("1.4.0","https://github.com/Robertg761/Sonder/releases/download/v1.4.0/Sonder-1.4.0.apk",file.length(),hash,"")
        assertThrows(IllegalStateException::class.java) { AppUpdater.verify(context,file,info.copy(size=file.length()+1)) }
        assertThrows(IllegalStateException::class.java) { AppUpdater.verify(context,file,info.copy(sha256="a".repeat(64))) }
        assertThrows(IllegalStateException::class.java) { AppUpdater.verify(context,file,info) }
    }
    @Test fun updateProviderCannotShareLibraryOrArbitraryFiles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context,"${context.packageName}.updates",File(context.filesDir,"library.db")) }
        val file=File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),"sonder-updates/update.apk")
        assertEquals("content",FileProvider.getUriForFile(context,"${context.packageName}.updates",file).scheme)
    }

}
