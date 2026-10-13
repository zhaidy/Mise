package com.personal.mise

import com.personal.mise.ai.AiSubstituter
import com.personal.mise.ai.Substitute
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSubstituterTest {

    @Test
    fun promptHasMissingIngredientAndRecipe() {
        val prompt = AiSubstituter.prompt("红烧肉", "生抽", "2勺", listOf("五花肉" to "500克", "生抽" to "2勺", "冰糖" to ""), listOf("焯水", "炒糖色"))
        assertTrue(prompt.startsWith("我正在做「红烧肉」，可是家里没有「生抽」（菜谱里要 2勺）"))
        assertTrue(prompt.contains("这道菜的用料：五花肉 500克、生抽 2勺、冰糖\n"))
        assertTrue(prompt.contains("2. 炒糖色\n"))
    }

    @Test
    fun parsesOptions() {
        val reply = JSONObject(
            """{"options":[{"name":"老抽 + 盐","amount":"半勺 + 一小撮","note":"颜色深一点"},""" +
                """{"name":"老抽 + 盐","amount":"1勺","note":""},{"name":" ","amount":"","note":""},""" +
                """{"name":"蚝油","amount":"1勺","note":"null"}]}""",
        )
        assertEquals(
            listOf(Substitute("老抽 + 盐", "半勺 + 一小撮", "颜色深一点"), Substitute("蚝油", "1勺", "")),
            AiSubstituter.parseResponse(reply),
        )
        assertThrows(ImportException::class.java) { AiSubstituter.parseResponse(JSONObject("""{"options":[]}""")) }
    }

    @Test
    fun noteLine() {
        assertEquals("没有生抽：用老抽 + 盐 半勺（颜色深一点）", Substitute("老抽 + 盐", "半勺", "颜色深一点").noteLine("生抽"))
        assertEquals("没有香菜：不放（少点香味）", Substitute("不放", "", "少点香味").noteLine("香菜"))
    }
}
