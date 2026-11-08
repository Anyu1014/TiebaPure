package dev.anyu.tiebapure

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*

class AutoReplyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { AutoReplyRuntime.get(this).let { runtime ->
            LocalStore(this).loadAccount()?.let { runtime.configureBackground(it.uid, false) }
        }; stopSelf(); return START_NOT_STICKY }
        val owner = runCatching { LocalStore(this).loadAccount() }.getOrNull()
        val runtime = AutoReplyRuntime.get(this)
        if (owner == null || !runtime.enabled(owner.uid) || !runtime.repository.background(owner.uid)) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("auto-reply", "自动回复监测", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("open-automation", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AutoReplyService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "auto-reply").setSmallIcon(R.drawable.ic_automation_notification)
            .setContentTitle("贴吧自动回复监测中").setContentText("每60秒检查已配置贴吧，点击查看任务").setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止保活", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(402, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(402, notification)
        AutoReplyRuntime.get(this).serviceChanged(true)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:automation").apply { setReferenceCounted(false) }
            scope.launch { while (isActive) { wakeLock?.let { if (it.isHeld) it.release(); it.acquire(120000) }; delay(60000) } }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        AutoReplyRuntime.get(this).message("系统已达到后台运行时限，保活暂停；下次打开将补扫并询问")
        stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        scope.cancel(); wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        AutoReplyRuntime.get(this).serviceChanged(false)
        super.onDestroy()
    }
}
