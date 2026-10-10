package com.personal.mise.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.mise.MiseApp

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MiseApp
        app.timers.onFinished(
            timerId = intent.getIntExtra(EXTRA_TIMER_ID, 0),
            recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, 0),
            recipeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            stepIndex = intent.getIntExtra(EXTRA_STEP, 0),
        )
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STEP = "step"
    }
}
