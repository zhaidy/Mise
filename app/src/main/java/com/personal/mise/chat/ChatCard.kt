package com.personal.mise.chat

import com.personal.mise.ai.RecipeReview
import org.json.JSONArray
import org.json.JSONObject

/** 卡片上的方案处理到哪一步了 */
enum class CardStatus {
    /** 等用户决定 */
    PENDING,
    /** 用上了 */
    APPLIED,
    /** 用户说不用了 */
    DISMISSED,
    /** 用上以后又撤销了 */
    UNDONE,
    /** App 重开过、或菜谱后来又改过，这个方案没法再用 */
    EXPIRED,
}

/** 方案改的是什么，决定按钮上写什么 */
enum class ProposalKind(val applyLabel: String, val doneLabel: String) {
    EDIT("应用修改", "已改好"),
    FIX("应用修改", "已照评价改好"),
    AMOUNTS("应用修改", "已补全用量"),
    SPLIT("应用修改", "已拆分步骤"),
    TRANSLATE("换成中文", "已翻译成中文"),
    BOWLS("换成这样分", "已重新分调料碗"),
    CALORIES("记到菜谱上", "已记到菜谱上"),
    NOTE("写进批注", "已写进批注"),
}

/** 对话里 AI 给出的结果卡片，跟着那句话存进数据库 */
sealed interface ChatCard {
    fun toJson(): JSONObject

    /** 后面接着聊时告诉 AI 这里发生了什么 */
    fun describe(): String

    /** 要用户确认才改菜谱的方案：改了哪些地方列在 [changes] */
    data class Proposal(
        val kind: ProposalKind,
        val title: String,
        val summary: String,
        val changes: List<String>,
        val status: CardStatus = CardStatus.PENDING,
    ) : ChatCard {
        override fun toJson(): JSONObject = JSONObject()
            .put("type", "proposal")
            .put("kind", kind.name)
            .put("title", title)
            .put("summary", summary)
            .put("changes", JSONArray(changes))
            .put("status", status.name)

        override fun describe(): String {
            val state = when (status) {
                CardStatus.PENDING -> "等用户确认"
                CardStatus.APPLIED -> "用户已应用"
                CardStatus.DISMISSED -> "用户没用"
                CardStatus.UNDONE -> "用户应用后又撤销了"
                CardStatus.EXPIRED -> "已失效"
            }
            return "（App 给出了「$title」：${summary.ifBlank { changes.joinToString("；") }}。$state）"
        }
    }

    /** AI 的口味评价，已经存到菜谱上 */
    data class Review(val review: RecipeReview) : ChatCard {
        override fun toJson(): JSONObject = JSONObject().put("type", "review").put("review", review.toJson())

        override fun describe(): String = buildString {
            append("（App 做了口味评价：${review.score}/10，${review.summary}")
            if (review.highlights.isNotEmpty()) append("；亮点：${review.highlights.joinToString("、")}")
            if (review.concerns.isNotEmpty()) append("；要注意：${review.concerns.joinToString("、")}")
            append("）")
        }
    }

    /** 提到的几道菜，点了进详情页 */
    data class Recipes(val ids: List<Long>, val titles: List<String>) : ChatCard {
        override fun toJson(): JSONObject = JSONObject().put("type", "recipes").put("ids", JSONArray(ids)).put("titles", JSONArray(titles))
        override fun describe(): String = "（App 列出了这几道菜：${titles.joinToString("、")}）"
    }

    /** 把几道菜排进某天的计划 */
    data class Plan(
        val date: String,
        val ids: List<Long>,
        val titles: List<String>,
        val status: CardStatus = CardStatus.PENDING,
    ) : ChatCard {
        override fun toJson(): JSONObject = JSONObject()
            .put("type", "plan")
            .put("date", date)
            .put("ids", JSONArray(ids))
            .put("titles", JSONArray(titles))
            .put("status", status.name)

        override fun describe(): String {
            val state = when (status) {
                CardStatus.APPLIED -> "用户已排上"
                CardStatus.PENDING -> "等用户确认"
                CardStatus.UNDONE -> "排上后又撤销了"
                else -> "用户没排"
            }
            return "（App 给出了计划：$date 做 ${titles.joinToString("、")}。$state）"
        }
    }

    /** 导入新菜谱：[text] 是链接或笔记文字，[link] 时按链接导入，否则交给 AI 整理 */
    data class Import(val text: String, val link: Boolean, val status: CardStatus = CardStatus.PENDING) : ChatCard {
        override fun toJson(): JSONObject = JSONObject().put("type", "import").put("text", text).put("link", link).put("status", status.name)
        override fun describe(): String = if (status == CardStatus.APPLIED) "（用户打开了导入页）" else "（App 准备好了导入）"
    }

    /** 让 AI 按 [request] 写一份新菜谱，点了去预览页看过再存 */
    data class Generate(val request: String, val status: CardStatus = CardStatus.PENDING) : ChatCard {
        override fun toJson(): JSONObject = JSONObject().put("type", "generate").put("request", request).put("status", status.name)
        override fun describe(): String = if (status == CardStatus.APPLIED) "（用户打开了 AI 写的菜谱）" else "（App 准备好了让 AI 写菜谱）"
    }

    /** 出错了；[retry] 是重试时要重发的话，[action] 是重试时要重做的操作 */
    data class Error(val message: String, val retry: String? = null, val action: String? = null) : ChatCard {
        override fun toJson(): JSONObject = JSONObject()
            .put("type", "error")
            .put("message", message)
            .put("retry", retry ?: JSONObject.NULL)
            .put("action", action ?: JSONObject.NULL)

        override fun describe(): String = ""
    }

    companion object {
        /** 格式不对或不认识的卡片返回 null，那句话照样显示 */
        fun fromJson(json: String?): ChatCard? = json?.takeIf { it.isNotBlank() }?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

        private fun parse(obj: JSONObject): ChatCard? = when (obj.optString("type")) {
            "proposal" -> Proposal(
                kind = ProposalKind.valueOf(obj.getString("kind")),
                title = obj.optString("title"),
                summary = obj.optString("summary"),
                changes = strings(obj.optJSONArray("changes")),
                status = status(obj),
            )
            "review" -> RecipeReview.parse(obj.getJSONObject("review"))?.let { Review(it) }
            "recipes" -> Recipes(longs(obj.optJSONArray("ids")), strings(obj.optJSONArray("titles")))
            "plan" -> Plan(obj.optString("date"), longs(obj.optJSONArray("ids")), strings(obj.optJSONArray("titles")), status(obj))
            "import" -> Import(obj.optString("text"), obj.optBoolean("link"), status(obj))
            "generate" -> Generate(obj.optString("request"), status(obj))
            "error" -> Error(obj.optString("message"), obj.optNullable("retry"), obj.optNullable("action"))
            else -> null
        }

        private fun status(obj: JSONObject) = runCatching { CardStatus.valueOf(obj.optString("status")) }.getOrDefault(CardStatus.PENDING)

        private fun strings(arr: JSONArray?): List<String> = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }

        private fun longs(arr: JSONArray?): List<Long> = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optLong(it) }

        private fun JSONObject.optNullable(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
    }
}
