package cn.edu.bistu.kebiao.data

import cn.edu.bistu.kebiao.data.local.CourseEntity
import cn.edu.bistu.kebiao.data.local.MeetingEntity
import cn.edu.bistu.kebiao.data.local.MeetingRow
import cn.edu.bistu.kebiao.data.local.ScheduleDao
import cn.edu.bistu.kebiao.data.local.SemesterEntity
import cn.edu.bistu.kebiao.domain.Course
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.Meeting
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduleRepository(private val dao: ScheduleDao) {
    val activeSemester: Flow<Semester?> = dao.observeLatestSemester().map { entity ->
        entity?.let {
            Semester(
                id = it.id,
                name = it.name,
                startDate = LocalDate.parse(it.startDate),
                totalWeeks = it.totalWeeks,
            )
        }
    }

    val scheduledCourses: Flow<List<ScheduledCourse>> =
        dao.observeLatestMeetingRows().map { rows -> rows.map { it.toDomain() } }

    suspend fun replaceSchedule(schedule: ImportedSchedule) {
        require(schedule.lessons.isNotEmpty()) { "没有识别到可导入的课程" }
        val semester = SemesterEntity(
            id = schedule.semester.id,
            name = schedule.semester.name,
            startDate = schedule.semester.startDate.toString(),
            totalWeeks = schedule.semester.totalWeeks,
            updatedAt = System.currentTimeMillis(),
        )
        val grouped = schedule.lessons.groupBy { it.courseName.trim() to it.teacher.trim() }
        val courseIds = grouped.keys.associateWith { (name, teacher) ->
            stableId(schedule.semester.id, name, teacher)
        }
        val courses = grouped.keys.mapIndexed { index, (name, teacher) ->
            CourseEntity(
                id = courseIds.getValue(name to teacher),
                semesterId = semester.id,
                name = name,
                teacher = teacher,
                colorIndex = index % 8,
            )
        }
        val meetings = schedule.lessons.mapIndexed { index, lesson ->
            val courseId = courseIds.getValue(lesson.courseName.trim() to lesson.teacher.trim())
            MeetingEntity(
                id = stableId(
                    courseId,
                    lesson.weekday.toString(),
                    lesson.startPeriod.toString(),
                    lesson.endPeriod.toString(),
                    lesson.room,
                    index.toString(),
                ),
                courseId = courseId,
                weekday = lesson.weekday,
                startPeriod = lesson.startPeriod,
                endPeriod = lesson.endPeriod,
                room = lesson.room.trim(),
                weeksCsv = lesson.weeks.sorted().joinToString(","),
            )
        }
        dao.replaceSemester(semester, courses, meetings)
    }

    suspend fun updateSemesterStartDate(semesterId: String, startDate: LocalDate) {
        require(startDate.dayOfWeek == DayOfWeek.MONDAY) { "第一周第一天必须是星期一" }
        check(dao.updateSemesterStartDate(semesterId, startDate.toString()) == 1) {
            "当前学期不存在，请重新导入课表"
        }
    }

    private fun MeetingRow.toDomain(): ScheduledCourse = ScheduledCourse(
        semester = Semester(semesterId, semesterName, LocalDate.parse(startDate), totalWeeks),
        course = Course(courseId, courseName, teacher, colorIndex),
        meeting = Meeting(
            id = meetingId,
            courseId = courseId,
            weekday = weekday,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            room = room,
            weeks = weeksCsv.split(',').mapNotNull(String::toIntOrNull).toSet(),
        ),
    )

    private fun stableId(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\u001f").toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
