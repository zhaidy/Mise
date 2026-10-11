package com.personal.mise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

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
        const val VERSION = 4
        const val NAME = "mise.db"

        fun create(context: Context): MiseDatabase =
            Room.databaseBuilder(context, MiseDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

/** v2：菜谱加 uid / updatedAt / deleted，为多设备同步做准备 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recipe ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipe ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE recipe SET updatedAt = createdAt")
        val ids = db.query("SELECT id FROM recipe").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        ids.forEach { id ->
            db.execSQL("UPDATE recipe SET uid = ? WHERE id = ?", arrayOf<Any>(UUID.randomUUID().toString(), id))
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recipe_uid ON recipe (uid)")
    }
}

/** v3：今日菜单加 updatedAt / removed，为多设备同步做准备 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE today_menu ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE today_menu ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4：菜谱加 calories，整道菜的热量 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN calories INTEGER")
    }
}
