package com.personal.mise

import com.personal.mise.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateChangesTest {
    @Test
    fun parsesMarkdownList() {
        val body = "- 步骤分成备料和开火两段\r\n- 搜索可以按用料搜\n\n"
        assertEquals(listOf("步骤分成备料和开火两段", "搜索可以按用料搜"), UpdateChecker.parseChanges(body))
    }

    @Test
    fun emptyBodyHasNoChanges() {
        assertEquals(emptyList<String>(), UpdateChecker.parseChanges(""))
    }
}
