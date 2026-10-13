package com.personal.mise

import com.personal.mise.ai.AiBowl
import com.personal.mise.ai.AiBowlOrganizer
import com.personal.mise.importer.ImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBowlOrganizerTest {

    private fun reply(inner: String) = org.json.JSONObject(inner)

    @Test
    fun `解析碗、下锅步骤和用量`() {
        val bowls = AiBowlOrganizer.parseResponse(
            reply(
                """{"bowls":[
                    {"step":1,"items":[{"ingredient":1,"amount":"500克"},{"ingredient":2,"amount":"3克"}]},
                    {"step":3,"items":[{"ingredient":3,"amount":"30克"},{"ingredient":3,"amount":"30克"}]}
                ]}""",
            ),
            ingredientCount = 3,
            stepCount = 3,
        )
        assertEquals(listOf(AiBowl(0, listOf(0 to "500克", 1 to "3克")), AiBowl(2, listOf(2 to "30克"))), bowls)
    }

    @Test
    fun `越界的序号丢掉，空碗不留`() {
        val bowls = AiBowlOrganizer.parseResponse(
            reply("""{"bowls":[{"step":9,"items":[{"ingredient":1,"amount":""}]},{"step":1,"items":[{"ingredient":7,"amount":"1克"}]}]}"""),
            ingredientCount = 2,
            stepCount = 2,
        )
        assertEquals(listOf(AiBowl(null, listOf(0 to ""))), bowls)
    }

    @Test
    fun `同一样用料进了好几个碗时每个碗都标分次加入`() {
        val bowls = AiBowlOrganizer.unifySplitAmounts(
            listOf(
                AiBowl(3, listOf(0 to "1结", 1 to "1粒")),
                AiBowl(4, listOf(0 to "分次加入，共 约20克")),
                AiBowl(5, listOf(2 to "少许")),
                AiBowl(6, listOf(2 to "少许")),
            ),
            listOf("1结", "1粒", "适量"),
        )
        assertEquals(
            listOf(
                AiBowl(3, listOf(0 to "分次加入，共 1结", 1 to "1粒")),
                AiBowl(4, listOf(0 to "分次加入，共 1结")),
                AiBowl(5, listOf(2 to "适量")),
                AiBowl(6, listOf(2 to "适量")),
            ),
            bowls,
        )
    }

    @Test
    fun `腌过的主料只留在腌料碗，下锅时只剩它的碗不留`() {
        val steps = listOf("牛肉加盐、淀粉腌 15 分钟", "爆香蒜，加盐", "下牛肉炒熟")
        val bowls = listOf(
            AiBowl(0, listOf(0 to "250克", 1 to "分次加入，共 5克", 2 to "8克")),
            AiBowl(1, listOf(3 to "2瓣", 1 to "")),
            AiBowl(2, listOf(0 to "")),
        )
        val result = AiBowlOrganizer.keepSingleOnce(bowls, listOf(true, false, false, false), steps)
        assertEquals(2, result.size)
        assertEquals(listOf(0, 1, 2), result[0].items.map { it.first })
        assertEquals(listOf(3, 1), result[1].items.map { it.first })
    }

    @Test
    fun `没腌过的主料留在最后一碗`() {
        val steps = listOf("土豆切丝", "土豆丝下锅，加醋")
        val bowls = listOf(AiBowl(0, listOf(0 to "1个")), AiBowl(1, listOf(0 to "", 1 to "1勺")))
        val result = AiBowlOrganizer.keepSingleOnce(bowls, listOf(true, false), steps)
        assertEquals(1, result.size)
        assertEquals(listOf(0, 1), result[0].items.map { it.first })
    }

    @Test
    fun `返回内容不对时报错`() {
        assertThrows(ImportException::class.java) { AiBowlOrganizer.parseResponse(reply("{}"), 1, 1) }
        assertThrows(ImportException::class.java) { AiBowlOrganizer.parseResponse(reply("""{"bowls":[]}"""), 1, 1) }
    }

    @Test
    fun `提示里带序号的用料和步骤`() {
        val prompt = AiBowlOrganizer.prompt(listOf("鸡腿" to "500克", "花椒" to ""), listOf("鸡腿切丁腌制", "爆香花椒"))
        assertTrue(prompt.contains("1. 鸡腿：500克\n2. 花椒\n"))
        assertTrue(prompt.contains("1. （备料）鸡腿切丁腌制\n2. 爆香花椒\n"))
    }
}
