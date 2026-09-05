package cn.edu.bistu.kebiao.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliableScheduleTest {
    private val semester = Semester(
        id = "2026-2027-1",
        name = "2026-2027学年第一学期",
        startDate = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
    )

    @Test
    fun `diff separates added removed changed and unchanged lessons`() {
        val unchanged = lesson("高等数学", weekday = 1, start = 1, end = 2, room = "教一101")
        val changedBefore = lesson("大学物理", weekday = 2, start = 3, end = 4, room = "实验楼201")
        val changedAfter = changedBefore.copy(room = "实验楼202")
        val removed = lesson("大学英语", weekday = 3, start = 5, end = 6)
        val added = lesson("程序设计", weekday = 4, start = 7, end = 8)

        val diff = ScheduleDiffCalculator.compare(
            semesterId = semester.id,
            previous = listOf(unchanged, changedBefore, removed),
            incoming = listOf(unchanged, changedAfter, added),
        )

        assertEquals(listOf("程序设计"), diff.added.map { it.courseName })
        assertEquals(listOf("大学英语"), diff.removed.map { it.courseName })
        assertEquals(1, diff.changed.size)
        assertEquals("实验楼201", diff.changed.single().before.room)
        assertEquals("实验楼202", diff.changed.single().after.room)
        assertEquals(1, diff.unchangedCount)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun `diff treats reordered input as unchanged`() {
        val first = lesson("高等数学", weekday = 1, start = 1, end = 2)
        val second = lesson("高等数学", weekday = 3, start = 3, end = 4)

        val diff = ScheduleDiffCalculator.compare(
            semesterId = semester.id,
            previous = listOf(first, second),
            incoming = listOf(second, first),
        )

        assertFalse(diff.hasChanges)
        assertEquals(2, diff.unchangedCount)
    }

    @Test
    fun `removing one same-day slot does not turn another slot into a change`() {
        val early = lesson("高等数学", weekday = 1, start = 1, end = 2)
        val late = lesson("高等数学", weekday = 1, start = 5, end = 6)

        val diff = ScheduleDiffCalculator.compare(
            semesterId = semester.id,
            previous = listOf(early, late),
            incoming = listOf(late),
        )

        assertEquals(listOf(early), diff.removed)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.changed.isEmpty())
        assertEquals(1, diff.unchangedCount)
    }

    @Test
    fun `replace and hide overrides win over imported baseline while manual course remains`() {
        val imported = scheduled("高等数学", sourceKey = "imported-key", source = CourseSource.IMPORTED)
        val manual = scheduled("自习", sourceKey = "manual-key", source = CourseSource.MANUAL)
        val replacement = ScheduleOverride(
            sourceKey = imported.meeting.sourceKey,
            action = ScheduleOverrideAction.REPLACE,
            draft = imported.toDraft().copy(room = "新教室", startPeriod = 5, endPeriod = 6),
            colorIndex = imported.course.colorIndex,
        )

        val replaced = applyScheduleOverrides(semester, listOf(imported, manual), listOf(replacement))

        assertEquals(2, replaced.size)
        assertEquals("新教室", replaced.single { it.meeting.sourceKey == "imported-key" }.meeting.room)
        assertTrue(replaced.single { it.meeting.sourceKey == "imported-key" }.isLocalOverride)
        assertEquals(CourseSource.MANUAL, replaced.single { it.meeting.sourceKey == "manual-key" }.course.source)

        val hidden = replacement.copy(action = ScheduleOverrideAction.HIDE)
        val afterHide = applyScheduleOverrides(semester, listOf(imported, manual), listOf(hidden))
        assertEquals(listOf("自习"), afterHide.map { it.course.name })
    }

    @Test
    fun `single date cancellation removes only the matching week occurrence`() {
        val course = scheduled("高等数学", sourceKey = "math", weeks = setOf(1, 2))
        val exception = ScheduleException(
            id = "cancel-math-week-1",
            sourceKey = "math",
            originalDate = LocalDate.of(2026, 9, 7),
            kind = ScheduleExceptionKind.CANCEL,
        )

        assertTrue(applyScheduleExceptionsForWeek(semester, listOf(course), listOf(exception), 1).isEmpty())
        assertEquals(1, applyScheduleExceptionsForWeek(semester, listOf(course), listOf(exception), 2).size)
    }

    @Test
    fun `reschedule moves a single occurrence within the same week`() {
        val course = scheduled("高等数学", sourceKey = "math", weeks = setOf(1, 2))
        val exception = ScheduleException(
            id = "move-math-week-1",
            sourceKey = "math",
            originalDate = LocalDate.of(2026, 9, 7),
            kind = ScheduleExceptionKind.RESCHEDULE,
            replacementDate = LocalDate.of(2026, 9, 9),
            startPeriod = 7,
            endPeriod = 8,
            roomOverride = "教二302",
        )

        val weekOne = applyScheduleExceptionsForWeek(semester, listOf(course), listOf(exception), 1)

        assertEquals(1, weekOne.size)
        assertEquals(3, weekOne.single().meeting.weekday)
        assertEquals(7, weekOne.single().meeting.startPeriod)
        assertEquals("教二302", weekOne.single().meeting.room)
        assertTrue(weekOne.single().isDateException)
    }

    @Test
    fun `reschedule can move an occurrence into another teaching week`() {
        val course = scheduled("高等数学", sourceKey = "math", weeks = setOf(1, 2))
        val exception = ScheduleException(
            id = "move-math-week-1-to-2",
            sourceKey = "math",
            originalDate = LocalDate.of(2026, 9, 7),
            kind = ScheduleExceptionKind.RESCHEDULE,
            replacementDate = LocalDate.of(2026, 9, 15),
            startPeriod = 5,
            endPeriod = 6,
        )

        assertTrue(applyScheduleExceptionsForWeek(semester, listOf(course), listOf(exception), 1).isEmpty())
        val weekTwo = applyScheduleExceptionsForWeek(semester, listOf(course), listOf(exception), 2)
        assertEquals(2, weekTwo.size)
        assertEquals(listOf(1, 2), weekTwo.map { it.meeting.weekday }.sorted())
        assertEquals(1, weekTwo.count { it.isDateException })
    }

    @Test
    fun `stale reschedule does not recreate an occurrence removed by a later sync`() {
        val course = scheduled("高等数学", sourceKey = "math", weeks = setOf(2))
        val staleException = ScheduleException(
            id = "move-removed-week-1-to-2",
            sourceKey = "math",
            originalDate = LocalDate.of(2026, 9, 7),
            kind = ScheduleExceptionKind.RESCHEDULE,
            replacementDate = LocalDate.of(2026, 9, 15),
            startPeriod = 5,
            endPeriod = 6,
        )

        val weekTwo = applyScheduleExceptionsForWeek(
            semester,
            listOf(course),
            listOf(staleException),
            2,
        )

        assertEquals(1, weekTwo.size)
        assertFalse(weekTwo.single().isDateException)
    }

    private fun lesson(
        name: String,
        weekday: Int,
        start: Int,
        end: Int,
        room: String = "教室",
    ) = ImportedLesson(
        courseName = name,
        teacher = "任课教师",
        room = room,
        weekday = weekday,
        startPeriod = start,
        endPeriod = end,
        weeks = (1..16).toSet(),
    )

    private fun scheduled(
        name: String,
        sourceKey: String,
        source: CourseSource = CourseSource.IMPORTED,
        weeks: Set<Int> = setOf(1),
    ): ScheduledCourse = ScheduledCourse(
        semester = semester,
        course = Course(
            id = "course-$sourceKey",
            name = name,
            teacher = "任课教师",
            colorIndex = 2,
            source = source,
        ),
        meeting = Meeting(
            id = "meeting-$sourceKey",
            courseId = "course-$sourceKey",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2,
            room = "原教室",
            weeks = weeks,
            sourceKey = sourceKey,
        ),
    )
}
