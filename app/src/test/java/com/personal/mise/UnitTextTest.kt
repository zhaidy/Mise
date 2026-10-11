package com.personal.mise

import com.personal.mise.util.TimeText
import com.personal.mise.util.UnitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitTextTest {
    @Test
    fun `英文缩写显示成中文单位`() {
        assertEquals("1 汤匙", UnitText.display("1 tbsp"))
        assertEquals("2 茶匙", UnitText.display("2 tsp"))
        assertEquals("1 汤匙", UnitText.display("1 Tbsp."))
        assertEquals("15克", UnitText.display("15g"))
        assertEquals("200毫升", UnitText.display("200 ml"))
        assertEquals("1千克", UnitText.display("1kg"))
        assertEquals("适量", UnitText.display("适量"))
        assertEquals("150克", UnitText.display("150克"))
    }

    @Test
    fun `单位写在数字前面的换回正常顺序`() {
        assertEquals("250克", UnitText.display("克250"))
        assertEquals("1勺 ≈18克", UnitText.display("勺1 ≈18克"))
        assertEquals("3根 ≈120克", UnitText.display("根3 ≈120克"))
        assertEquals("分次加入，共 5克", UnitText.display("分次加入，共 克5"))
        assertEquals("1小勺", UnitText.display("小勺1"))
        assertEquals("15毫升", UnitText.display("毫升15"))
        assertEquals("1.5千克", UnitText.display("千克1.5"))
        // 本来就是正常顺序的不动
        assertEquals("1勺 ≈18克", UnitText.display("1勺 ≈18克"))
        assertEquals("1千克5", UnitText.normalize("1千克5"))
        assertEquals("15毫升2", UnitText.normalize("15毫升2"))
        // 单位前面已经有数量的猜不出原意，不动
        assertEquals("一勺1", UnitText.normalize("一勺1"))
        assertEquals("约5个（估算） ≈15克", UnitText.normalize("约5个（估算） ≈15克"))
    }

    @Test
    fun `从步骤文字里猜计时`() {
        assertEquals(900, TimeText.guessSeconds("盖上锅盖焖 15 分钟"))
        assertEquals(30, TimeText.guessSeconds("煮30秒"))
        assertEquals(3600, TimeText.guessSeconds("炖1小时"))
        assertNull(TimeText.guessSeconds("翻炒均匀"))
    }

    @Test
    fun `倒计时格式`() {
        assertEquals("05:00", TimeText.format(300))
        assertEquals("1:00:05", TimeText.format(3605))
        assertEquals("5 分钟", TimeText.describe(300))
        assertEquals("1 分 30 秒", TimeText.describe(90))
    }
}
