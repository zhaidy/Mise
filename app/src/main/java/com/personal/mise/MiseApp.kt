package com.personal.mise

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import com.personal.mise.ai.AiAmountEstimator
import com.personal.mise.ai.AiBowlOrganizer
import com.personal.mise.ai.AiCalorieEstimator
import com.personal.mise.ai.AiClient
import com.personal.mise.ai.AiHttp
import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.ai.AiSettings
import com.personal.mise.ai.AiStepSplitter
import com.personal.mise.ai.CellularNetwork
import com.personal.mise.ai.ClaudeClient
import com.personal.mise.ai.DeepSeekClient
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.SwitchableAiClient
import com.personal.mise.backup.BackupManager
import com.personal.mise.data.CalorieBudget
import com.personal.mise.data.MiseDatabase
import com.personal.mise.data.Pantry
import com.personal.mise.data.RecipeRepository
import com.personal.mise.image.ImageStore
import com.personal.mise.sync.KitchenSync
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.importer.WebPageLoader
import com.personal.mise.timer.Notifications
import com.personal.mise.timer.TimerManager
import com.personal.mise.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MiseApp : Application() {
    lateinit var database: MiseDatabase
        private set
    lateinit var images: ImageStore
        private set
    lateinit var repository: RecipeRepository
        private set
    lateinit var timers: TimerManager
        private set
    lateinit var kitchen: KitchenSync
        private set
    /** 跟着进程走的后台任务，比如同步 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val importer: RecipeImporter by lazy { RecipeImporter(images, noteOrganizer, WebPageLoader(this)) }
    val aiSettings: AiSettings by lazy { AiSettings(this) }
    val updater: UpdateChecker by lazy { UpdateChecker(this) }
    val backup: BackupManager by lazy { BackupManager(this, database) }
    val pantry: Pantry by lazy { Pantry(this) }
    val calorieBudget: CalorieBudget by lazy { CalorieBudget(this) }
    private val ai: AiClient by lazy {
        val cellular = CellularNetwork(this)
        val onCellularUsed = {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Wi-Fi 连不上，已改用移动数据", Toast.LENGTH_SHORT).show()
            }
            Unit
        }
        SwitchableAiClient(
            aiSettings,
            GeminiClient(aiSettings, AiHttp(aiSettings, cellular, readTimeoutSeconds = 90, onCellularUsed = onCellularUsed)),
            ClaudeClient(aiSettings, AiHttp(aiSettings, cellular, readTimeoutSeconds = 120, onCellularUsed = onCellularUsed)),
            DeepSeekClient(aiSettings, AiHttp(aiSettings, cellular, readTimeoutSeconds = 120, onCellularUsed = onCellularUsed)),
            onModelUsed = ::showModelUsed,
        )
    }
    private var lastModelNotice = ""
    private var lastModelNoticeAt = 0L

    /** 提示这次 AI 用的是哪个模型；一次估算好几道菜时同样的提示几秒内只弹一次 */
    private fun showModelUsed(message: String) {
        Handler(Looper.getMainLooper()).post {
            val now = SystemClock.elapsedRealtime()
            if (message == lastModelNotice && now - lastModelNoticeAt < 5_000) return@post
            lastModelNotice = message
            lastModelNoticeAt = now
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    val stepSplitter: AiStepSplitter by lazy { AiStepSplitter(ai) }
    val noteOrganizer: AiRecipeOrganizer by lazy { AiRecipeOrganizer(ai) }
    val bowlOrganizer: AiBowlOrganizer by lazy { AiBowlOrganizer(ai) }
    val amountEstimator: AiAmountEstimator by lazy { AiAmountEstimator(ai) }
    val calorieEstimator: AiCalorieEstimator by lazy { AiCalorieEstimator(ai) }

    /** 每道菜当前的用量倍率，只在本次运行里有效；详情、备料、做菜页共用 */
    val scales = mutableStateMapOf<Long, Double>()
    fun scaleOf(recipeId: Long): Double = scales[recipeId] ?: 1.0

    override fun onCreate() {
        super.onCreate()
        images = ImageStore(this)
        database = MiseDatabase.create(this)
        repository = RecipeRepository(database, images)
        timers = TimerManager(this)
        kitchen = KitchenSync(this, repository, images, aiSettings, appScope).also { it.start() }
        Notifications.createChannels(this)
        appScope.launch { updater.cleanup() }
    }
}
