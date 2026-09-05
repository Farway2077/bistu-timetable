package cn.edu.bistu.kebiao.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class StudyTaskKind(val label: String) { HOMEWORK("作业"), EXAM("考试"), OTHER("其他") }

data class StudyTask(
    val id: String,
    val title: String,
    val kind: StudyTaskKind,
    val courseName: String = "",
    val dueAt: LocalDateTime,
    val notes: String = "",
    val completed: Boolean = false,
) {
    fun validated(): StudyTask {
        require(title.isNotBlank()) { "请填写待办标题" }
        require(title.trim().length <= 120) { "标题不能超过 120 字" }
        require(courseName.length <= 120) { "课程名不能超过 120 字" }
        require(notes.length <= 2000) { "备注不能超过 2000 字" }
        return copy(title = title.trim(), courseName = courseName.trim(), notes = notes.trim())
    }

    fun isOverdue(now: LocalDateTime): Boolean = !completed && dueAt.isBefore(now)

    fun deadlineLabel(now: LocalDateTime): String = when {
        completed -> "已完成"
        isOverdue(now) -> "已逾期"
        dueAt.toLocalDate() == now.toLocalDate() -> "今天截止"
        dueAt.toLocalDate() == now.toLocalDate().plusDays(1) -> "明天截止"
        else -> "${ChronoUnit.DAYS.between(now.toLocalDate(), dueAt.toLocalDate())} 天后截止"
    }
}

data class CourseOccurrence(val date: LocalDate, val scheduled: ScheduledCourse)

fun searchSemesterCourses(
    semester: Semester,
    courses: List<ScheduledCourse>,
    exceptions: List<ScheduleException>,
    query: String,
): List<CourseOccurrence> {
    if (query.isBlank()) return emptyList()
    return (1..semester.totalWeeks).flatMap { week ->
        val effective = applyScheduleExceptionsForWeek(semester, courses, exceptions, week)
        searchCourses(effective, query).map { scheduled ->
            CourseOccurrence(
                semester.startDate.plusWeeks((week - 1).toLong()).plusDays((scheduled.meeting.weekday - 1).toLong()),
                scheduled,
            )
        }
    }.sortedWith(compareBy({ it.date }, { it.scheduled.meeting.startPeriod }, { it.scheduled.course.name }))
}

data class FreePeriodRange(val start: Int, val end: Int)

fun coursesOnDate(
    semester: Semester,
    courses: List<ScheduledCourse>,
    exceptions: List<ScheduleException>,
    date: LocalDate,
): List<ScheduledCourse>? {
    val days = ChronoUnit.DAYS.between(semester.startDate, date)
    if (days !in 0 until semester.totalWeeks * 7L) return null
    return applyScheduleExceptionsForWeek(semester, courses, exceptions, (days / 7 + 1).toInt())
        .filter { it.meeting.weekday == date.dayOfWeek.value }
        .sortedWith(compareBy({ it.meeting.startPeriod }, { it.course.name }))
}

fun searchCourses(courses: List<ScheduledCourse>, query: String): List<ScheduledCourse> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return courses.filter { scheduled ->
        val text = "${scheduled.course.name} ${scheduled.course.teacher} ${scheduled.meeting.room}"
        terms.all { text.contains(it, ignoreCase = true) }
    }
}

/** Supply the effective courses for one date, before search filtering. */
fun freePeriodRanges(courses: List<ScheduledCourse>): List<FreePeriodRange> {
    val occupied = courses.flatMap { it.meeting.startPeriod..it.meeting.endPeriod }.toSet()
    val ranges = mutableListOf<FreePeriodRange>()
    var start: Int? = null
    for (period in 1..15) {
        if (period <= 14 && period !in occupied) {
            if (start == null) start = period
        } else if (start != null) {
            ranges += FreePeriodRange(start, period - 1)
            start = null
        }
    }
    return ranges
}
