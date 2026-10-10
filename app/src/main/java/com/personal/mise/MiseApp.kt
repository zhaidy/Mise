package com.personal.mise

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import com.personal.mise.ai.AiSettings
import com.personal.mise.ai.GeminiAmountEstimator
import com.personal.mise.ai.GeminiBowlOrganizer
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.GeminiRecipeOrganizer
import com.personal.mise.ai.GeminiStepSplitter
import com.personal.mise.backup.BackupManager
import com.personal.mise.data.MiseDatabase
import com.personal.mise.data.RecipeRepository
import com.personal.mise.image.ImageStore
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.timer.Notifications
import com.personal.mise.timer.TimerManager
import com.personal.mise.update.UpdateChecker

class MiseApp : Application() {
    lateinit var database: MiseDatabase
        private set
    lateinit var images: ImageStore
        private set
    lateinit var repository: RecipeRepository
        private set
    lateinit var timers: TimerManager
        private set
    val importer: RecipeImporter by lazy { RecipeImporter(images) }
    val aiSettings: AiSettings by lazy { AiSettings(this) }
    val updater: UpdateChecker by lazy { UpdateChecker(this) }
    val backup: BackupManager by lazy { BackupManager(this, database) }
    private val gemini: GeminiClient by lazy { GeminiClient(aiSettings) }
    val stepSplitter: GeminiStepSplitter by lazy { GeminiStepSplitter(gemini) }
    val noteOrganizer: GeminiRecipeOrganizer by lazy { GeminiRecipeOrganizer(gemini) }
    val bowlOrganizer: GeminiBowlOrganizer by lazy { GeminiBowlOrganizer(gemini) }
    val amountEstimator: GeminiAmountEstimator by lazy { GeminiAmountEstimator(gemini) }

    /** 每道菜当前的用量倍率，只在本次运行里有效；详情、备料、做菜页共用 */
    val scales = mutableStateMapOf<Long, Double>()
    fun scaleOf(recipeId: Long): Double = scales[recipeId] ?: 1.0

    override fun onCreate() {
        super.onCreate()
        images = ImageStore(this)
        database = MiseDatabase.create(this)
        repository = RecipeRepository(database, images)
        timers = TimerManager(this)
        Notifications.createChannels(this)
    }
}
