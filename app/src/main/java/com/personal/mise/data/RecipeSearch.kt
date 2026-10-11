package com.personal.mise.data

/** 菜谱的 id 和一样用料的名字，搜索时按用料找菜谱用 */
data class IngredientName(val recipeId: Long, val name: String)

/** 搜到的菜谱；靠用料搜到时带上对上的用料，好在卡片上显示 */
data class SearchHit(val recipe: Recipe, val matchedIngredients: List<String> = emptyList())

object RecipeSearch {
    private val separators = Regex("[,，、;；\\s]+")

    /** 空格、逗号、顿号隔开的每个词；「#荤菜」也能搜到标签 */
    fun terms(query: String): List<String> =
        query.split(separators).map { it.trim().trimStart('#', '＃') }.filter { it.isNotEmpty() }.distinct()

    /**
     * 每个词都要对上：菜名、分类、标签里有，或者某样用料的名字里有。
     * 「鸡蛋 番茄」就是两样用料都有的菜。
     */
    fun search(query: String, recipes: List<Recipe>, ingredients: List<IngredientName>): List<SearchHit> {
        val terms = terms(query)
        if (terms.isEmpty()) return recipes.map { SearchHit(it) }
        val namesByRecipe = ingredients.groupBy({ it.recipeId }, { it.name })
        return recipes.mapNotNull { recipe ->
            val names = namesByRecipe[recipe.id].orEmpty()
            val matched = mutableListOf<String>()
            for (term in terms) {
                val inRecipe = listOfNotNull(recipe.title, recipe.category, recipe.subcategory, recipe.tags)
                    .any { it.contains(term, ignoreCase = true) }
                val inIngredients = names.filter { it.contains(term, ignoreCase = true) }
                if (!inRecipe && inIngredients.isEmpty()) return@mapNotNull null
                matched += inIngredients
            }
            SearchHit(recipe, matched.distinct())
        }
    }
}
