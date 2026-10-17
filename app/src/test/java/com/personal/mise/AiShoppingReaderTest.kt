package com.personal.mise

import com.personal.mise.ai.AiShoppingReader
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiShoppingReaderTest {

    @Test
    fun parseResponse() {
        val reply = JSONObject("""{"items":[" 鸡蛋 2盒 ","","酱油",null]}""")
        assertEquals(listOf("鸡蛋 2盒", "酱油"), AiShoppingReader.parseResponse(reply))
    }

    @Test
    fun nothingRecognized() {
        assertThrows(ImportException::class.java) { AiShoppingReader.parseResponse(JSONObject("""{"items":[]}""")) }
        assertThrows(ImportException::class.java) { AiShoppingReader.parseResponse(JSONObject("{}")) }
    }
}
