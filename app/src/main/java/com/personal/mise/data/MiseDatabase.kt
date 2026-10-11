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
        CookLog::class,
    ],
    version = MiseDatabase.VERSION,
    exportSchema = false,
)
abstract class MiseDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun todayDao(): TodayDao
    abstract fun cookLogDao(): CookLogDao

    companion object {
        const val VERSION = 5
        const val NAME = "mise.db"

        fun create(context: Context): MiseDatabase =
            Room.databaseBuilder(context, MiseDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

/** v4：做菜记录 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cook_log (uid TEXT NOT NULL, recipeUid TEXT NOT NULL, title TEXT NOT NULL, " +
                "date TEXT NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cook_log_recipeUid ON cook_log (recipeUid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cook_log_date ON cook_log (date)")
    }
}

/** v5：菜谱加 calories，整道菜的热量 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN calories INTEGER")
    }
}
