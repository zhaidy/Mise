package com.personal.mise.ai

import com.personal.mise.importer.ImportException

/** 调用 Agnes 的 OpenAI 格式接口；文档没说支持 JSON 模式，只靠提示让它返回 JSON */
class AgnesClient(settings: AiSettings, http: AiHttp, usage: AiUsage) :
    OpenAiCompatibleClient(AiProvider.AGNES, ENDPOINT, settings, http, usage) {

    override val jsonMode = false

    override fun error(code: Int, detail: String?): ImportException = when (code) {
        401 -> ImportException("Agnes API Key 不对，请检查")
        402 -> AiUnavailableException("余额或套餐额度用完了")
        404 -> AiUnavailableException("暂时不能用，可能下线了或这个 Key 没有它的权限")
        429 -> AiUnavailableException("请求太频繁或额度用完了")
        500, 502, 503, 520 -> AiUnavailableException("服务器繁忙")
        else -> ImportException("Agnes 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}")
    }

    companion object {
        private const val ENDPOINT = "https://apihub.agnes-ai.com/v1/chat/completions"

        /** 可选的模型（API 名 to 显示名），都能看图 */
        val MODELS = listOf(
            "agnes-2.5-flash" to "Agnes 2.5 Flash",
            "agnes-2.0-flash" to "Agnes 2.0 Flash",
            "agnes-1.5-flash" to "Agnes 1.5 Flash",
        )
    }
}
