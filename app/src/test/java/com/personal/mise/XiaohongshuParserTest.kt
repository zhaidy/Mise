package com.personal.mise

import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.importer.XiaohongshuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小红书页面结构按网页版 window.__INITIAL_STATE__ 简化（src/test/resources/xiaohongshu/） */
class XiaohongshuParserTest {

    private fun page(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("xiaohongshu/$name.html")).readText()

    @Test
    fun `识别小红书链接`() {
        assertTrue(XiaohongshuParser.isNoteUrl("http://xhslink.com/a/AbCdEf"))
        assertTrue(XiaohongshuParser.isNoteUrl("https://xhslink.cn/o/32yv0Wq7FMZ"))
        assertTrue(XiaohongshuParser.isNoteUrl("https://www.xiaohongshu.com/explore/66aabbccdd?xsec_token=x"))
        assertTrue(XiaohongshuParser.isNoteUrl("https://www.xiaohongshu.com/discovery/item/66aabbccdd"))
        assertFalse(XiaohongshuParser.isNoteUrl("https://www.xiachufang.com/recipe/100403615/"))
        assertFalse(XiaohongshuParser.isNoteUrl("https://example.com/?from=xiaohongshu.com"))
    }

    @Test
    fun `小红书分享文字按链接导入`() {
        val shared = "一次吃三碗的可乐鸡翅，零失败做法分享给大家，真的超级好吃，一定要试试，收藏起来慢慢看 " +
            "http://xhslink.com/a/AbCdEf，复制本条信息，打开【小红书】App查看精彩内容！"
        assertTrue(UrlExtractor.isLinkShare(shared))
        assertEquals("http://xhslink.com/a/AbCdEf", UrlExtractor.firstUrl(shared))
    }

    @Test
    fun `xhslink cn 短链的分享文字`() {
        val shared = "今年出炉的第3款面包｜第一次做港式菠萝包 慢慢解锁我... https://xhslink.cn/o/32yv0Wq7FMZ 把文本复制下来，打开【小红书】即可查看。"
        assertEquals("https://xhslink.cn/o/32yv0Wq7FMZ", UrlExtractor.firstUrl(shared))
        assertTrue(XiaohongshuParser.isNoteUrl(UrlExtractor.firstUrl(shared)!!))
    }

    @Test
    fun `从 INITIAL_STATE 取出标题、正文和配图`() {
        val note = XiaohongshuParser.parse(page("note_desktop"), "https://www.xiaohongshu.com/discovery/item/66aabbccdd")

        assertEquals("绝了！！一次吃三碗的可乐鸡翅😋", note.title)
        assertTrue(note.desc.startsWith("真的零失败\n鸡翅10个 可乐330ml"))
        assertTrue(note.desc.contains("#可乐鸡翅 "))
        assertFalse(note.desc.contains("[话题]"))
        assertFalse(note.desc.contains("[哭惹R]"))
        assertEquals(
            listOf(
                "https://sns-webpic-qc.xhscdn.com/1/cover!nd_dft_wlteh_webp_3",
                "https://sns-webpic-qc.xhscdn.com/2/dft",
            ),
            note.imageUrls,
        )
    }

    @Test
    fun `打不开笔记时是空的`() {
        val note = XiaohongshuParser.parse(page("login_wall"), "https://www.xiaohongshu.com/website-login/error")
        assertEquals("", note.desc)
        assertTrue(note.imageUrls.isEmpty())
        assertTrue(note.isEmpty)
    }

    @Test
    fun `没有页面数据时退回 meta 标签`() {
        val html = """<html><head><meta property="og:title" content="葱油拌面 - 小红书">
            <meta name="description" content="小葱切段，小火熬油">
            <meta property="og:image" content="//sns-img.xhscdn.com/a"></head></html>"""
        val note = XiaohongshuParser.parse(html, "https://www.xiaohongshu.com/explore/1")
        assertEquals("葱油拌面", note.title)
        assertEquals("小葱切段，小火熬油", note.desc)
        assertEquals(listOf("https://sns-img.xhscdn.com/a"), note.imageUrls)
    }

    @Test
    fun `发给 AI 的文字带上标题`() {
        assertEquals("标题：可乐鸡翅\n鸡翅10个", AiRecipeOrganizer.xhsText(" 可乐鸡翅 ", "鸡翅10个"))
        assertEquals("鸡翅10个", AiRecipeOrganizer.xhsText("", "鸡翅10个"))
    }
}
