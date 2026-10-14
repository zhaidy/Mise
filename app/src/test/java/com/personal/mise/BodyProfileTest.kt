package com.personal.mise

import com.personal.mise.data.ActivityLevel
import com.personal.mise.data.BodyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyProfileTest {

    @Test
    fun dailyBurn() {
        // 30 岁男，175 厘米，140 斤：基础代谢 1648.75，很少动 × 1.2
        assertEquals(1979, BodyProfile(true, 30, 175, 140, ActivityLevel.SEDENTARY).dailyBurn())
        // 30 岁女，162 厘米，110 斤：基础代谢 1251.5
        assertEquals(1502, BodyProfile(false, 30, 162, 110, ActivityLevel.SEDENTARY).dailyBurn())
        assertEquals(1721, BodyProfile(false, 30, 162, 110, ActivityLevel.LIGHT).dailyBurn())
        assertEquals(2844, BodyProfile(true, 30, 175, 140, ActivityLevel.ACTIVE).dailyBurn())
    }

    @Test
    fun parse() {
        assertEquals(
            BodyProfile(true, 30, 175, 140, ActivityLevel.MODERATE),
            BodyProfile.parse(true, "30", "175", "140", ActivityLevel.MODERATE),
        )
        assertNull(BodyProfile.parse(null, "30", "175", "140", ActivityLevel.SEDENTARY))
        assertNull(BodyProfile.parse(true, "", "175", "140", ActivityLevel.SEDENTARY))
        assertNull(BodyProfile.parse(true, "30", "17", "140", ActivityLevel.SEDENTARY))
        assertNull(BodyProfile.parse(true, "30", "175", "40", ActivityLevel.SEDENTARY))
        assertNull(BodyProfile.parse(true, "8", "175", "140", ActivityLevel.SEDENTARY))
    }
}
