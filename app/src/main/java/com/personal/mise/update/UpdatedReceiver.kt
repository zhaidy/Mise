package com.personal.mise.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.mise.MainActivity

/**
 * 新版本装好后系统会结束旧进程。是在应用里点的更新就试着自动重新打开；
 * 不管打没打开都发个通知，点一下回到应用（应用真被打开时 MainActivity 会撤掉它）
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (UpdateChecker.consumeRelaunchRequest(context)) relaunch(context)
        // 新系统（以及小米等 ROM）多半会拦住后台打开界面，而且拦了也不报错，
        // 所以没法根据 relaunch 的结果决定要不要发通知，一律发
        val build = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        UpdateNotifications.show(context, "Mise 已更新到 build $build", "点这里打开", UpdateNotifications.UPDATED_ID)
    }

    /**
     * Android 10+ 不允许后台直接 startActivity，定一个马上就响的闹钟（setAlarmClock）去打开，
     * 部分系统上管用，尽力而为。不用“显示在其他应用上层”权限：它和“安装应用”放一起
     * 像恶意软件，会让 Play 保护机制更不信任 Mise
     */
    private fun relaunch(context: Context) {
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        val open = PendingIntent.getActivity(
            context, RELAUNCH_REQUEST,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 500, open), open)
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val RELAUNCH_REQUEST = 20_001
    }
}
