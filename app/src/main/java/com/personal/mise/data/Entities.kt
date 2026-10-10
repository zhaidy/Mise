package com.personal.mise.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipe")
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

data class BowlFull(val bowl: SauceBowl, val items: List<BowlItemRow>)

data class RecipeFull(
    val recipe: Recipe,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val bowls: List<BowlFull>,
) {
    fun bowlsForStep(stepId: Long): List<BowlFull> = bowls.filter { it.bowl.stepId == stepId }
}
