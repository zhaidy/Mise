package com.personal.mise

import com.personal.mise.ai.AiCookFeedback
import com.personal.mise.ai.NoteUpdate
import com.personal.mise.importer.ImportException
import com.personal.mise.util.NoteText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCookFeedbackTest {

    @Test
    fun promptHasOldNoteFeedbackAndSteps() {
        val prompt = AiCookFeedback.prompt("可乐鸡翅", "糖少放", "偏咸，鸡翅多炖了 5 分钟才熟", listOf("鸡翅焯水", " ", "加可乐炖 15 分钟"))
        assertTrue(prompt.contains("原来的批注：糖少放\n"))
        assertTrue(prompt.contains("这次的感受：偏咸，鸡翅多炖了 5 分钟才熟\n"))
        assertTrue(prompt.contains("2. 加可乐炖 15 分钟\n"))
        assertTrue(AiCookFeedback.prompt("x", "", "好吃", emptyList()).contains("原来的批注：（没有）"))
    }

    @Test
    fun parsesAndTidiesNote() {
        val reply = JSONObject("""{"note":"- 生抽少放一点\n\n• 第 2 步：多炖 5 分钟\n糖少放","summary":"加了两条"}""")
        assertEquals(NoteUpdate("生抽少放一点\n第 2 步：多炖 5 分钟\n糖少放", "加了两条"), AiCookFeedback.parseResponse(reply))
        assertThrows(ImportException::class.java) { AiCookFeedback.parseResponse(JSONObject("{}")) }
    }

    @Test
    fun stepRemindersFromNote() {
        val note = "生抽少放一点\n第 2 步：多炖 5 分钟\n第2步，火小一点\n第 3 步: 出锅前撒葱"
        assertEquals(listOf("多炖 5 分钟", "火小一点"), NoteText.forStep(note, 1))
        assertEquals(listOf("出锅前撒葱"), NoteText.forStep(note, 2))
        assertEquals(emptyList<String>(), NoteText.forStep(note, 0))
        assertEquals(emptyList<String>(), NoteText.forStep(null, 0))
    }
}
