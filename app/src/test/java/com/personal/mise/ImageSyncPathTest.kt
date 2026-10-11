package com.personal.mise

import com.personal.mise.image.ImageStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSyncPathTest {

    @Test
    fun `接受 images 下一层的图片`() {
        assertTrue(ImageStore.isSyncPath("images/3f2a-b1.jpg"))
        assertTrue(ImageStore.isSyncPath("images/0b8c9e1e-5c2b-4a8e-9d3f-1a2b3c4d5e6f.jpg"))
    }

    @Test
    fun `拒绝越界路径和奇怪的名字`() {
        listOf(
            "../images/a.jpg",
            "images/../shared_prefs/kitchen.xml",
            "images/..",
            "images/.",
            "images/.hidden",
            "images/",
            "images/sub/a.jpg",
            "/data/data/x",
            "databases/mise.db",
            "images/a..jpg",
        ).forEach { assertFalse(it, ImageStore.isSyncPath(it)) }
    }
}
