package cn.edu.bistu.kebiao.data

import cn.edu.bistu.kebiao.data.local.MeetingRow
import cn.edu.bistu.kebiao.data.local.SemesterEntity
import cn.edu.bistu.kebiao.domain.ImportedLesson
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.Semester
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySourceKeyTest {
    @Test
    fun `first version two sync keeps legacy source key for matching lesson`() {
        val semester = Semester("term", "学期", LocalDate.of(2026, 9, 7), 20)
        val lesson = ImportedLesson("高等数学", "张老师", "教一101", 1, 1, 2, setOf(1, 2))
        val existingSemester = SemesterEntity("term", "学期", "2026-09-07", 20, 1)
        val existingRows = listOf(
            MeetingRow(
                semesterId = "term",
                semesterName = "学期",
                startDate = "2026-09-07",
                totalWeeks = 20,
                courseId = "legacy-course",
                courseName = lesson.courseName,
                teacher = lesson.teacher,
                colorIndex = 4,
                courseSource = "IMPORTED",
                meetingId = "legacy-meeting-id",
                weekday = lesson.weekday,
                startPeriod = lesson.startPeriod,
                endPeriod = lesson.endPeriod,
                room = lesson.room,
                weeksCsv = "1,2",
                sourceKey = "legacy-meeting-id",
            ),
        )

        val mapped = ImportedScheduleMapper.map(
            ImportedSchedule(semester, listOf(lesson)),
            existingSemester,
            existingRows,
            updatedAt = 2,
        )

        assertEquals("legacy-meeting-id", mapped.meetings.single().sourceKey)
        assertEquals("legacy-meeting-id", mapped.meetings.single().id)
    }
}
