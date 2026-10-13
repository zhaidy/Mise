package com.personal.mise

import com.personal.mise.importer.XiachufangSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiachufangSearchTest {

    private val base = "https://www.xiachufang.com/search/?keyword=%E7%BA%A2%E7%83%A7%E8%82%89&cat=1001"

    @Test
    fun `搜索地址带关键词和页码`() {
        assertEquals("https://www.xiachufang.com/search/?keyword=%E7%BA%A2%E7%83%A7%E8%82%89&cat=1001", XiachufangSearch.searchUrl(" 红烧肉 "))
        assertTrue(XiachufangSearch.searchUrl("红烧肉", 2).endsWith("&page=2"))
    }

    @Test
    fun `解析电脑版搜索结果`() {
        val html = """
            <html><body>
            <div class="side"><a href="/recipe/999/">侧栏热门</a></div>
            <div class="normal-recipe-list"><ul class="list">
              <li><div class="recipe recipe-215-horizontal pure-g image-link display-block">
                <a href="/recipe/100403615/" target="_blank">
                  <div class="cover pure-u"><img src="data:image/gif;base64,AAAA" data-src="https://i2.chuimg.com/abc.jpg?imageView2/1/w/215/h/136/interlace/1/q/90" alt="红烧肉"></div>
                </a>
                <div class="info pure-u">
                  <p class="name"><a href="/recipe/100403615/">  秘制红烧肉 </a></p>
                  <p class="ing ellipsis">五花肉、冰糖、
                    生抽</p>
                  <p class="stats">综合评分 <span>8.0</span>（<span>1234</span> 做过）</p>
                  <p class="author"><a href="/cook/1/" class="name">某人</a></p>
                </div>
              </div></li>
              <li><div class="recipe">
                <a href="https://www.xiachufang.com/recipe/200/"><img src="//i2.chuimg.com/def.jpg" alt="外婆红烧肉"></a>
              </div></li>
            </ul></div>
            </body></html>
        """.trimIndent()
        val hits = XiachufangSearch.parse(html, base)

        assertEquals(listOf("100403615", "200"), hits.map { it.recipeId })
        val first = hits[0]
        assertEquals("秘制红烧肉", first.title)
        assertEquals("https://i2.chuimg.com/abc.jpg?imageView2/1/w/215/h/136/interlace/1/q/90", first.coverUrl)
        assertEquals("五花肉、冰糖、 生抽", first.ingredients)
        assertEquals("综合评分 8.0（1234 做过）", first.stats)
        assertEquals("https://www.xiachufang.com/recipe/100403615/", first.url)
        // 只有图的卡片用图片的 alt 当菜名
        assertEquals("外婆红烧肉", hits[1].title)
        assertEquals("https://i2.chuimg.com/def.jpg", hits[1].coverUrl)
        assertNull(hits[1].stats)
        // 1234 人做过，够进荣誉殿堂
        assertEquals("荣誉殿堂", first.honor)
        assertNull(hits[1].honor)
    }

    @Test
    fun `页面上的荣誉殿堂角标连名次一起显示，不混进菜名`() {
        val html = """
            <div class="normal-recipe-list"><ul class="list">
              <li><div class="recipe">
                <p class="name"><a href="/recipe/1/">红烧肉</a> <span class="honor-badge">荣誉殿堂 No.3</span></p>
                <p class="stats">综合评分 8.6（523 做过）</p>
              </div></li>
              <li><div class="recipe">
                <div class="cover"><a href="/recipe/2/"><img src="https://i2.chuimg.com/b.jpg" alt="可乐鸡翅"></a><span class="hall-of-fame">No.1</span></div>
                <p class="name"><a href="/recipe/2/">可乐鸡翅</a></p>
              </div></li>
              <li><div class="recipe"><p class="name"><a href="/recipe/3/">糖醋排骨</a></p><p class="stats">7.9（1.2万人做过）</p></div></li>
            </ul></div>
        """.trimIndent()
        val hits = XiachufangSearch.parse(html, base)
        assertEquals(listOf("红烧肉", "可乐鸡翅", "糖醋排骨"), hits.map { it.title })
        assertEquals(listOf("荣誉殿堂 No.3", "荣誉殿堂 No.1", "荣誉殿堂"), hits.map { it.honor })
    }

    @Test
    fun `做过人数`() {
        assertEquals(1234, XiachufangSearch.made("综合评分 8.0（1234 做过）"))
        assertEquals(12000, XiachufangSearch.made("1.2万人做过"))
        assertEquals(5235, XiachufangSearch.made("（5,235 人做过）"))
        assertNull(XiachufangSearch.made("综合评分 8.0"))
    }

    @Test
    fun `没有结果列表时看整页的菜谱链接`() {
        val html = """
            <div class="feed"><div class="item"><a href="/recipe/1/"><img src="https://i2.chuimg.com/a.jpg"><span class="name">糖醋排骨</span></a></div>
            <div class="item"><a href="/recipe/2/"><span class="name">可乐鸡翅</span></a></div></div>
        """.trimIndent()
        val hits = XiachufangSearch.parse(html, "https://m.xiachufang.com/search/?keyword=x")
        assertEquals(listOf("糖醋排骨", "可乐鸡翅"), hits.map { it.title })
        assertEquals("https://i2.chuimg.com/a.jpg", hits[0].coverUrl)
        assertNull(hits[1].coverUrl)
    }

    @Test
    fun `认出没搜到的搜索页`() {
        assertTrue(XiachufangSearch.isEmptyResultPage("""<form><input name="keyword"></form><p>没有找到相关菜谱</p>"""))
        // 验证页没有搜索框
        assertFalse(XiachufangSearch.isEmptyResultPage("""<p>没有找到</p>"""))
    }
}
