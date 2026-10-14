package com.personal.mise

import com.personal.mise.data.ActivityLevel
import com.personal.mise.data.BodyProfile
import com.personal.mise.data.Diner
import com.personal.mise.sync.SyncedDiners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedDinersTest {

    @Test
    fun roundTrip() {
        val synced = SyncedDiners(
            listOf(
                Diner("小明", 2270, body = BodyProfile(true, 30, 175, 140, ActivityLevel.LIGHT)),
                Diner("小红", 1700, eating = false),
            ),
            updatedAt = 1234L,
        )
        assertEquals(synced, SyncedDiners.fromMap("diners", synced.toMap()))
    }

    @Test
    fun rejectsOtherDocsAndBadData() {
        val data = SyncedDiners(listOf(Diner("小明", 2000)), 1L).toMap()
        assertNull(SyncedDiners.fromMap("other", data))
        assertNull(SyncedDiners.fromMap("diners", mapOf("diners" to "[]", "updatedAt" to 1L)))
        assertNull(SyncedDiners.fromMap("diners", mapOf("diners" to "not json", "updatedAt" to 1L)))
        assertNull(SyncedDiners.fromMap("diners", mapOf("updatedAt" to 1L)))
    }
}
