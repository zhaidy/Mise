package com.personal.mise

import android.app.Application
import com.personal.mise.data.MiseDatabase
import com.personal.mise.data.RecipeRepository
import com.personal.mise.image.ImageStore
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.timer.Notifications
import com.personal.mise.timer.TimerManager

class MiseApp : Application() {
    lateinit var images: ImageStore
        private set
    lateinit var repository: RecipeRepository
        private set
    lateinit var timers: TimerManager
        private set
    val importer: RecipeImporter by lazy { RecipeImporter(images) }

    override fun onCreate() {
        super.onCreate()
        images = ImageStore(this)
        repository = RecipeRepository(MiseDatabase.create(this), images)
        timers = TimerManager(this)
        Notifications.createChannels(this)
    }
}
