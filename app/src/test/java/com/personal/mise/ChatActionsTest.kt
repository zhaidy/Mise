package com.personal.mise

import com.personal.mise.ai.ChatTurn
import com.personal.mise.ai.ChatTurns
import com.personal.mise.chat.ChatAction
import com.personal.mise.chat.ChatActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatActionsTest {

    @Test
    fun `取出单独一行的标记，正文不带标记`() {
        val reply = ChatActions.parse("可以改成4人份，糖也少一点。\n我把方案列在下面了。\n\n<<edit: 改成4人份，冰糖减到25克>>\n<<calories>>")
        assertEquals("可以改成4人份，糖也少一点。\n我把方案列在下面了。", reply.text)
        assertEquals(listOf(ChatAction("edit", "改成4人份，冰糖减到25克"), ChatAction("calories")), reply.actions)
    }

    @Test
    fun `接在句子后面、全角冒号、大写的标记也认`() {
        val reply = ChatActions.parse("推荐这两道。<<RECIPES：12, 34>>")
        assertEquals("推荐这两道。", reply.text)
        assertEquals(listOf(ChatAction("recipes", "12, 34")), reply.actions)
    }

    @Test
    fun `重复的标记只做一次`() {
        assertEquals(1, ChatActions.parse("<<review>>\n<<review>>").actions.size)
    }

    @Test
    fun `边收边显示时藏起还没写完的标记`() {
        assertEquals("好的，方案如下。", ChatActions.visible("好的，方案如下。\n<<ed"))
        assertEquals("好的，方案如下。", ChatActions.visible("好的，方案如下。\n<"))
        assertEquals("好的。", ChatActions.visible("好的。\n<<review>>\n<<plan: 2026-10-14"))
        assertEquals("1 < 2 没问题", ChatActions.visible("1 < 2 没问题"))
    }

    @Test
    fun `操作存进卡片再读回来不变`() {
        val action = ChatAction("plan", "2026-10-14 12,34")
        assertEquals(action, ChatAction.decode(action.encode()))
        assertEquals(ChatAction("review"), ChatAction.decode(ChatAction("review").encode()))
    }

    @Test
    fun `对话整理成一来一回、从用户开始`() {
        val turns = ChatTurns.normalize(
            listOf(
                ChatTurn(false, "（开场白）"),
                ChatTurn(true, "冰糖没了"),
                ChatTurn(true, "用什么代替"),
                ChatTurn(false, "白砂糖"),
                ChatTurn(false, ""),
                ChatTurn(false, "（App 给出了方案）"),
                ChatTurn(true, "好"),
            ),
        )
        assertEquals(
            listOf(ChatTurn(true, "冰糖没了\n\n用什么代替"), ChatTurn(false, "白砂糖\n\n（App 给出了方案）"), ChatTurn(true, "好")),
            turns,
        )
    }

    @Test
    fun `SSE 只取 data 行`() {
        assertEquals("{\"a\":1}", ChatTurns.sseData("data: {\"a\":1}"))
        assertEquals("[DONE]", ChatTurns.sseData("data:[DONE]"))
        assertNull(ChatTurns.sseData("event: content_block_delta"))
        assertNull(ChatTurns.sseData(""))
    }
}
