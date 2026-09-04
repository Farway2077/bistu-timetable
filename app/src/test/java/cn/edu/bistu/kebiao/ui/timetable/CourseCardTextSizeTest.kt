package cn.edu.bistu.kebiao.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCardTextSizeTest {
    @Test
    fun `stored enum names restore every supported text size`() {
        CourseCardTextSize.entries.forEach { size ->
            assertEquals(size, CourseCardTextSize.fromStorage(size.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to standard`() {
        assertEquals(CourseCardTextSize.STANDARD, CourseCardTextSize.fromStorage(null))
        assertEquals(CourseCardTextSize.STANDARD, CourseCardTextSize.fromStorage("future-size"))
    }

    @Test
    fun `text size scales stay in ascending order`() {
        val scales = CourseCardTextSize.entries.map { it.scale }

        assertEquals(scales.sorted(), scales)
        assertTrue(scales.first() < 1f)
        assertTrue(scales.last() > 1f)
    }
}
