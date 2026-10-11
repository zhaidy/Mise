package com.personal.mise.bowl

/** 内置调料词典。用料名称命中词典的标为「调料」。 */
object SeasoningDictionary {

    val seasonings: List<String> = listOf(
        "生抽", "老抽", "酱油", "味极鲜", "蒸鱼豉油", "蚝油", "鱼露",
        "料酒", "黄酒", "花雕酒", "米酒", "啤酒", "白酒",
        "香醋", "陈醋", "米醋", "白醋", "醋",
        "白糖", "冰糖", "红糖", "绵白糖", "细砂糖", "砂糖", "糖",
        "盐", "鸡精", "味精", "鸡粉",
        "胡椒粉", "白胡椒粉", "黑胡椒粉", "白胡椒", "黑胡椒", "花椒粉", "五香粉", "十三香", "孜然粉", "辣椒粉", "辣椒面",
        "淀粉", "水淀粉", "生粉", "玉米淀粉", "土豆淀粉",
        "豆瓣酱", "郫县豆瓣", "黄豆酱", "甜面酱", "番茄酱", "番茄沙司", "辣椒酱", "老干妈", "海鲜酱", "柱候酱", "沙茶酱", "芝麻酱", "花生酱", "腐乳",
        "芝麻油", "香油", "辣椒油", "红油", "花椒油", "藤椒油",
        "清水", "高汤",
        "咖喱", "豆豉",
    )

    /** 炒菜用的油：不放进调料碗 */
    val cookingOils: List<String> = listOf(
        "食用油", "植物油", "花生油", "菜籽油", "菜油", "色拉油", "玉米油", "橄榄油", "葵花籽油", "大豆油", "猪油", "油",
    )

    /** 葱姜蒜等爆香料：不单独配碗，与调料同一步下锅时并入该步的调料碗 */
    val aromatics: List<String> = listOf(
        "葱", "小葱", "大葱", "香葱", "葱花", "葱段", "葱白",
        "姜", "生姜", "姜片", "姜丝", "姜末",
        "蒜", "大蒜", "蒜头", "蒜瓣", "蒜末", "蒜片", "蒜蓉",
        "干辣椒", "花椒", "八角", "桂皮", "香叶",
    )

    /** 步骤文字里常用的简称，例如用料写「白糖」、步骤写「糖」 */
    private val aliases: Map<String, List<String>> = mapOf(
        "料酒" to listOf("黄酒"),
        "黄酒" to listOf("料酒"),
        "淀粉" to listOf("生粉"),
        "生粉" to listOf("淀粉"),
        "芝麻油" to listOf("香油"),
        "香油" to listOf("芝麻油"),
        "清水" to listOf("水"),
        "干辣椒" to listOf("干红辣椒", "辣椒干"),
    )

    fun isCookingOil(name: String): Boolean = name.trim() in cookingOils

    fun isAromatic(name: String): Boolean {
        val n = name.trim()
        if (n in aromatics) return true
        if (n.length > 4 || n in notAromatics || seasonings.any { n.contains(it) }) return false
        return aromatics.any { n.contains(it) }
    }

    private val notAromatics = setOf("洋葱", "紫洋葱", "葱头")

    fun isSeasoning(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        if (isCookingOil(n)) return true
        return seasonings.any { n.contains(it) }
    }

    /** 在步骤文字里查找这个用料时使用的词，较长的在前 */
    fun matchTerms(name: String): List<String> {
        val n = name.trim()
        if (n.isEmpty()) return emptyList()
        val terms = linkedSetOf(n)
        val dictionary = if (isAromatic(n)) aromatics else seasonings
        dictionary.filter { n.contains(it) }.forEach { terms += it }
        terms.toList().forEach { t -> aliases[t]?.let { terms += it } }
        // 单个「水」太容易误配（「水开」「水淀粉」），只在用料名就叫「水」时才用
        if (n != "水") terms -= "水"
        return terms.sortedByDescending { it.length }
    }
}
