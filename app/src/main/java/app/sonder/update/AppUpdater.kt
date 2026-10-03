package app.sonder.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import app.sonder.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateState(val checking:Boolean=false,val available:ReleaseInfo?=null,val phase:String="",val progress:Float=0f,val message:String="",val automatic:Boolean=true)

class AppUpdater(private val context:Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val prefs=context.getSharedPreferences("updates",Context.MODE_PRIVATE)
    private val dm=context.getSystemService(DownloadManager::class.java)
    private val mutable=MutableStateFlow(UpdateState(automatic=prefs.getBoolean("automatic",true)))
    val state=mutable.asStateFlow()
    private var checkJob:Job?=null
    private var monitorJob:Job?=null
    private fun file()=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"sonder-updates/update.apk")
    fun automatic(enabled:Boolean) { prefs.edit().putBoolean("automatic",enabled).apply();mutable.value=mutable.value.copy(automatic=enabled) }
    @Synchronized fun foreground() {
        if(BuildConfig.DEBUG) return
        if(prefs.contains("download")) { resumeDownload();return }
        if(mutable.value.automatic && System.currentTimeMillis()-prefs.getLong("checked",0)>24*60*60*1000L) check(false)
    }
    @Synchronized fun check(manual:Boolean=true) {
        if(checkJob?.isActive==true || mutable.value.phase in listOf("downloading","verifying","ready")) return
        if(BuildConfig.DEBUG) { mutable.value=mutable.value.copy(message="GitHub updates are available in signed release builds.");return }
        checkJob=scope.launch {
            mutable.value=mutable.value.copy(checking=true,message="")
            try {
                val connection=URL(ReleaseInfo.API).openConnection() as HttpURLConnection
                val info=try {
                    connection.connectTimeout=15000;connection.readTimeout=15000
                    connection.setRequestProperty("Accept","application/vnd.github+json")
                    connection.setRequestProperty("User-Agent","Sonder/${BuildConfig.VERSION_NAME}")
                    val code=connection.responseCode
                    if(code==404) null else {
                        check(code==200) { if(code==403 || code==429) "GitHub is limiting update checks. Try again later." else "Could not check for updates. Try again later." }
                        val bytes=connection.inputStream.use { input ->
                            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                            while(true) { val count=input.read(buffer);if(count<0) break;check(output.size()+count<=1_000_000) { "Release response is too large." };output.write(buffer,0,count) }
                            output.toByteArray()
                        }
                        check(bytes.size<=1_000_000) { "Release response is too large." }
                        ReleaseInfo.parse(bytes.toString(Charsets.UTF_8),BuildConfig.VERSION_NAME)
                    }
                } finally { connection.disconnect() }
                prefs.edit().putLong("checked",System.currentTimeMillis()).apply()
                mutable.value=mutable.value.copy(available=info,message=if(manual && info==null) "You're up to date." else "")
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.value=mutable.value.copy(message=if(manual) e.message ?: "Could not check for updates." else "");if(!manual) prefs.edit().putLong("checked",System.currentTimeMillis()-23*60*60*1000L).apply() }
            finally { mutable.value=mutable.value.copy(checking=false) }
        }
    }
    @Synchronized fun download() {
        if(BuildConfig.DEBUG || monitorJob?.isActive==true || mutable.value.phase in listOf("ready","downloading","verifying")) return
        val info=mutable.value.available ?: return
        mutable.value=mutable.value.copy(phase="downloading",message="",progress=0f)
        scope.launch {
            try {
                ReleaseInfo.validate(info);clearDownload();file().parentFile?.mkdirs()
                val request=DownloadManager.Request(Uri.parse(info.url)).setTitle("Sonder ${info.version}").setDescription("Audiobook app update")
                    .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverRoaming(false).setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"sonder-updates/update.apk")
                val id=dm.enqueue(request)
                prefs.edit().putLong("download",id).putString("release",info.json()).apply()
                resumeDownload()
            } catch(e:Exception) { mutable.value=mutable.value.copy(phase="error",message="Could not start the download. Check storage and try again.") }
        }
    }
    @Synchronized private fun resumeDownload() {
        if(monitorJob?.isActive==true) return
        monitorJob=scope.launch {
            try {
                val info=ReleaseInfo.stored(prefs.getString("release",null) ?: error("Missing release information"))
                if(!ReleaseInfo.newer(info.version,BuildConfig.VERSION_NAME)) { clearDownload();mutable.value=mutable.value.copy(available=null,phase="");return@launch }
                mutable.value=mutable.value.copy(available=info,phase="downloading",message="")
                while(isActive) {
                    val status=dm.query(DownloadManager.Query().setFilterById(prefs.getLong("download",-1))).use { c ->
                        check(c.moveToFirst()) { "Download was removed. Tap retry." }
                        val bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        mutable.value=mutable.value.copy(progress=(bytes.toFloat()/info.size).coerceIn(0f,1f))
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    }
                    if(status==DownloadManager.STATUS_SUCCESSFUL) {
                        mutable.value=mutable.value.copy(phase="verifying")
                        verify(context,file(),info)
                        ensureActive()
                        mutable.value=mutable.value.copy(phase="ready",progress=1f);break
                    }
                    check(status!=DownloadManager.STATUS_FAILED) { "Download failed. Check your connection and tap retry." }
                    delay(750)
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { clearDownload();mutable.value=mutable.value.copy(phase="error",message=e.message ?: "Could not verify this update.") }
        }
    }
    fun cancel() { monitorJob?.cancel();monitorJob=null;clearDownload();mutable.value=mutable.value.copy(phase="",progress=0f,message="") }
    private fun clearDownload() { val id=prefs.getLong("download",-1);if(id>0) runCatching { dm.remove(id) };file().delete();prefs.edit().remove("download").remove("release").apply() }
    suspend fun installIntent():Intent=withContext(Dispatchers.IO) {
        val info=mutable.value.available ?: error("Check for updates first.")
        check(mutable.value.phase=="ready") { "Download the update first." }
        verify(context,file(),info)
        if(!context.packageManager.canRequestPackageInstalls()) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))
        else Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context,"${context.packageName}.updates",file()),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    companion object {
        fun verify(context:Context,file:File,info:ReleaseInfo) {
            ReleaseInfo.validate(info)
            check(file.length()==info.size) { "Update size doesn't match the release." }
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer=ByteArray(65536);while(true) { val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) } }
            check(digest.digest().joinToString("") { "%02x".format(it) }==info.sha256) { "Update checksum doesn't match. Download it again." }
            val pm=context.packageManager
            val archive=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES) ?: error("This update is not a valid APK.")
            val installed=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
            check(archive.packageName==context.packageName && archive.longVersionCode>installed.longVersionCode && archive.versionName==info.version) { "This APK is not a newer Sonder release." }
            val expected=installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val actual=archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            check(!expected.isNullOrEmpty() && expected==actual) { "This update has a different signing certificate." }
        }
    }
}
