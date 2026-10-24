package com.personal.mise

import com.personal.mise.data.StepImages
import org.junit.Assert.assertEquals
import org.junit.Test

class StepImagesTest {
    @Test
    fun `好几张图存成一个字段再读回来不变，没有图是空字符串`() {
        val images = listOf("images/a.jpg", "images/b.jpg")
        assertEquals(images, StepImages.split(StepImages.join(images)))
        assertEquals("", StepImages.join(emptyList()))
        assertEquals(emptyList<String>(), StepImages.split(""))
    }
}
