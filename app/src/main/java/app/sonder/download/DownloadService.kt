package app.sonder.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.sonder.MainActivity
import app.sonder.R
import app.sonder.SonderApp
import kotlinx.coroutines.*

/** Keeps Sonder running while audiobooks download, with a progress notification. */
class DownloadService:Service() {
    private companion object { const val CHANNEL="downloads";const val PROGRESS=41;const val CANCEL="app.sonder.download.CANCEL" }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val downloads get()=(application as SonderApp).downloads
    private val manager get()=getSystemService(NotificationManager::class.java)
    private var worker:Job?=null
    private val seen=mutableMapOf<String,DownloadJob.State>()
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Downloads",NotificationManager.IMPORTANCE_LOW).apply { description="Audiobook download progress" })
        downloads.jobs.value.forEach { seen[it.id]=it.state }
        // StateFlow keeps only the latest list, so the delay throttles updates without missing the final state.
        scope.launch { downloads.jobs.collect { render(it);delay(800) } }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // The progress notification's Cancel button. The service still starts in the foreground as Android requires, then stops if nothing is left.
        if(intent?.action==CANCEL) intent.getStringExtra("id")?.let(downloads::remove)
        ServiceCompat.startForeground(this,PROGRESS,progress(downloads.jobs.value),if(Build.VERSION.SDK_INT>=29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        launchWorker()
        return START_NOT_STICKY
    }
    private fun launchWorker() {
        if(worker?.isActive==true) return
        worker=scope.launch {
            withContext(Dispatchers.IO) { downloads.work() }
            // Back on the main thread, where onStartCommand runs, so a job queued meanwhile isn't missed.
            worker=null
            if(downloads.pending()) launchWorker() else { render(downloads.jobs.value);ServiceCompat.stopForeground(this@DownloadService,ServiceCompat.STOP_FOREGROUND_REMOVE);stopSelf() }
        }
    }
    // Android 15 limits data-sync services to six hours a day. The running job stops and can be retried.
    override fun onTimeout(startId:Int,fgsType:Int) { worker?.cancel();ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy() { scope.cancel();super.onDestroy() }

    private fun render(jobs:List<DownloadJob>) {
        if(worker?.isActive==true) notify(PROGRESS,progress(jobs))
        jobs.forEach { job ->
            // Updates are sampled, so a quick job can go from queued (or unseen) straight to its result.
            val before=seen.put(job.id,job.state)
            if(job.active || before==job.state || before==DownloadJob.State.DONE || before==DownloadJob.State.FAILED) return@forEach
            notify(job.id.hashCode(),finished(job.title,if(job.state==DownloadJob.State.DONE) "Added to your library" else job.message))
        }
    }
    // MainActivity carries Media3's UnstableApi marker; opening it from a notification uses no Media3 API.
    @androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
    private fun open()=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun cancel(job:DownloadJob)=PendingIntent.getForegroundService(this,job.id.hashCode(),Intent(this,DownloadService::class.java).setAction(CANCEL).putExtra("id",job.id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun progress(jobs:List<DownloadJob>):android.app.Notification {
        val job=jobs.firstOrNull { it.state==DownloadJob.State.WORKING } ?: jobs.lastOrNull { it.state==DownloadJob.State.QUEUED }
        val waiting=jobs.count { it.state==DownloadJob.State.QUEUED }-if(job?.state==DownloadJob.State.QUEUED) 1 else 0
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_sonder).setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentTitle(job?.title ?: "Preparing downloads").setContentText(job?.message.orEmpty()+if(waiting>0) " · $waiting more waiting" else "")
            .setProgress(100,((job?.progress ?: 0f)*100).toInt().coerceIn(0,100),job==null || job.progress<0)
            .apply { if(job!=null) addAction(R.drawable.ic_stat_sonder,"Cancel",cancel(job)) }.build()
    }
    private fun finished(title:String,text:String)=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_sonder).setContentIntent(open()).setAutoCancel(true)
        .setContentTitle(title).setContentText(text).build()
    private fun notify(id:Int,notification:android.app.Notification) {
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) manager.notify(id,notification)
    }
}
