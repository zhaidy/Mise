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
        MealPlan::class,
    ],
    version = MiseDatabase.VERSION,
    exportSchema = false,
)
abstract class MiseDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun todayDao(): TodayDao
    abstract fun cookLogDao(): CookLogDao
    abstract fun mealPlanDao(): MealPlanDao

    companion object {
        const val VERSION = 10
        const val NAME = "mise.db"

        fun create(context: Context): MiseDatabase =
            Room.databaseBuilder(context, MiseDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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

/** v6：菜谱加大类、小类和标签，按分类找菜 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE recipe ADD COLUMN subcategory TEXT")
        db.execSQL("ALTER TABLE recipe ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

/** v7：菜谱加 original，翻译过的菜谱留着原文 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN original TEXT")
    }
}

/** v8：做菜计划 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS meal_plan (uid TEXT NOT NULL, recipeUid TEXT NOT NULL, title TEXT NOT NULL, " +
                "date TEXT NOT NULL, checked TEXT NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_plan_recipeUid ON meal_plan (recipeUid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_plan_date ON meal_plan (date)")
    }
}

/** v9：今日菜单加 done，做完的菜打个勾 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE today_menu ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
    }
}

/** v10：菜谱加 note，自己的批注 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN note TEXT")
    }
}
