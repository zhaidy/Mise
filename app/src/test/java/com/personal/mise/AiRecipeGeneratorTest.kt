package com.personal.mise

import com.personal.mise.ai.AiClient
import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.chat.ChatAction
import com.personal.mise.chat.ChatActions
import com.personal.mise.chat.ChatCard
import com.personal.mise.importer.ImportException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeGeneratorTest {

    private fun replying(json: String, seen: MutableList<String> = mutableListOf()) = object : AiClient {
        override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject {
            seen += prompt
            return JSONObject(json)
        }
    }

    @Test
    fun `按要求写菜谱，填进导入预览`() = runBlocking {
        val prompts = mutableListOf<String>()
        val ai = replying(
            """{"title":"番茄牛腩","servings":"3人份","ingredients":[{"name":"牛腩","amount":"500克"},{"name":"生抽","amount":"2勺"}],""" +
                """"steps":[{"text":"牛腩切块焯水","timerMinutes":null},{"text":"小火炖 60 分钟","timerMinutes":60}]}""",
            prompts,
        )
        val result = AiRecipeOrganizer(ai).generate(" 番茄牛腩 3人份 ")
        assertTrue(prompts.single().endsWith("我的要求：\n番茄牛腩 3人份"))
        assertEquals("番茄牛腩", result.draft.title)
        assertEquals("3人份", result.draft.servings)
        assertEquals(listOf("牛腩" to "500克", "生抽" to "2勺"), result.draft.ingredients.map { it.name to it.amountText })
        assertEquals(listOf(null, 3600), result.draft.steps.map { it.timerSeconds })
        assertNull(result.draft.sourceUrl)
    }

    @Test
    fun `写不出菜谱或没说要求时报错`() {
        val empty = replying("""{"title":"","servings":null,"ingredients":[],"steps":[]}""")
        val e = assertThrows(ImportException::class.java) { runBlocking { AiRecipeOrganizer(empty).generate("今天天气怎么样") } }
        assertEquals("AI 没写出菜谱，换个说法试试", e.message)
        assertThrows(ImportException::class.java) { runBlocking { AiRecipeOrganizer(empty).generate("  ") } }
    }

    @Test
    fun `聊天里的 generate 标记和卡片`() {
        val reply = ChatActions.parse("番茄牛腩很下饭，我来写一份。<<generate: 番茄牛腩 3人份 少油>>")
        assertEquals(listOf(ChatAction("generate", "番茄牛腩 3人份 少油")), reply.actions)
        val card = ChatCard.Generate("番茄牛腩 3人份")
        assertEquals(card, ChatCard.fromJson(card.toJson().toString()))
    }
}
