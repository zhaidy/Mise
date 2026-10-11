package com.personal.mise.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.mise.MainActivity

/**
 * 新版本装好后系统会结束旧进程。是在应用里点的更新就自动重新打开；
 * 打不开（或是别的方式装的）就发个通知，点一下回到应用
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (UpdateChecker.consumeRelaunchRequest(context) && relaunch(context)) return
        val build = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        UpdateNotifications.show(context, "Mise 已更新到 build $build", "点这里打开")
    }

    /**
     * Android 10+ 不允许后台直接 startActivity，但闹钟（setAlarmClock）到点时可以打开界面，
     * 所以定一个马上就响的闹钟来打开 MainActivity
     */
    private fun relaunch(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return false
        val open = PendingIntent.getActivity(
            context, RELAUNCH_REQUEST,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 500, open), open)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val RELAUNCH_REQUEST = 20_001
    }
}
