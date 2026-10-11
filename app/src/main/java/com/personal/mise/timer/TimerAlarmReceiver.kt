package com.personal.mise.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.mise.MiseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MiseApp
        val job = app.timers.onFinished(
            timerId = intent.getIntExtra(EXTRA_TIMER_ID, 0),
            recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, 0),
            recipeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            stepIndex = intent.getIntExtra(EXTRA_STEP, 0),
            autoNext = intent.getBooleanExtra(EXTRA_AUTO_NEXT, false),
        ) ?: return
        // 自动下一步要查数据库、排下一个计时，做完前别让系统把进程收掉
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                job.join()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STEP = "step"
        const val EXTRA_AUTO_NEXT = "auto_next"
    }
}
