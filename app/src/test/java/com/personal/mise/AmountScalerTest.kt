package com.personal.mise

import com.personal.mise.util.AmountScaler
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountScalerTest {
    @Test
    fun `数字按倍率换算`() {
        assertEquals("300克", AmountScaler.scale("150克", 2.0))
        assertEquals("75g", AmountScaler.scale("150g", 0.5))
        assertEquals("3 tbsp", AmountScaler.scale("1.5 tbsp", 2.0))
        assertEquals("4-6个", AmountScaler.scale("2-3个", 2.0))
        assertEquals("1 tsp", AmountScaler.scale("1/2 tsp", 2.0))
        assertEquals("3 cup", AmountScaler.scale("1 1/2 cup", 2.0))
        assertEquals("1.5勺", AmountScaler.scale("½勺", 3.0))
        assertEquals("2.25勺", AmountScaler.scale("1½勺", 1.5))
        assertEquals("8", AmountScaler.scale("12/3", 2.0))
    }

    @Test
    fun `中文数量词换算`() {
        assertEquals("1个", AmountScaler.scale("半个", 2.0))
        assertEquals("4勺", AmountScaler.scale("两勺", 2.0))
        assertEquals("1.5小把", AmountScaler.scale("一小把", 1.5))
    }

    @Test
    fun `没有数量的不动`() {
        assertEquals("适量", AmountScaler.scale("适量", 2.0))
        assertEquals("少许", AmountScaler.scale("少许", 2.0))
        assertEquals("一点", AmountScaler.scale("一点", 2.0))
        assertEquals("十二个", AmountScaler.scale("十二个", 2.0))
        assertEquals("150克", AmountScaler.scale("150克", 1.0))
    }

    @Test
    fun `结果格式`() {
        assertEquals("0.33", AmountScaler.format(1.0 / 3))
        assertEquals("13", AmountScaler.format(12.5))
        assertEquals("×1.5", AmountScaler.label(1.5))
        assertEquals("×2", AmountScaler.label(2.0))
    }
}
