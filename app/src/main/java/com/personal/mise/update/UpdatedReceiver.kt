package com.personal.mise.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 新版本装好后系统会结束旧进程，发个通知，点一下就能回到应用 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val build = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        UpdateNotifications.show(context, "Mise 已更新到 build $build", "点这里打开")
    }
}
