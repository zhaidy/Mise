package com.personal.mise.timer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.mise.MainActivity
import com.personal.mise.R

object Notifications {
    const val CHANNEL_RUNNING = "timer_running"
    const val CHANNEL_DONE = "timer_done_alarm"
    const val RUNNING_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "计时进行中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "做菜计时器的倒计时"
                setShowBadge(false)
            }
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "计时结束", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "计时结束时响铃提醒"
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            }
        )
    }

    fun openRecipeIntent(context: Context, recipeId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_COOKING)
            .putExtra(MainActivity.EXTRA_RECIPE_ID, recipeId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildRunning(context: Context, timers: List<CookingTimer>): android.app.Notification {
        val nearest = timers.minByOrNull { it.endAtElapsed }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentTitle(if (timers.size <= 1) "计时中" else "${timers.size} 个计时器进行中")
        if (nearest != null) {
            val wallEnd = System.currentTimeMillis() + (nearest.endAtElapsed - SystemClock.elapsedRealtime())
            builder.setContentText("${nearest.recipeTitle} · 第 ${nearest.stepIndex + 1} 步")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(wallEnd)
                .setShowWhen(true)
                .setContentIntent(openRecipeIntent(context, nearest.recipeId, 0))
            if (timers.size > 1) {
                val style = NotificationCompat.InboxStyle()
                timers.sortedBy { it.endAtElapsed }.forEach {
                    style.addLine("${it.recipeTitle} · 第 ${it.stepIndex + 1} 步${if (it.autoNext) " · 到点自动下一步" else ""}")
                }
                builder.setStyle(style)
            }
        }
        return builder.build()
    }

    fun showFinished(context: Context, event: TimerFinished) {
        val timer = event.timer
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        if (!granted) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("计时结束：${timer.recipeTitle}")
            .setContentText(event.message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openRecipeIntent(context, timer.recipeId, 10_000 + timer.id))
            .build()
        NotificationManagerCompat.from(context).notify(10_000 + timer.id, notification)
    }
}
