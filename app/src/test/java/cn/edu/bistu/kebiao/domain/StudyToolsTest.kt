package cn.edu.bistu.kebiao.domain

import cn.edu.bistu.kebiao.data.local.toEntity
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class StudyToolsTest {
    private val start = LocalDate.of(2026, 9, 7)
    private val semester = Semester("s", "秋季", start, 2)
    private fun course(
        id: String = "math",
        from: Int = 1,
        to: Int = 2,
        weeks: Set<Int> = setOf(1, 2),
    ) = ScheduledCourse(
        semester, Course(id, "高等数学", "张老师"),
        Meeting(id, id, 1, from, to, "XXD-407", weeks),
    )

    @Test fun datesOutsideSemesterAreUnknownRatherThanFree() {
        assertNull(coursesOnDate(semester, listOf(course()), emptyList(), start.minusDays(1)))
        assertNull(coursesOnDate(semester, listOf(course()), emptyList(), start.plusWeeks(2)))
        assertEquals(emptyList<ScheduledCourse>(), coursesOnDate(semester, listOf(course()), emptyList(), start.plusDays(13)))
    }

    @Test fun exactWeekMembershipAndWeekdayAreRespected() {
        assertEquals(1, coursesOnDate(semester, listOf(course(weeks = setOf(1))), emptyList(), start)!!.size)
        assertTrue(coursesOnDate(semester, listOf(course(weeks = setOf(1))), emptyList(), start.plusWeeks(1))!!.isEmpty())
        assertTrue(coursesOnDate(semester, listOf(course()), emptyList(), start.plusDays(1))!!.isEmpty())
    }

    @Test fun cancellationFreesOnlyItsOriginalDate() {
        val exception = ScheduleException("cancel", "math", start, ScheduleExceptionKind.CANCEL)
        assertEquals(listOf(FreePeriodRange(1, 14)),
            freePeriodRanges(coursesOnDate(semester, listOf(course()), listOf(exception), start)!!))
        assertEquals(1, coursesOnDate(semester, listOf(course()), listOf(exception), start.plusWeeks(1))!!.size)
    }

    @Test fun crossWeekMoveChangesSearchRoomAndFreePeriods() {
        val target = start.plusDays(8)
        val exception = ScheduleException("move", "math", start, ScheduleExceptionKind.RESCHEDULE,
            target, 5, 6, "LAB-202")
        assertTrue(coursesOnDate(semester, listOf(course()), listOf(exception), start)!!.isEmpty())
        val moved = coursesOnDate(semester, listOf(course()), listOf(exception), target)!!
        assertEquals("LAB-202", moved.single().meeting.room)
        assertTrue(moved.single().isDateException)
        assertEquals(listOf(FreePeriodRange(1, 4), FreePeriodRange(7, 14)), freePeriodRanges(moved))
        assertEquals(1, searchCourses(moved, "lab-202").size)
        assertTrue(searchCourses(moved, "XXD-407").isEmpty())
    }

    @Test fun freePeriodsMergeOverlappingAndAdjacentClasses() {
        assertEquals(listOf(FreePeriodRange(7, 12)),
            freePeriodRanges(listOf(course(from = 1, to = 3), course("b", 3, 4), course("c", 5, 6), course("d", 13, 14))))
        assertTrue(freePeriodRanges(listOf(course(from = 1, to = 14))).isEmpty())
        assertEquals(listOf(FreePeriodRange(1, 14)), freePeriodRanges(emptyList()))
    }

    @Test fun searchCombinesFieldsCaseAndWhitespace() {
        val courses = listOf(course())
        assertEquals(courses, searchCourses(courses, " 高等  张老师  xxd-407 "))
        assertEquals(courses, searchCourses(courses, "  "))
        assertTrue(searchCourses(courses, "数学 王老师").isEmpty())
    }

    @Test fun semesterSearchUsesActualOccurrencesAndSortsByDate() {
        val exception = ScheduleException("move", "math", start, ScheduleExceptionKind.RESCHEDULE,
            start.plusDays(8), 5, 6, "LAB-202")
        val result = searchSemesterCourses(semester, listOf(course()), listOf(exception), "数学")
        assertEquals(listOf(start.plusDays(7), start.plusDays(8)), result.map { it.date })
        assertEquals(1, searchSemesterCourses(semester, listOf(course()), listOf(exception), "LAB").size)
        assertEquals(1, searchSemesterCourses(semester, listOf(course()), listOf(exception), "XXD").size)
        assertTrue(searchSemesterCourses(semester, listOf(course()), emptyList(), " ").isEmpty())
    }

    private val due = LocalDateTime.of(2026, 9, 8, 23, 59)
    private fun task() = StudyTask("id", "  实验报告  ", StudyTaskKind.HOMEWORK, "  物理  ", due, "  第二章  ")

    @Test fun deadlinesHandleExactTimeMidnightAndCompletedItems() {
        assertFalse(task().isOverdue(due))
        assertTrue(task().isOverdue(due.plusSeconds(1)))
        assertEquals("今天截止", task().deadlineLabel(due))
        assertEquals("明天截止", task().deadlineLabel(due.minusDays(1)))
        assertEquals("已逾期", task().deadlineLabel(due.plusMinutes(1)))
        assertFalse(task().copy(completed = true).isOverdue(due.plusDays(4)))
        assertEquals("已完成", task().copy(completed = true).deadlineLabel(due.plusDays(4)))
    }

    @Test fun taskValidationTrimsAndPreservesOptionalFields() {
        val value = task().validated()
        assertEquals("实验报告", value.title)
        assertEquals("物理", value.courseName)
        assertEquals("第二章", value.notes)
        assertEquals("", task().copy(courseName = "", notes = "").validated().courseName)
    }

    @Test fun taskValidationRejectsBlankAndOversizedValues() {
        listOf(task().copy(title = "  "), task().copy(title = "a".repeat(121)),
            task().copy(courseName = "a".repeat(121)), task().copy(notes = "a".repeat(2001))).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validated() }
        }
    }

    @Test fun persistenceRoundTripRetainsAllKindsAndCompletion() {
        StudyTaskKind.entries.forEach { kind ->
            val value = task().copy(kind = kind, completed = true).validated()
            assertEquals(value, value.toEntity().toDomain())
        }
    }
}
