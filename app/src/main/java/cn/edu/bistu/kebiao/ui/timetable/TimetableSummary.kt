package cn.edu.bistu.kebiao.ui.timetable

import cn.edu.bistu.kebiao.domain.BistuPeriodTimes
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class TodayPhase {
    NO_COURSES,
    UPCOMING,
    IN_PROGRESS,
    FINISHED,
}

data class TodayScheduleSummary(
    val date: LocalDate,
    val teachingWeek: Int?,
    val courses: List<ScheduledCourse>,
    val phase: TodayPhase,
    val focusCourse: ScheduledCourse? = null,
    val minutesRemaining: Long? = null,
)

fun teachingWeekForDate(semester: Semester, date: LocalDate): Int? {
    val daysFromStart = ChronoUnit.DAYS.between(semester.startDate, date)
    if (daysFromStart < 0) return null

    val week = (daysFromStart / 7 + 1).toInt()
    return week.takeIf { it in 1..semester.totalWeeks }
}

fun isValidFirstWeekStart(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY

fun LocalDate.toDatePickerUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun localDateFromDatePickerUtcMillis(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

fun buildTodayScheduleSummary(
    courses: List<ScheduledCourse>,
    semester: Semester,
    now: LocalDateTime,
): TodayScheduleSummary {
    val teachingWeek = teachingWeekForDate(semester, now.toLocalDate())
    val todayCourses = teachingWeek
        ?.let { week ->
            courses
                .asSequence()
                .filter { it.meeting.weekday == now.dayOfWeek.value }
                .filter { it.meeting.occursIn(week) }
                .sortedWith(compareBy({ it.meeting.startPeriod }, { it.course.name }))
                .toList()
        }
        .orEmpty()

    if (todayCourses.isEmpty()) {
        return TodayScheduleSummary(
            date = now.toLocalDate(),
            teachingWeek = teachingWeek,
            courses = emptyList(),
            phase = TodayPhase.NO_COURSES,
        )
    }

    val timedCourses = todayCourses.mapNotNull { scheduled ->
        val start = BistuPeriodTimes.getOrNull(scheduled.meeting.startPeriod - 1)
            ?.startsAt
            ?.let(LocalTime::parse)
        val end = BistuPeriodTimes.getOrNull(scheduled.meeting.endPeriod - 1)
            ?.endsAt
            ?.let(LocalTime::parse)
        if (start == null || end == null) null else TimedCourse(scheduled, start, end)
    }
    val currentTime = now.toLocalTime()
    val active = timedCourses.firstOrNull { currentTime >= it.start && currentTime < it.end }
    if (active != null) {
        return TodayScheduleSummary(
            date = now.toLocalDate(),
            teachingWeek = teachingWeek,
            courses = todayCourses,
            phase = TodayPhase.IN_PROGRESS,
            focusCourse = active.course,
            minutesRemaining = roundedUpMinutes(Duration.between(currentTime, active.end)),
        )
    }

    val upcoming = timedCourses.firstOrNull { currentTime < it.start }
    if (upcoming != null) {
        return TodayScheduleSummary(
            date = now.toLocalDate(),
            teachingWeek = teachingWeek,
            courses = todayCourses,
            phase = TodayPhase.UPCOMING,
            focusCourse = upcoming.course,
            minutesRemaining = roundedUpMinutes(Duration.between(currentTime, upcoming.start)),
        )
    }

    return TodayScheduleSummary(
        date = now.toLocalDate(),
        teachingWeek = teachingWeek,
        courses = todayCourses,
        phase = TodayPhase.FINISHED,
    )
}

private data class TimedCourse(
    val course: ScheduledCourse,
    val start: LocalTime,
    val end: LocalTime,
)

private fun roundedUpMinutes(duration: Duration): Long {
    val seconds = duration.seconds.coerceAtLeast(0)
    return (seconds + 59) / 60
}
