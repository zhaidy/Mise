package com.personal.mise.chat

/** AI 回复里要 App 去做的一件事，比如 edit「改成4人份」；[arg] 没有时为空字符串 */
data class ChatAction(val name: String, val arg: String = "") {
    /** 存在出错卡片里、重试时再解析回来 */
    fun encode(): String = if (arg.isEmpty()) name else "$name|$arg"

    companion object {
        fun decode(text: String): ChatAction = ChatAction(text.substringBefore('|'), text.substringAfter('|', ""))

        // 菜谱里的
        const val EDIT = "edit"
        const val REVIEW = "review"
        const val FIX = "fix"
        const val CALORIES = "calories"
        const val AMOUNTS = "amounts"
        const val BOWLS = "bowls"
        const val SPLIT = "split"
        const val TRANSLATE = "translate"
        const val NOTE = "note"

        // 两边都有的
        const val PLAN = "plan"

        // 全局的
        const val RECIPES = "recipes"
        const val IMPORT = "import"
    }
}

/** AI 说的话（去掉了操作标记）和要做的事 */
data class ParsedReply(val text: String, val actions: List<ChatAction>)

/**
 * AI 回复的格式：正文后面跟着「<<edit: 改成4人份>>」这样的操作标记，App 去执行，标记不给用户看。
 * 模型偶尔把标记接在句子后面、用全角冒号，都认。
 */
object ChatActions {
    private val marker = Regex("<<\\s*([A-Za-z_]+)\\s*(?:[:：]\\s*([^<>]*?))?\\s*>>")

    fun parse(raw: String): ParsedReply {
        val actions = marker.findAll(raw).map { ChatAction(it.groupValues[1].lowercase(), it.groupValues[2].trim()) }.toList()
        return ParsedReply(tidy(marker.replace(raw, "")), actions.distinct())
    }

    /** 边收边显示：去掉已经完整的标记，最后还没写完的「<<…」也先藏起来 */
    fun visible(raw: String): String {
        var text = marker.replace(raw, "")
        val open = text.lastIndexOf("<<")
        if (open >= 0) text = text.substring(0, open)
        else if (text.endsWith("<")) text = text.dropLast(1)
        return tidy(text)
    }

    private fun tidy(text: String): String = text.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
}
