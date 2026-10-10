package com.personal.mise.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

data class CookingTimer(
    val id: Int,
    val recipeId: Long,
    val recipeTitle: String,
    val stepIndex: Int,
    val totalSeconds: Int,
    /** SystemClock.elapsedRealtime() 时间点 */
    val endAtElapsed: Long,
) {
    fun remainingSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        ((endAtElapsed - nowElapsed + 999) / 1000).coerceAtLeast(0)
}

/**
 * 多个计时器同时运行。每个计时器用 AlarmManager 精确闹钟兜底（App 被杀也能响），
 * 同时由前台服务显示倒计时通知。
 */
class TimerManager(private val context: Context) {

    private val _timers = MutableStateFlow<List<CookingTimer>>(emptyList())
    val timers: StateFlow<List<CookingTimer>> = _timers.asStateFlow()

    private val _finished = MutableSharedFlow<CookingTimer>(extraBufferCapacity = 16)
    /** 计时结束事件，App 在前台时用来弹窗 */
    val finished: SharedFlow<CookingTimer> = _finished.asSharedFlow()

    private val nextId = AtomicInteger((System.currentTimeMillis() / 1000 % 100_000).toInt())
    private val fired = mutableSetOf<Int>()

    fun start(recipeId: Long, recipeTitle: String, stepIndex: Int, seconds: Int): CookingTimer {
        val timer = CookingTimer(
            id = nextId.incrementAndGet(),
            recipeId = recipeId,
            recipeTitle = recipeTitle,
            stepIndex = stepIndex,
            totalSeconds = seconds,
            endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L,
        )
        _timers.update { it + timer }
        scheduleAlarm(timer)
        TimerService.start(context)
        return timer
    }

    fun cancel(timerId: Int) {
        val timer = _timers.value.firstOrNull { it.id == timerId } ?: return
        alarmManager().cancel(alarmIntent(timer))
        _timers.update { list -> list.filterNot { it.id == timerId } }
    }

    /** 闹钟或前台服务发现计时结束时调用；同一个计时器只提醒一次 */
    @Synchronized
    fun onFinished(timerId: Int, recipeId: Long, recipeTitle: String, stepIndex: Int) {
        if (!fired.add(timerId)) return
        val timer = _timers.value.firstOrNull { it.id == timerId }
            ?: CookingTimer(timerId, recipeId, recipeTitle, stepIndex, 0, 0)
        _timers.update { list -> list.filterNot { it.id == timerId } }
        alarmManager().cancel(alarmIntent(timer))
        Notifications.showFinished(context, timer)
        _finished.tryEmit(timer)
    }

    fun checkExpired() {
        val now = SystemClock.elapsedRealtime()
        _timers.value.filter { it.endAtElapsed <= now }.forEach {
            onFinished(it.id, it.recipeId, it.recipeTitle, it.stepIndex)
        }
    }

    private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(timer: CookingTimer): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timer.id)
            .putExtra(TimerAlarmReceiver.EXTRA_RECIPE_ID, timer.recipeId)
            .putExtra(TimerAlarmReceiver.EXTRA_TITLE, timer.recipeTitle)
            .putExtra(TimerAlarmReceiver.EXTRA_STEP, timer.stepIndex)
        return PendingIntent.getBroadcast(
            context,
            timer.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAlarm(timer: CookingTimer) {
        val am = alarmManager()
        val pending = alarmIntent(timer)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.endAtElapsed, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.endAtElapsed, pending)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.endAtElapsed, pending)
        }
    }
}
