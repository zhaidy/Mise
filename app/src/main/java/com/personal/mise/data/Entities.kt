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

@Entity(
    tableName = "today_menu",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TodayMenu(
    @PrimaryKey val recipeId: Long,
    @ColumnInfo(name = "sortOrder") val order: Int,
    val currentStep: Int = 0,
    /** 备料清单里已勾选的项目，逗号分隔，如「i12,b3」 */
    val bowlsChecked: String = "",
    /** 加入、移出、换顺序、翻步骤、勾备料时更新，同步时按它判断谁新 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 移出菜单只做标记，好让移出也能同步到别的设备 */
    val removed: Boolean = false,
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

/** 记录页的一行；菜谱还在时带上它的 id 和封面 */
data class CookLogRow(
    val uid: String,
    val date: String,
    val title: String,
    val recipeId: Long?,
    val coverImage: String?,
)

/** 调料碗里的一项，连同用料名称 */
data class BowlItemRow(
    val id: Long,
    val bowlId: Long,
    val ingredientId: Long,
    val name: String,
    val amountText: String,
)

data class TodayRow(
    val recipeId: Long,
    val position: Int,
    val currentStep: Int,
    val bowlsChecked: String,
    val title: String,
    val coverImage: String?,
    val stepCount: Int,
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
