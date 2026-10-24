package com.personal.mise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.mise.util.DayText
import java.util.UUID

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        Step::class,
        SauceBowl::class,
        SauceBowlItem::class,
        CookLog::class,
        MealPlan::class,
        ChatMessageEntity::class,
    ],
    version = MiseDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(StepImages::class)
abstract class MiseDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun cookLogDao(): CookLogDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun chatDao(): ChatDao

    companion object {
        const val VERSION = 15
        const val NAME = "mise.db"

        fun create(context: Context): MiseDatabase =
            Room.databaseBuilder(context, MiseDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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

/**
 * v11：今日菜单并进计划。计划加顺序、做到第几步、做没做完；
 * 今日菜单里还在的菜变成今天的计划，进度和备料勾选带过去，然后删掉今日菜单表
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meal_plan ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE meal_plan ADD COLUMN currentStep INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE meal_plan ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
        // 以前做完只记在今日菜单里：记过做菜的那天，计划也算做完，免得被当成没做完的
        db.execSQL("UPDATE meal_plan SET done = 1 WHERE uid IN (SELECT uid FROM cook_log WHERE deleted = 0)")
        val today = DayText.today()
        val now = System.currentTimeMillis()
        db.query(
            """
            SELECT r.uid, r.title, t.sortOrder, t.currentStep, t.bowlsChecked, t.done
            FROM today_menu t JOIN recipe r ON r.id = t.recipeId
            WHERE t.removed = 0 AND r.deleted = 0
            """
        ).use { c ->
            while (c.moveToNext()) {
                val recipeUid = c.getString(0)
                val uid = "${recipeUid}_$today"
                // 今日菜单里没勾过备料时，留着计划里提前勾的
                val checked = c.getString(4).ifEmpty {
                    db.query("SELECT checked FROM meal_plan WHERE uid = ? AND deleted = 0", arrayOf<Any>(uid)).use { p ->
                        if (p.moveToFirst()) p.getString(0) else ""
                    }
                }
                db.execSQL(
                    "INSERT OR REPLACE INTO meal_plan (uid, recipeUid, title, date, checked, sortOrder, currentStep, done, updatedAt, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    arrayOf<Any>(uid, recipeUid, c.getString(1), today, checked, c.getInt(2), c.getInt(3), c.getInt(5), now),
                )
            }
        }
        db.execSQL("DROP TABLE today_menu")
    }
}

/** v12：菜谱加 review，AI 对菜谱的评价 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN review TEXT")
    }
}

/** v13：「问问 AI」的对话 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ai_chat_message (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, thread TEXT NOT NULL, " +
                "fromUser INTEGER NOT NULL, text TEXT NOT NULL, card TEXT, model TEXT, createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_message_thread ON ai_chat_message (thread)")
    }
}

/** v14：菜谱加 stepTimes，AI 估的每一步时长 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN stepTimes TEXT")
    }
}

/**
 * v15：一步可以放好几张图，步骤的 image 换成 images（换行隔开）。
 * 老版本的 SQLite 删不了列，整张表重建，编号不变，调料碗还对得上
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS step_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recipeId INTEGER NOT NULL, " +
                "sortOrder INTEGER NOT NULL, text TEXT NOT NULL, images TEXT NOT NULL, timerSeconds INTEGER, " +
                "FOREIGN KEY(recipeId) REFERENCES recipe(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO step_new (id, recipeId, sortOrder, text, images, timerSeconds) " +
                "SELECT id, recipeId, sortOrder, text, COALESCE(image, ''), timerSeconds FROM step"
        )
        db.execSQL("DROP TABLE step")
        db.execSQL("ALTER TABLE step_new RENAME TO step")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_step_recipeId ON step (recipeId)")
    }
}
