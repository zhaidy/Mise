package com.personal.mise.timer

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personal.mise.MiseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 有计时器在跑时保持前台，显示倒计时通知；计时器都结束后自动停止。 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null

    private val timers get() = (application as MiseApp).timers

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(
            this,
            Notifications.RUNNING_ID,
            Notifications.buildRunning(this, timers.timers.value),
            type,
        )
        if (watchJob == null) {
            watchJob = scope.launch {
                launch {
                    timers.timers.collect { list ->
                        if (list.isEmpty()) {
                            ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            getSystemService(NotificationManager::class.java)
                                .notify(Notifications.RUNNING_ID, Notifications.buildRunning(this@TimerService, list))
                        }
                    }
                }
                // 闹钟之外的兜底检查
                while (isActive) {
                    timers.checkExpired()
                    delay(1000)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }
}
