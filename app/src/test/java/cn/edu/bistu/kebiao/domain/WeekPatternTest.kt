package cn.edu.bistu.kebiao.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPatternTest {
    @Test
    fun parsesContinuousRange() {
        assertEquals((1..16).toSet(), WeekPattern.parse("1-16周"))
    }

    @Test
    fun parsesOddAndEvenWeeks() {
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), WeekPattern.parse("1-15周(单)"))
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), WeekPattern.parse("2-16周（双）"))
    }

    @Test
    fun parsesMixedRangesAndIndividuals() {
        assertEquals(setOf(1, 2, 3, 5, 8, 9), WeekPattern.parse("1-3,5,8-9周"))
    }

    @Test
    fun formatsCommonPatterns() {
        assertEquals("1-16周", WeekPattern.format((1..16).toSet()))
        assertEquals("1-15周（单周）", WeekPattern.format(setOf(1, 3, 5, 7, 9, 11, 13, 15)))
    }
}

