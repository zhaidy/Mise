package com.personal.mise.ai

import com.personal.mise.importer.ImportException

/** 对话里的一句：用户说的，或 AI 回的 */
data class ChatTurn(val fromUser: Boolean, val text: String)

/** 某一家服务商的流式聊天 */
interface AiChatProvider {
    /**
     * 按 [system] 和对话 [turns] 让模型接着说，收到一段文字就交给 [onText]；返回实际用的模型（显示名）。
     * [model] 是 API 名；额度用完、余额不足这类换个模型能解决的问题抛 [AiUnavailableException]。
     */
    suspend fun chat(model: String, system: String, turns: List<ChatTurn>, onText: (String) -> Unit): String
}

/** 聊天用的 AI：按设置里的模型顺序转给各家 */
interface AiChatClient {
    /** 返回给人看的模型说明，比如「Gemini 3.8 Flash」或「Gemini 今天免费额度用完了，这次用的是 DeepSeek Flash」 */
    suspend fun chat(system: String, turns: List<ChatTurn>, onText: (String) -> Unit): String
}

/**
 * 和 [SwitchableAiClient] 一样按设置里勾选的模型从上往下试；
 * 只有还没收到任何文字时才换下一个模型，已经说了一半再出错就直接报错，免得两个模型的话接在一起。
 */
class SwitchableAiChat(
    private val settings: AiSettings,
    private val clients: Map<AiProvider, AiChatProvider>,
) : AiChatClient {
    override suspend fun chat(system: String, turns: List<ChatTurn>, onText: (String) -> Unit): String {
        val candidates = settings.models.filter { settings.key(it.provider).isNotBlank() }
        if (candidates.isEmpty()) throw ImportException(SwitchableAiClient.NO_KEY)
        val skipped = mutableListOf<String>()
        val details = mutableListOf<String>()
        val unreachable = mutableSetOf<AiProvider>()
        for (model in candidates) {
            if (model.provider in unreachable) continue
            var started = false
            val used = try {
                clients.getValue(model.provider).chat(model.id, system, ChatTurns.normalize(turns)) {
                    if (it.isNotEmpty()) {
                        started = true
                        onText(it)
                    }
                }
            } catch (e: AiUnavailableException) {
                if (started) throw e
                skipped += "${model.label} ${e.message}"
                details += "${model.label}：${e.message}"
                continue
            } catch (e: AiNetworkException) {
                if (started) throw e
                unreachable += model.provider
                skipped += "连不上 ${model.provider.label}"
                details += e.message.orEmpty()
                continue
            }
            return if (skipped.isEmpty()) used else "${skipped.first()}，这次用的是 $used"
        }
        throw ImportException("勾选的模型都用不了：\n" + details.joinToString("\n"))
    }
}

object ChatTurns {
    /**
     * 各家都要求用户和 AI 一来一回、从用户开始：去掉空话，连着的同一方合成一句，开头的 AI 话去掉。
     */
    fun normalize(turns: List<ChatTurn>): List<ChatTurn> {
        val out = mutableListOf<ChatTurn>()
        turns.filter { it.text.isNotBlank() }.forEach { turn ->
            val last = out.lastOrNull()
            when {
                out.isEmpty() && !turn.fromUser -> Unit
                last != null && last.fromUser == turn.fromUser -> out[out.size - 1] = last.copy(text = last.text + "\n\n" + turn.text)
                else -> out += turn
            }
        }
        return out
    }

    /** SSE 的一行：「data: …」返回后面的内容，其他行（event:、空行、注释）返回 null */
    fun sseData(line: String): String? {
        if (!line.startsWith("data:")) return null
        return line.removePrefix("data:").trimStart()
    }
}
