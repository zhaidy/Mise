package com.personal.mise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        Step::class,
        SauceBowl::class,
        SauceBowlItem::class,
        TodayMenu::class,
    ],
    version = MiseDatabase.VERSION,
    exportSchema = false,
)
abstract class MiseDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun todayDao(): TodayDao

    companion object {
        const val VERSION = 1
        const val NAME = "mise.db"

        fun create(context: Context): MiseDatabase =
            Room.databaseBuilder(context, MiseDatabase::class.java, NAME).build()
    }
}
