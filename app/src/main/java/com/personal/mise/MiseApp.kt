package com.personal.mise

import android.app.Application
import com.personal.mise.ai.AiSettings
import com.personal.mise.ai.GeminiStepSplitter
import com.personal.mise.data.MiseDatabase
import com.personal.mise.data.RecipeRepository
import com.personal.mise.image.ImageStore
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.timer.Notifications
import com.personal.mise.timer.TimerManager
import com.personal.mise.update.UpdateChecker

class MiseApp : Application() {
    lateinit var images: ImageStore
        private set
    lateinit var repository: RecipeRepository
        private set
    lateinit var timers: TimerManager
        private set
    val importer: RecipeImporter by lazy { RecipeImporter(images) }
    val aiSettings: AiSettings by lazy { AiSettings(this) }
    val updater: UpdateChecker by lazy { UpdateChecker(this) }
    val stepSplitter: GeminiStepSplitter by lazy { GeminiStepSplitter(aiSettings) }

    override fun onCreate() {
        super.onCreate()
        images = ImageStore(this)
        repository = RecipeRepository(MiseDatabase.create(this), images)
        timers = TimerManager(this)
        Notifications.createChannels(this)
    }
}
