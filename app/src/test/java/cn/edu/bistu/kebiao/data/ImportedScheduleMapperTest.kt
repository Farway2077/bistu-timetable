package cn.edu.bistu.kebiao.data

import cn.edu.bistu.kebiao.data.local.MeetingRow
import cn.edu.bistu.kebiao.data.local.SemesterEntity
import cn.edu.bistu.kebiao.domain.ImportedLesson
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.Semester
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImportedScheduleMapperTest {
    private val semester = Semester(
        id = "2026-2027-1",
        name = "2026-2027学年第一学期",
        startDate = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
    )

    @Test
    fun `mapping is deterministic when extractor order changes`() {
        val monday = lesson("高等数学", weekday = 1, start = 1, end = 2)
        val wednesday = lesson("高等数学", weekday = 3, start = 3, end = 4)

        val first = map(listOf(monday, wednesday))
        val reordered = map(listOf(wednesday, monday))

        assertEquals(first.courses, reordered.courses)
        assertEquals(first.meetings, reordered.meetings)
        assertNotEquals(first.meetings[0].sourceKey, first.meetings[1].sourceKey)
    }

    @Test
    fun `existing first week and course color survive another sync`() {
        val oldSemester = SemesterEntity(
            id = semester.id,
            name = semester.name,
            startDate = "2026-08-31",
            totalWeeks = 20,
            updatedAt = 1,
        )
        val existing = listOf(
            MeetingRow(
                semesterId = semester.id,
                semesterName = semester.name,
                startDate = oldSemester.startDate,
                totalWeeks = 20,
                courseId = "old-course",
                courseName = "高等数学",
                teacher = "任课教师",
                colorIndex = 6,
                courseSource = "IMPORTED",
                meetingId = "old-meeting",
                weekday = 1,
                startPeriod = 1,
                endPeriod = 2,
                room = "教室",
                weeksCsv = "1,2",
                sourceKey = "old-meeting",
            ),
        )

        val mapped = ImportedScheduleMapper.map(
            schedule = ImportedSchedule(semester, listOf(lesson("高等数学", 1, 1, 2))),
            existingSemester = oldSemester,
            existingRows = existing,
            updatedAt = 2,
        )

        assertEquals("2026-08-31", mapped.semester.startDate)
        assertEquals(6, mapped.courses.single().colorIndex)
    }

    private fun map(lessons: List<ImportedLesson>) = ImportedScheduleMapper.map(
        schedule = ImportedSchedule(semester, lessons),
        existingSemester = null,
        existingRows = emptyList(),
        updatedAt = 1,
    )

    private fun lesson(name: String, weekday: Int, start: Int, end: Int) = ImportedLesson(
        courseName = name,
        teacher = "任课教师",
        room = "教室",
        weekday = weekday,
        startPeriod = start,
        endPeriod = end,
        weeks = setOf(1, 2),
    )
}
