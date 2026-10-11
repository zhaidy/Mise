package com.personal.mise.data

/** 菜谱的标签，如「荤菜」「烘焙」；数据库里用逗号连成一个字符串存 */
object RecipeTags {
    private val separators = Regex("[,，、#＃\\s]+")

    /** 把用户输入拆成标签：逗号、顿号、# 或空白都算分隔，去掉重复的 */
    fun parse(text: String): List<String> =
        text.split(separators).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun format(tags: List<String>): String = tags.flatMap { parse(it) }.distinct().joinToString(",")
}

val Recipe.tagList: List<String> get() = RecipeTags.parse(tags)

/** 大类和它下面的小类，都按用得多少排，常用的在前 */
data class CategoryNode(val name: String, val subcategories: List<String>)

/** 大类、小类各自出现几次 */
data class CategoryCount(val category: String, val subcategory: String?, val count: Int)

object Categories {
    /** 列表里「未分类」的标记；不会和用户起的名字撞上 */
    const val NONE = "\u0000未分类"

    fun tree(counts: List<CategoryCount>): List<CategoryNode> =
        counts.groupBy { it.category }
            .map { (name, rows) -> Triple(name, rows.sumOf { it.count }, rows) }
            .sortedWith(compareByDescending<Triple<String, Int, List<CategoryCount>>> { it.second }.thenBy { it.first })
            .map { (name, _, rows) ->
                val subs = rows.filter { !it.subcategory.isNullOrBlank() }
                    .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.subcategory })
                    .map { it.subcategory!! }
                CategoryNode(name, subs)
            }

    /** 所有菜谱用到的标签，用得多的在前 */
    fun tagsByUse(tagStrings: List<String>): List<String> =
        tagStrings.flatMap { RecipeTags.parse(it) }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}

/**
 * 首页的筛选条件。category 为 null 是全部，[Categories.NONE] 是没分类的；
 * subcategory 为 null 是这个大类下的全部；tags 要全都带上才算。
 */
data class RecipeFilter(
    val category: String? = null,
    val subcategory: String? = null,
    val tags: Set<String> = emptySet(),
) {
    fun matches(recipe: Recipe): Boolean {
        val categoryOk = when (category) {
            null -> true
            Categories.NONE -> recipe.category.isNullOrBlank()
            else -> recipe.category == category && (subcategory == null || recipe.subcategory == subcategory)
        }
        return categoryOk && (tags.isEmpty() || recipe.tagList.containsAll(tags))
    }

    /** 分类、标签被改名或删光了，就去掉对应的条件 */
    fun prune(tree: List<CategoryNode>, allTags: List<String>): RecipeFilter {
        val node = tree.find { it.name == category }
        val cat = if (category == null || category == Categories.NONE || node != null) category else null
        val sub = subcategory?.takeIf { node != null && it in node.subcategories }
        return RecipeFilter(cat, sub, tags.filter { it in allTags }.toSet())
    }
}
