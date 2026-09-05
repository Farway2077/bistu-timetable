package cn.edu.bistu.kebiao.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSnapshotCodecTest {
    @Test
    fun `snapshot round trip preserves exact imported baseline`() {
        val semester = SemesterEntity(
            id = "2026-2027-1",
            name = "2026-2027学年第一学期",
            startDate = "2026-08-31",
            totalWeeks = 20,
            updatedAt = 100,
        )
        val row = MeetingRow(
            semesterId = semester.id,
            semesterName = semester.name,
            startDate = semester.startDate,
            totalWeeks = semester.totalWeeks,
            courseId = "course-1",
            courseName = "高等数学",
            teacher = "张老师",
            colorIndex = 3,
            courseSource = "IMPORTED",
            meetingId = "meeting-1",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2,
            room = "XXD-407",
            weeksCsv = "1,2,3",
            sourceKey = "source-1",
        )

        val snapshot = ImportSnapshotCodec.encode(semester, listOf(row), createdAt = 200)
        val decoded = ImportSnapshotCodec.decode(snapshot)

        assertEquals(semester, decoded.semester)
        assertEquals(
            CourseEntity("course-1", semester.id, "高等数学", "张老师", 3, "IMPORTED"),
            decoded.courses.single(),
        )
        assertEquals(
            MeetingEntity("meeting-1", "course-1", 1, 1, 2, "XXD-407", "1,2,3", "source-1"),
            decoded.meetings.single(),
        )
        assertEquals(200, snapshot.createdAt)
    }
}
