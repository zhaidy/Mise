package com.personal.mise.timer

import com.personal.mise.data.Step
import com.personal.mise.util.TimeText

/** 「自动下一步」的计时到点后跳到的那一步；[seconds] 不为空时接着计这么久 */
data class NextStep(val index: Int, val seconds: Int?)

object AutoNext {
    /** 第 [stepIndex] 步到点后该跳到哪一步；已经是最后一步就不跳 */
    fun after(steps: List<Step>, stepIndex: Int): NextStep? {
        val index = stepIndex + 1
        val step = steps.getOrNull(index) ?: return null
        return NextStep(index, step.timerSeconds ?: TimeText.guessSeconds(step.text))
    }

    /** 计时结束时弹窗和通知里的那句话 */
    fun message(timer: CookingTimer, next: NextStep?): String {
        val done = "第 ${timer.stepIndex + 1} 步的时间到了"
        if (next == null) return done
        val jumped = "$done，已跳到第 ${next.index + 1} 步"
        return next.seconds?.let { "$jumped，接着计时 ${TimeText.describe(it)}" } ?: jumped
    }
}

/** 计时结束事件；[next] 不为空说明已经自动跳到下一步 */
data class TimerFinished(val timer: CookingTimer, val next: NextStep?) {
    val message: String get() = AutoNext.message(timer, next)
}
