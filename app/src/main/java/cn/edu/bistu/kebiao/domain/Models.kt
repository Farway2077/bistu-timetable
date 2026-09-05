package cn.edu.bistu.kebiao.domain

import java.time.LocalDate

data class Semester(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int = 20,
)

data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val colorIndex: Int = 0,
    val source: CourseSource = CourseSource.IMPORTED,
)

data class Meeting(
    val id: String,
    val courseId: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val room: String = "",
    val weeks: Set<Int>,
    val sourceKey: String = id,
) {
    init {
        require(weekday in 1..7) { "weekday must be in 1..7" }
        require(startPeriod in 1..20) { "startPeriod must be positive" }
        require(endPeriod >= startPeriod) { "endPeriod must not precede startPeriod" }
    }

    fun occursIn(week: Int): Boolean = week in weeks
}

data class ScheduledCourse(
    val semester: Semester,
    val course: Course,
    val meeting: Meeting,
    val isLocalOverride: Boolean = false,
    val isDateException: Boolean = false,
    val originalDate: LocalDate? = null,
)

data class ImportedLesson(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)

data class ImportedSchedule(
    val semester: Semester,
    val lessons: List<ImportedLesson>,
    val warnings: List<String> = emptyList(),
)

