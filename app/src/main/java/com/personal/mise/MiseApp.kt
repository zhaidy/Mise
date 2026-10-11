package com.personal.mise

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import com.personal.mise.ai.AiAmountEstimator
import com.personal.mise.ai.AiBowlOrganizer
import com.personal.mise.ai.AiClient
import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.ai.AiSettings
import com.personal.mise.ai.AiStepSplitter
import com.personal.mise.ai.ClaudeClient
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.SwitchableAiClient
import com.personal.mise.backup.BackupManager
import com.personal.mise.data.MiseDatabase
import com.personal.mise.data.RecipeRepository
import com.personal.mise.image.ImageStore
import com.personal.mise.sync.KitchenSync
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.timer.Notifications
import com.personal.mise.timer.TimerManager
import com.personal.mise.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val importer: RecipeImporter by lazy { RecipeImporter(images, noteOrganizer) }
    val aiSettings: AiSettings by lazy { AiSettings(this) }
    val updater: UpdateChecker by lazy { UpdateChecker(this) }
    val backup: BackupManager by lazy { BackupManager(this, database) }
    private val ai: AiClient by lazy { SwitchableAiClient(aiSettings, GeminiClient(aiSettings), ClaudeClient(aiSettings)) }
    val stepSplitter: AiStepSplitter by lazy { AiStepSplitter(ai) }
    val noteOrganizer: AiRecipeOrganizer by lazy { AiRecipeOrganizer(ai) }
    val bowlOrganizer: AiBowlOrganizer by lazy { AiBowlOrganizer(ai) }
    val amountEstimator: AiAmountEstimator by lazy { AiAmountEstimator(ai) }

    /** 每道菜当前的用量倍率，只在本次运行里有效；详情、备料、做菜页共用 */
    val scales = mutableStateMapOf<Long, Double>()
    fun scaleOf(recipeId: Long): Double = scales[recipeId] ?: 1.0

    override fun onCreate() {
        super.onCreate()
        images = ImageStore(this)
        database = MiseDatabase.create(this)
        repository = RecipeRepository(database, images)
        timers = TimerManager(this)
        kitchen = KitchenSync(this, repository, images, appScope).also { it.start() }
        Notifications.createChannels(this)
    }
}
