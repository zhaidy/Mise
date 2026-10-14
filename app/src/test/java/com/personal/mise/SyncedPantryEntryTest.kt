package com.personal.mise

import com.personal.mise.data.Pantry
import com.personal.mise.data.PantryEntry
import com.personal.mise.sync.SyncedPantryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedPantryEntryTest {

    @Test
    fun docIdIsSafeAndStable() {
        val id = Pantry.docId("葱/姜")
        assertEquals(40, id.length)
        assertFalse('/' in id)
        assertEquals(id, Pantry.docId("葱/姜"))
        assertTrue(id != Pantry.docId("葱姜"))
    }

    @Test
    fun roundTrip() {
        val entry = PantryEntry("生抽", have = false, updatedAt = 1234L)
        assertEquals(entry, SyncedPantryEntry.fromMap(Pantry.docId("生抽"), SyncedPantryEntry.toMap(entry)))
    }

    @Test
    fun rejectsMismatchedOrBadData() {
        val data = SyncedPantryEntry.toMap(PantryEntry("生抽", true, 1L))
        assertNull(SyncedPantryEntry.fromMap(Pantry.docId("老抽"), data))
        assertNull(SyncedPantryEntry.fromMap(Pantry.docId("生抽"), mapOf("key" to "生抽", "updatedAt" to 1L)))
    }

    @Test
    fun jsonRoundTrip() {
        val entries = listOf(PantryEntry("盐", true, 1L), PantryEntry("生抽", false, 99L))
        assertEquals(entries, Pantry.fromJson(Pantry.toJson(entries)))
    }
}
