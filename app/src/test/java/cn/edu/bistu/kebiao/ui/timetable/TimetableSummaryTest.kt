package cn.edu.bistu.kebiao.ui.timetable

import cn.edu.bistu.kebiao.domain.Course
import cn.edu.bistu.kebiao.domain.Meeting
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSummaryTest {
    private val semester = Semester(
        id = "semester",
        name = "2026-2027学年第一学期",
        startDate = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
    )

    @Test
    fun `teaching week is null outside semester and counted from start date`() {
        assertNull(teachingWeekForDate(semester, LocalDate.of(2026, 9, 6)))
        assertEquals(1, teachingWeekForDate(semester, LocalDate.of(2026, 9, 7)))
        assertEquals(2, teachingWeekForDate(semester, LocalDate.of(2026, 9, 14)))
        assertNull(teachingWeekForDate(semester, LocalDate.of(2027, 1, 25)))
    }

    @Test
    fun `today summary keeps only matching weekday and teaching week`() {
        val mondayWeekOne = scheduledCourse("高等数学", weekday = 1, start = 1, end = 2, weeks = setOf(1))
        val tuesdayWeekOne = scheduledCourse("大学英语", weekday = 2, start = 1, end = 2, weeks = setOf(1))
        val mondayWeekTwo = scheduledCourse("大学物理", weekday = 1, start = 3, end = 4, weeks = setOf(2))

        val summary = buildTodayScheduleSummary(
            courses = listOf(mondayWeekTwo, tuesdayWeekOne, mondayWeekOne),
            semester = semester,
            now = LocalDateTime.of(2026, 9, 7, 7, 30),
        )

        assertEquals(1, summary.teachingWeek)
        assertEquals(listOf("高等数学"), summary.courses.map { it.course.name })
    }

    @Test
    fun `upcoming class reports rounded minutes until start`() {
        val first = scheduledCourse("高等数学", weekday = 1, start = 1, end = 2)
        val second = scheduledCourse("程序设计", weekday = 1, start = 3, end = 4)

        val summary = buildTodayScheduleSummary(
            courses = listOf(second, first),
            semester = semester,
            now = LocalDateTime.of(2026, 9, 7, 7, 30, 30),
        )

        assertEquals(TodayPhase.UPCOMING, summary.phase)
        assertEquals("高等数学", summary.focusCourse?.course?.name)
        assertEquals(30L, summary.minutesRemaining)
        assertEquals(listOf("高等数学", "程序设计"), summary.courses.map { it.course.name })
    }

    @Test
    fun `active class reports minutes until its final period ends`() {
        val course = scheduledCourse("高等数学", weekday = 1, start = 1, end = 2)

        val summary = buildTodayScheduleSummary(
            courses = listOf(course),
            semester = semester,
            now = LocalDateTime.of(2026, 9, 7, 8, 15),
        )

        assertEquals(TodayPhase.IN_PROGRESS, summary.phase)
        assertEquals(course, summary.focusCourse)
        assertEquals(80L, summary.minutesRemaining)
    }

    @Test
    fun `day is finished after the last course`() {
        val course = scheduledCourse("高等数学", weekday = 1, start = 1, end = 2)

        val summary = buildTodayScheduleSummary(
            courses = listOf(course),
            semester = semester,
            now = LocalDateTime.of(2026, 9, 7, 21, 50),
        )

        assertEquals(TodayPhase.FINISHED, summary.phase)
        assertNull(summary.focusCourse)
        assertNull(summary.minutesRemaining)
    }

    @Test
    fun `day without courses has an explicit empty phase`() {
        val summary = buildTodayScheduleSummary(
            courses = emptyList(),
            semester = semester,
            now = LocalDateTime.of(2026, 9, 7, 12, 0),
        )

        assertEquals(TodayPhase.NO_COURSES, summary.phase)
        assertEquals(emptyList<ScheduledCourse>(), summary.courses)
    }

    @Test
    fun `date picker conversion keeps the same calendar date in UTC`() {
        val date = LocalDate.of(2026, 8, 31)

        assertEquals(date, localDateFromDatePickerUtcMillis(date.toDatePickerUtcMillis()))
    }

    @Test
    fun `first teaching week must start on monday`() {
        assertTrue(isValidFirstWeekStart(LocalDate.of(2026, 8, 31)))
        assertFalse(isValidFirstWeekStart(LocalDate.of(2026, 9, 1)))
    }

    private fun scheduledCourse(
        name: String,
        weekday: Int,
        start: Int,
        end: Int,
        weeks: Set<Int> = setOf(1),
    ): ScheduledCourse {
        val id = "$name-$weekday-$start"
        return ScheduledCourse(
            semester = semester,
            course = Course(id = id, name = name, teacher = "任课教师", colorIndex = start),
            meeting = Meeting(
                id = "meeting-$id",
                courseId = id,
                weekday = weekday,
                startPeriod = start,
                endPeriod = end,
                room = "教室",
                weeks = weeks,
            ),
        )
    }
}
