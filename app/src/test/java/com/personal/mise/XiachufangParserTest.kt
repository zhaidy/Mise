package com.personal.mise

import com.personal.mise.importer.UrlExtractor
import com.personal.mise.importer.XiachufangParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用真实下厨房页面快照验证选择器（src/test/resources/xiachufang/） */
class XiachufangParserTest {

    private fun page(id: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("xiachufang/recipe_$id.html")).readText()

    @Test
    fun `解析焗饭页面`() {
        val r = XiachufangParser.parse(page("100403615"), "https://www.xiachufang.com/recipe/100403615/")

        assertEquals("番茄鸡肉浓情焗饭【两人份】", r.title)
        assertEquals("https://i2.chuimg.com/691355a488a511e6b87c0242ac110003_800w_533h.jpg", r.coverUrl)
        assertEquals("鸡胸肉" to "150克", r.ingredients.first())
        assertEquals(14, r.ingredients.size)
        assertEquals(13, r.steps.size)
        assertEquals("番茄切丁，洋葱切丝备用", r.steps.first().text)
        assertEquals("https://i2.chuimg.com/1c8851148f4711e6a9a10242ac110002_800w_533h.jpg", r.steps.first().imageUrl)
        assertTrue(r.steps.all { it.imageUrl != null })
        assertTrue(r.isComplete)
    }

    @Test
    fun `解析 MIP 页面`() {
        val r = XiachufangParser.parse(page("100371213_mip"), "https://hanwuji.xiachufang.com/recipe/100371213/")

        assertEquals("锅包肉", r.title)
        assertEquals("https://i2.chuimg.com/30be8264887811e6b87c0242ac110003_640w_640h.jpg", r.coverUrl)
        // 名称末尾多余的逗号去掉
        assertEquals("里脊肉" to "200克", r.ingredients.first())
        assertEquals("植物油" to "600ml（实用不多）", r.ingredients[4])
        assertEquals(9, r.ingredients.size)
        assertEquals(11, r.steps.size)
        assertEquals("把肉洗净并切成刀背厚簿的片，用1克盐和5ml料酒腌制二十分钟。", r.steps.first().text)
        assertEquals("https://i2.chuimg.com/394e41e48ec211e6b87c0242ac110003_640w_640h.jpg", r.steps.first().imageUrl)
        assertTrue(r.steps.all { it.imageUrl != null })
        assertTrue(r.isComplete)
    }

    @Test
    fun `MIP 页面地址`() {
        assertEquals("100371213", XiachufangParser.recipeId("https://hanwuji.xiachufang.com/recipe/100371213/"))
        assertEquals("https://hanwuji.xiachufang.com/recipe/1/", XiachufangParser.mipUrls("1").first())
    }

    @Test
    fun `用料分组标题不算用料`() {
        val r = XiachufangParser.parse(page("107508795"), "https://www.xiachufang.com/recipe/107508795/")

        assertTrue(r.isComplete)
        assertEquals("奶油奶酪" to "250克", r.ingredients.first())
        assertTrue(r.ingredients.none { it.first == "巴斯克" })
        // 最后两步只有图片没有文字，也要保留
        assertEquals(12, r.steps.size)
        assertEquals("六寸模具垫油纸，底部放几颗蓝莓", r.steps[1].text)
        assertEquals("", r.steps.last().text)
        assertTrue(r.steps.all { it.imageUrl != null })
    }

    @Test
    fun `页面结构变了时退回 JSON-LD`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@type":"Recipe","name":"焗饭","image":"https://i2.chuimg.com/abc.jpg?imageView2/1/w/280",
             "recipeIngredient":["150克鸡胸肉","适量黑胡椒粉","盐","一个半番茄"],
             "recipeInstructions":"1.番茄切丁 2.鸡肉切块，腌制15分钟 3.烤箱预热200度"}
            </script></head><body></body></html>
        """.trimIndent()
        val r = XiachufangParser.parse(html, "https://www.xiachufang.com/recipe/1/")

        assertEquals("焗饭", r.title)
        assertEquals("https://i2.chuimg.com/abc.jpg", r.coverUrl)
        assertEquals(
            listOf("鸡胸肉" to "150克", "黑胡椒粉" to "适量", "盐" to "", "番茄" to "一个半"),
            r.ingredients,
        )
        assertEquals(listOf("番茄切丁", "鸡肉切块，腌制15分钟", "烤箱预热200度"), r.steps.map { it.text })
    }

    @Test
    fun `步骤退回 JSON-LD 时按 alt 补回步骤图`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@type":"Recipe","name":"锅包肉","recipeIngredient":["300克猪里脊"],
             "recipeInstructions":"1.肉切片腌制 2.调淀粉糊 3.下锅炸"}
            </script></head><body>
            <img src="https://i2.chuimg.com/cover.jpg" alt="锅包肉的做法">
            <img data-src="https://i2.chuimg.com/s1.jpg?imageView2/2/w/300" src="data:image/gif;base64,R0" alt="锅包肉的做法 步骤1">
            <img srcset="https://i2.chuimg.com/s3.jpg?imageView2/2/w/300 1x, https://i2.chuimg.com/s3.jpg 2x" alt="锅包肉的做法 步骤3">
            </body></html>
        """.trimIndent()
        val r = XiachufangParser.parse(html, "https://www.xiachufang.com/recipe/1/")

        assertEquals(
            listOf("https://i2.chuimg.com/s1.jpg", null, "https://i2.chuimg.com/s3.jpg"),
            r.steps.map { it.imageUrl },
        )
    }

    @Test
    fun `不是菜谱的页面什么都解析不出来`() {
        val r = XiachufangParser.parse("<html><body><p>hello</p></body></html>", "https://example.com/")
        assertNull(r.title)
        assertTrue(r.ingredients.isEmpty())
        assertTrue(r.steps.isEmpty())
    }

    @Test
    fun `从分享文字里提取链接`() {
        assertEquals(
            "https://www.xiachufang.com/recipe/100403615/",
            UrlExtractor.firstUrl("【下厨房】番茄鸡肉浓情焗饭 https://www.xiachufang.com/recipe/100403615/ 快来看看"),
        )
        assertEquals("https://xcf.ink/abc", UrlExtractor.firstUrl("看看这个菜谱：https://xcf.ink/abc，很好吃"))
        assertNull(UrlExtractor.firstUrl("没有链接"))
        assertEquals("100403615", XiachufangParser.recipeId("https://m.xiachufang.com/recipe/100403615/?from=share"))
    }
}
