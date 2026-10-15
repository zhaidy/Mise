package com.personal.mise.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.personal.mise.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

data class CookingTimer(
    val id: Int,
    val recipeId: Long,
    val recipeTitle: String,
    val stepIndex: Int,
    val totalSeconds: Int,
    /** SystemClock.elapsedRealtime() 时间点 */
    val endAtElapsed: Long,
    /** 到点后不响铃，自动跳到下一步，下一步有时间就接着计时；否则只响铃 */
    val autoNext: Boolean = false,
) {
    fun remainingSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        ((endAtElapsed - nowElapsed + 999) / 1000).coerceAtLeast(0)
}

/**
 * 多个计时器同时运行。每个计时器用 AlarmManager 精确闹钟兜底（App 被杀也能响），
 * 同时由前台服务显示倒计时通知。
 */
class TimerManager(private val context: Context, private val repository: RecipeRepository) {

    private val _timers = MutableStateFlow<List<CookingTimer>>(emptyList())
    val timers: StateFlow<List<CookingTimer>> = _timers.asStateFlow()

    private val _finished = MutableSharedFlow<TimerFinished>(extraBufferCapacity = 16)
    /** 要响铃的计时结束事件，App 在前台时用来弹窗；自动翻到下一步的不发 */
    val finished: SharedFlow<TimerFinished> = _finished.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val nextId = AtomicInteger((System.currentTimeMillis() / 1000 % 100_000).toInt())
    private val fired = mutableSetOf<Int>()

    fun start(recipeId: Long, recipeTitle: String, stepIndex: Int, seconds: Int, autoNext: Boolean = false): CookingTimer {
        val timer = CookingTimer(
            id = nextId.incrementAndGet(),
            recipeId = recipeId,
            recipeTitle = recipeTitle,
            stepIndex = stepIndex,
            totalSeconds = seconds,
            endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L,
            autoNext = autoNext,
        )
        _timers.update { it + timer }
        scheduleAlarm(timer)
        // 自动接着计时时可能在后台，系统不让起前台服务也没关系，闹钟照样会响
        try {
            TimerService.start(context)
        } catch (_: IllegalStateException) {
        }
        return timer
    }

    fun cancel(timerId: Int) {
        val timer = _timers.value.firstOrNull { it.id == timerId } ?: return
        alarmManager().cancel(alarmIntent(timer))
        _timers.update { list -> list.filterNot { it.id == timerId } }
    }

    /**
     * 闹钟或前台服务发现计时结束时调用；同一个计时器只提醒一次。
     * 自动下一步的计时要查数据库，返回那个任务，闹钟广播等它做完再结束。
     */
    @Synchronized
    fun onFinished(timerId: Int, recipeId: Long, recipeTitle: String, stepIndex: Int, autoNext: Boolean): Job? {
        if (!fired.add(timerId)) return null
        val timer = _timers.value.firstOrNull { it.id == timerId }
            ?: CookingTimer(timerId, recipeId, recipeTitle, stepIndex, 0, 0, autoNext)
        alarmManager().cancel(alarmIntent(timer))
        if (!timer.autoNext) {
            finish(timer, null)
            return null
        }
        return scope.launch {
            // 先把下一步的计时排上再撤掉这个，计时列表不会空一下、把前台服务停掉
            val next = runCatching { advance(timer) }.getOrNull()
            finish(timer, next)
        }
    }

    private fun finish(timer: CookingTimer, next: NextStep?) {
        _timers.update { list -> list.filterNot { it.id == timer.id } }
        val event = TimerFinished(timer, next)
        // 翻成了就悄悄翻过去；没翻成（最后一步、人已经翻到别处）照常响铃，免得错过时间
        if (event.silent) return
        Notifications.showFinished(context, event)
        _finished.tryEmit(event)
    }

    /** 翻到下一步，下一步有时间就接着计时；人已经翻到别的步骤了就不动 */
    private suspend fun advance(timer: CookingTimer): NextStep? {
        val steps = repository.loadFull(timer.recipeId)?.steps ?: return null
        val next = AutoNext.after(steps, timer.stepIndex) ?: return null
        val current = repository.currentStepOf(timer.recipeId)
        if (current != null && current != timer.stepIndex && current != next.index) return null
        repository.setCurrentStep(timer.recipeId, next.index)
        val alreadyRunning = _timers.value.any { it.recipeId == timer.recipeId && it.stepIndex == next.index }
        if (next.seconds != null && !alreadyRunning) {
            start(timer.recipeId, timer.recipeTitle, next.index, next.seconds, autoNext = true)
        }
        return next
    }

    fun checkExpired() {
        val now = SystemClock.elapsedRealtime()
        _timers.value.filter { it.endAtElapsed <= now }.forEach {
            onFinished(it.id, it.recipeId, it.recipeTitle, it.stepIndex, it.autoNext)
        }
    }

    private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(timer: CookingTimer): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timer.id)
            .putExtra(TimerAlarmReceiver.EXTRA_RECIPE_ID, timer.recipeId)
            .putExtra(TimerAlarmReceiver.EXTRA_TITLE, timer.recipeTitle)
            .putExtra(TimerAlarmReceiver.EXTRA_STEP, timer.stepIndex)
            .putExtra(TimerAlarmReceiver.EXTRA_AUTO_NEXT, timer.autoNext)
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
