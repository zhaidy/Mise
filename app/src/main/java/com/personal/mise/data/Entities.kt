package com.personal.mise.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recipe", indices = [Index(value = ["uid"], unique = true)])
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 相对 filesDir 的路径，如 images/xxx.jpg */
    val coverImage: String? = null,
    /** 为空表示手动录入 */
    val sourceUrl: String? = null,
    val servings: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 用户手动调整过调料碗后为 true，之后保存菜谱不再自动覆盖 */
    val bowlsManual: Boolean = false,
    /** 跨设备的身份，同步时用它对应同一道菜；本地 id 只在这台手机上有意义 */
    val uid: String = UUID.randomUUID().toString(),
    /** 菜谱本身、用料、步骤或调料碗改动时更新，同步时按它判断谁新 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 删除只做标记并清空内容，好让删除也能同步到别的设备 */
    val deleted: Boolean = false,
    /** 整道菜（按菜谱原文的份量）大约多少千卡，AI 估算或手填；空表示还没算 */
    val calories: Int? = null,
    /** 大类，如「家常菜」「汤」；空表示未分类 */
    val category: String? = null,
    /** 大类下的小类，如家常菜下的「炒菜」「炖菜」；没有大类时也为空 */
    val subcategory: String? = null,
    /** 标签，逗号分隔，如「荤菜,好吃」；见 RecipeTags */
    val tags: String = "",
    /** 从英文网站导入、翻译成中文的菜谱留着原文（RecipeText 的 JSON），详情页可以切过去看；空表示没翻译过 */
    val original: String? = null,
    /** 自己的批注，如「盐少放一半」「火开大点」；打开这道菜时显示在最上面，空表示没写 */
    val note: String? = null,
)

@Entity(
    tableName = "ingredient",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val name: String,
    /** 保留原文，如「1 汤匙」「适量」 */
    val amountText: String,
    /** IngredientKind.MAIN / IngredientKind.SEASONING */
    val kind: String,
    @ColumnInfo(name = "sortOrder") val order: Int,
)

@Entity(
    tableName = "step",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Step(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    @ColumnInfo(name = "sortOrder") val order: Int,
    val text: String,
    val image: String? = null,
    val timerSeconds: Int? = null,
)

@Entity(
    tableName = "sauce_bowl",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class SauceBowl(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** 如「调料碗 A」 */
    val label: String,
    /** 这碗调料下锅的那一步，可空 */
    val stepId: Long?,
    @ColumnInfo(name = "sortOrder") val order: Int,
)

@Entity(
    tableName = "sauce_bowl_item",
    foreignKeys = [
        ForeignKey(
            entity = SauceBowl::class,
            parentColumns = ["id"],
            childColumns = ["bowlId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bowlId"), Index("ingredientId")],
)
data class SauceBowlItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bowlId: Long,
    val ingredientId: Long,
    /** 同一调料分几次下锅时，每碗记自己的用量 */
    val amountText: String,
)

/**
 * 做菜记录：哪天做过哪道菜。一道菜一天只记一条，
 * 主键是「菜谱 uid_日期」，两台手机同一天各记一次也只算一条。
 */
@Entity(tableName = "cook_log", indices = [Index("recipeUid"), Index("date")])
data class CookLog(
    @PrimaryKey val uid: String,
    /** 不设外键：菜谱删了，记录还留着 */
    val recipeUid: String,
    /** 记下当时的菜名，菜谱删了也看得出做的是什么 */
    val title: String,
    /** 本地日期，如 2026-10-11，按字符串排序就是按日期排序 */
    val date: String,
    /** 新记、删除时更新，同步时按它判断谁新 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 删除只做标记，好让删除也能同步到别的设备 */
    val deleted: Boolean = false,
) {
    companion object {
        fun uidOf(recipeUid: String, date: String) = "${recipeUid}_$date"
    }
}

/**
 * 做菜计划：哪天打算做哪道菜。和做菜记录一样，一道菜一天只排一次，
 * 主键是「菜谱 uid_日期」，两台手机同一天各排一次也只算一条。
 * 今日菜单就是今天的计划：顺序、做到第几步、做没做完也记在这里。
 */
@Entity(tableName = "meal_plan", indices = [Index("recipeUid"), Index("date")])
data class MealPlan(
    @PrimaryKey val uid: String,
    /** 不设外键：菜谱删了，计划还看得出排的是什么 */
    val recipeUid: String,
    /** 记下排的时候的菜名 */
    val title: String,
    /** 本地日期，如 2026-10-11 */
    val date: String,
    /** 备料清单里已勾选的项目，逗号分隔，如「i12,b3」 */
    val checked: String = "",
    /** 同一天里的先后，今日菜单里拖动排序 */
    @ColumnInfo(name = "sortOrder") val order: Int = 0,
    /** 做菜页翻到第几步 */
    val currentStep: Int = 0,
    /** 这道菜做完了；可以随时取消 */
    val done: Boolean = false,
    /** 排上、移出、换顺序、翻步骤、勾备料、做完时更新，同步时按它判断谁新 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 移出计划只做标记，好让移出也能同步到别的设备 */
    val deleted: Boolean = false,
) {
    companion object {
        fun uidOf(recipeUid: String, date: String) = "${recipeUid}_$date"
    }
}

/** 计划页的一行；菜谱还在时带上它的 id、封面和热量 */
data class PlanRow(
    val uid: String,
    val date: String,
    val title: String,
    val checked: String,
    val done: Boolean,
    val recipeId: Long?,
    val coverImage: String?,
    val calories: Int?,
)

/** 记录页的一行；菜谱还在时带上它的 id 和封面 */
data class CookLogRow(
    val uid: String,
    val date: String,
    val title: String,
    val recipeId: Long?,
    val coverImage: String?,
    /** 菜谱现在记的整道菜热量，菜谱删了或没估过是 null */
    val calories: Int?,
)

/** 一道菜最后一次做的日子，菜单页用 */
data class LastCooked(val recipeUid: String, val date: String)

/** 调料碗里的一项，连同用料名称 */
data class BowlItemRow(
    val id: Long,
    val bowlId: Long,
    val ingredientId: Long,
    val name: String,
    val amountText: String,
)

/** 今日菜单的一行：今天的计划，菜谱还在的才算 */
data class TodayRow(
    /** 计划的 uid */
    val uid: String,
    val recipeId: Long,
    val position: Int,
    val currentStep: Int,
    val checked: String,
    val title: String,
    val coverImage: String?,
    val stepCount: Int,
    val calories: Int?,
    val done: Boolean,
)

data class RecipeStamp(val uid: String, val updatedAt: Long)

data class BowlFull(val bowl: SauceBowl, val items: List<BowlItemRow>)

data class RecipeFull(
    val recipe: Recipe,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val bowls: List<BowlFull>,
) {
    fun bowlsForStep(stepId: Long): List<BowlFull> = bowls.filter { it.bowl.stepId == stepId }
}
