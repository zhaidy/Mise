package com.personal.mise.sync

import com.personal.mise.data.RecipeFull
import com.personal.mise.data.tagList

/**
 * 一道菜在云端的样子：菜谱、用料、步骤、调料碗打包成一个文档。
 * 本地 id 只在一台手机上有意义，所以调料碗按下标引用用料和步骤。
 */
data class SyncedRecipe(
    val uid: String,
    val title: String,
    val coverImage: String?,
    val sourceUrl: String?,
    val servings: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val bowlsManual: Boolean,
    val calories: Int?,
    val category: String?,
    val subcategory: String?,
    val tags: List<String>,
    val ingredients: List<SyncedIngredient>,
    val steps: List<SyncedStep>,
    val bowls: List<SyncedBowl>,
    /** 翻译前的原文（RecipeText 的 JSON），没翻译过为空 */
    val original: String? = null,
    /** 自己的批注，没写为空 */
    val note: String? = null,
    /** AI 评价（RecipeReview 的 JSON），没评过为空 */
    val review: String? = null,
    /** AI 估的步骤时长（StepTimes 的 JSON），没估过为空 */
    val stepTimes: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "schema" to SCHEMA,
        "title" to title,
        "coverImage" to coverImage,
        "sourceUrl" to sourceUrl,
        "servings" to servings,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "deleted" to deleted,
        "bowlsManual" to bowlsManual,
        "calories" to calories,
        "category" to category,
        "subcategory" to subcategory,
        "tags" to tags,
        "original" to original,
        "note" to note,
        "review" to review,
        "stepTimes" to stepTimes,
        "ingredients" to ingredients.map { mapOf("name" to it.name, "amountText" to it.amountText, "kind" to it.kind) },
        // image 只放第一张，给还不认 images 的旧版本看
        "steps" to steps.map {
            mapOf("text" to it.text, "images" to it.images, "image" to it.images.firstOrNull(), "timerSeconds" to it.timerSeconds)
        },
        "bowls" to bowls.map { bowl ->
            mapOf(
                "label" to bowl.label,
                "stepIndex" to bowl.stepIndex,
                "items" to bowl.items.map { mapOf("ingredientIndex" to it.ingredientIndex, "amountText" to it.amountText) },
            )
        },
    )

    companion object {
        const val SCHEMA = 1

        fun from(full: RecipeFull): SyncedRecipe {
            val ingredientIndex = full.ingredients.withIndex().associate { (i, g) -> g.id to i }
            val stepIndex = full.steps.withIndex().associate { (i, s) -> s.id to i }
            val r = full.recipe
            return SyncedRecipe(
                uid = r.uid,
                title = r.title,
                coverImage = r.coverImage,
                sourceUrl = r.sourceUrl,
                servings = r.servings,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt,
                deleted = r.deleted,
                bowlsManual = r.bowlsManual,
                calories = r.calories,
                category = r.category,
                subcategory = r.subcategory,
                tags = r.tagList,
                original = r.original,
                note = r.note,
                review = r.review,
                stepTimes = r.stepTimes,
                ingredients = full.ingredients.map { SyncedIngredient(it.name, it.amountText, it.kind) },
                steps = full.steps.map { SyncedStep(it.text, it.images, it.timerSeconds) },
                bowls = full.bowls.map { b ->
                    SyncedBowl(
                        label = b.bowl.label,
                        stepIndex = b.bowl.stepId?.let { stepIndex[it] },
                        items = b.items.mapNotNull { item ->
                            ingredientIndex[item.ingredientId]?.let { SyncedBowlItem(it, item.amountText) }
                        },
                    )
                },
            )
        }

        /** Firestore 读回来的数字都是 Long；格式不对或来自更新的版本时返回 null */
        fun fromMap(uid: String, data: Map<String, Any?>): SyncedRecipe? = runCatching {
            if (((data["schema"] as? Number)?.toInt() ?: return null) > SCHEMA) return null
            val ingredients = list(data["ingredients"]).map {
                SyncedIngredient(it["name"] as String, it["amountText"] as String, it["kind"] as String)
            }
            val steps = list(data["steps"]).map {
                // 旧版本写的文档只有一张 image
                val images = (it["images"] as List<*>?)?.map { path -> path as String } ?: listOfNotNull(it["image"] as String?)
                SyncedStep(it["text"] as String, images, (it["timerSeconds"] as Number?)?.toInt())
            }
            val bowls = list(data["bowls"]).map { bowl ->
                SyncedBowl(
                    label = bowl["label"] as String,
                    stepIndex = (bowl["stepIndex"] as Number?)?.toInt()?.takeIf { it in steps.indices },
                    items = list(bowl["items"]).mapNotNull { item ->
                        val index = (item["ingredientIndex"] as Number).toInt()
                        if (index in ingredients.indices) SyncedBowlItem(index, item["amountText"] as String) else null
                    },
                )
            }
            SyncedRecipe(
                uid = uid,
                title = data["title"] as String,
                coverImage = data["coverImage"] as String?,
                sourceUrl = data["sourceUrl"] as String?,
                servings = data["servings"] as String?,
                createdAt = (data["createdAt"] as Number).toLong(),
                updatedAt = (data["updatedAt"] as Number).toLong(),
                deleted = data["deleted"] as Boolean,
                bowlsManual = data["bowlsManual"] as Boolean,
                // 旧版本写的文档没有这一项
                calories = (data["calories"] as Number?)?.toInt(),
                category = data["category"] as String?,
                subcategory = data["subcategory"] as String?,
                tags = (data["tags"] as List<*>?).orEmpty().map { it as String },
                original = data["original"] as String?,
                note = data["note"] as String?,
                review = data["review"] as String?,
                stepTimes = data["stepTimes"] as String?,
                ingredients = ingredients,
                steps = steps,
                bowls = bowls,
            )
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        private fun list(value: Any?): List<Map<String, Any?>> = (value as List<*>).map { it as Map<String, Any?> }
    }
}

data class SyncedIngredient(val name: String, val amountText: String, val kind: String)

data class SyncedStep(val text: String, val images: List<String>, val timerSeconds: Int?)

data class SyncedBowl(val label: String, val stepIndex: Int?, val items: List<SyncedBowlItem>)

data class SyncedBowlItem(val ingredientIndex: Int, val amountText: String)
