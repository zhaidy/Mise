package com.personal.mise

import com.personal.mise.ai.AiClassifier
import com.personal.mise.ai.Classification
import com.personal.mise.data.CategoryNode
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClassifierTest {

    private val tree = listOf(CategoryNode("家常菜", listOf("炒菜", "炖菜")), CategoryNode("甜品", emptyList()))
    private val tags = listOf("荤菜", "下饭")

    @Test
    fun promptListsExistingOptions() {
        val prompt = AiClassifier.prompt("红烧肉", listOf("五花肉", " "), listOf("焯水"), tree, tags)
        assertTrue(prompt.contains("- 家常菜：炒菜、炖菜\n"))
        assertTrue(prompt.contains("- 甜品\n"))
        assertTrue(prompt.contains("标签：荤菜、下饭\n"))
        assertTrue(prompt.contains("用料：五花肉\n"))
        assertFalse(prompt.contains("烘焙"))
    }

    @Test
    fun promptFallsBackToCommonOptionsWhenLibraryIsEmpty() {
        val prompt = AiClassifier.prompt("红烧肉", emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(prompt.contains("大类：家常菜、汤"))
        assertTrue(prompt.contains("标签：荤菜、素菜"))
    }

    @Test
    fun keepsOnlyExistingNames() {
        val reply = JSONObject("""{"category":"家常菜","subcategory":"炖菜","tags":["#荤菜","新标签","下饭","荤菜"]}""")
        assertEquals(Classification("家常菜", "炖菜", listOf("荤菜", "下饭")), AiClassifier.parseResponse(reply, tree, tags))
    }

    @Test
    fun dropsMadeUpCategoryAndWrongSubcategory() {
        val made = JSONObject("""{"category":"硬菜","subcategory":"炖菜","tags":[]}""")
        assertTrue(AiClassifier.parseResponse(made, tree, tags).isEmpty)
        val wrongSub = JSONObject("""{"category":"甜品","subcategory":"炖菜","tags":[]}""")
        assertEquals(Classification("甜品", null, emptyList()), AiClassifier.parseResponse(wrongSub, tree, tags))
    }

    @Test
    fun throwsOnMalformedReply() {
        assertThrows(ImportException::class.java) { AiClassifier.parseResponse(JSONObject("{}"), tree, tags) }
    }

    @Test
    fun describe() {
        assertEquals("家常菜 › 炒菜 · #荤菜 #下饭", Classification("家常菜", "炒菜", listOf("荤菜", "下饭")).describe())
        assertEquals("#荤菜", Classification(null, null, listOf("荤菜")).describe())
    }
}
