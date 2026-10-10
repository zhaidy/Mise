package com.personal.mise

import com.personal.mise.backup.BackupManager.Companion.entryTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupEntryTest {

    @Test
    fun `接受数据库和一层图片`() {
        assertEquals("mise.db", entryTarget("mise.db"))
        assertEquals("images/3f2a-b1.jpg", entryTarget("images/3f2a-b1.jpg"))
    }

    @Test
    fun `拒绝越界路径和无关条目`() {
        listOf(
            "../mise.db",
            "images/../../shared_prefs/ai.xml",
            "images/..",
            "images/",
            "images/sub/a.jpg",
            "/data/data/x",
            "other.txt",
            "databases/mise.db",
        ).forEach { assertNull(it, entryTarget(it)) }
    }
}
