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
