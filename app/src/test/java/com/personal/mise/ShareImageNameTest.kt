package com.personal.mise

import com.personal.mise.share.shareFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareImageNameTest {
    @Test
    fun keepsChineseTitle() {
        assertEquals("红烧肉", shareFileName("红烧肉"))
    }

    @Test
    fun replacesUnsafeCharacters() {
        assertEquals("番茄_鸡蛋_汤", shareFileName("番茄/鸡蛋 汤"))
        assertEquals("a_b", shareFileName("a:*?\"<>|b"))
    }

    @Test
    fun trimsEdgesAndFallsBack() {
        assertEquals("可乐鸡翅", shareFileName("  可乐鸡翅. "))
        assertEquals("mise", shareFileName("///"))
        assertEquals("mise", shareFileName(""))
    }

    @Test
    fun capsLength() {
        assertEquals(40, shareFileName("菜".repeat(100)).length)
    }
}
