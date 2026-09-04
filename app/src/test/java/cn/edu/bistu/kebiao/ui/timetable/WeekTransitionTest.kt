package cn.edu.bistu.kebiao.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTransitionTest {
    @Test
    fun `week maps to zero based pager index`() {
        assertEquals(0, pageIndexForWeek(1, 20))
        assertEquals(11, pageIndexForWeek(12, 20))
    }

    @Test
    fun `pager index maps back to teaching week`() {
        assertEquals(1, weekForPageIndex(0, 20))
        assertEquals(12, weekForPageIndex(11, 20))
    }

    @Test
    fun `week and page values are clamped to semester bounds`() {
        assertEquals(0, pageIndexForWeek(-3, 20))
        assertEquals(19, pageIndexForWeek(25, 20))
        assertEquals(1, weekForPageIndex(-2, 20))
        assertEquals(20, weekForPageIndex(24, 20))
    }

    @Test
    fun `invalid semester length still keeps a usable first page`() {
        assertEquals(0, pageIndexForWeek(8, 0))
        assertEquals(1, weekForPageIndex(8, 0))
    }
}
