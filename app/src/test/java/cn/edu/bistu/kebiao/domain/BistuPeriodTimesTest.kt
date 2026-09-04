package cn.edu.bistu.kebiao.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BistuPeriodTimesTest {
    @Test
    fun containsTheFourteenOfficialPeriods() {
        assertEquals(14, BistuPeriodTimes.size)
        assertEquals(PeriodTime(1, "08:00", "08:45"), BistuPeriodTimes.first())
        assertEquals(PeriodTime(10, "17:00", "17:45"), BistuPeriodTimes[9])
        assertEquals(PeriodTime(14, "21:00", "21:45"), BistuPeriodTimes.last())
    }
}
