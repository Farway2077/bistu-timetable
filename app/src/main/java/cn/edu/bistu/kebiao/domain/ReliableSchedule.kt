package cn.edu.bistu.kebiao.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CourseSource {
    IMPORTED,
    MANUAL;

    companion object {
        fun fromStorage(value: String?): CourseSource =
            entries.firstOrNull { it.name == value } ?: IMPORTED
    }
}

enum class ScheduleOverrideAction {
    REPLACE,
    HIDE,
}

enum class ScheduleExceptionKind {
    CANCEL,
    RESCHEDULE,
}

data class ScheduleLessonDraft(
    val courseName: String,
    val teacher: String = "",
    val room: String = "",
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)

data class ScheduleOverride(
    val sourceKey: String,
    val action: ScheduleOverrideAction,
    val draft: ScheduleLessonDraft? = null,
    val colorIndex: Int = 0,
) {
    init {
        require(action != ScheduleOverrideAction.REPLACE || draft != null) {
            "替换课程必须包含修正后的内容"
        }
    }
}

data class ScheduleException(
    val id: String,
    val sourceKey: String,
    val originalDate: LocalDate,
    val kind: ScheduleExceptionKind,
    val replacementDate: LocalDate? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val roomOverride: String? = null,
) {
    init {
        if (kind == ScheduleExceptionKind.RESCHEDULE) {
            require(replacementDate != null) { "调课必须选择新日期" }
            require(startPeriod != null && endPeriod != null && endPeriod >= startPeriod) {
                "调课节次不完整"
            }
        }
    }
}

data class ScheduleLessonChange(
    val before: ImportedLesson,
    val after: ImportedLesson,
)

data class ScheduleDiff(
    val added: List<ImportedLesson> = emptyList(),
    val removed: List<ImportedLesson> = emptyList(),
    val changed: List<ScheduleLessonChange> = emptyList(),
    val unchangedCount: Int = 0,
    val semesterMetadataChanged: Boolean = false,
) {
    val hasChanges: Boolean
        get() = added.isNotEmpty() || removed.isNotEmpty() || changed.isNotEmpty() || semesterMetadataChanged
}

data class KeyedImportedLesson(
    val sourceKey: String,
    val lesson: ImportedLesson,
)

object ScheduleDiffCalculator {
    fun compare(
        semesterId: String,
        previous: List<ImportedLesson>,
        incoming: List<ImportedLesson>,
        semesterMetadataChanged: Boolean = false,
    ): ScheduleDiff {
        val before = keyedImportedLessons(semesterId, previous).associateBy(KeyedImportedLesson::sourceKey)
        val after = keyedImportedLessons(semesterId, incoming).associateBy(KeyedImportedLesson::sourceKey)
        val keys = (before.keys + after.keys).sorted()
        val added = mutableListOf<ImportedLesson>()
        val removed = mutableListOf<ImportedLesson>()
        val changed = mutableListOf<ScheduleLessonChange>()
        var unchanged = 0

        keys.forEach { key ->
            val old = before[key]?.lesson
            val new = after[key]?.lesson
            when {
                old == null && new != null -> added += new
                old != null && new == null -> removed += old
                old != null && new != null && old.normalized() != new.normalized() -> {
                    changed += ScheduleLessonChange(old, new)
                }
                old != null && new != null -> unchanged += 1
            }
        }

        return ScheduleDiff(
            added = added.sortedWith(lessonComparator),
            removed = removed.sortedWith(lessonComparator),
            changed = changed.sortedWith(compareBy({ it.after.weekday }, { it.after.startPeriod }, { it.after.courseName })),
            unchangedCount = unchanged,
            semesterMetadataChanged = semesterMetadataChanged,
        )
    }
}

fun keyedImportedLessons(
    semesterId: String,
    lessons: List<ImportedLesson>,
): List<KeyedImportedLesson> = lessons
    .map(ImportedLesson::normalized)
    .groupBy { lesson ->
        listOf(
            normalizeKeyText(lesson.courseName),
            normalizeKeyText(lesson.teacher),
            lesson.weekday.toString(),
            lesson.startPeriod.toString(),
            lesson.endPeriod.toString(),
        ).joinToString("\u001f")
    }
    .toSortedMap()
    .flatMap { (groupKey, groupedLessons) ->
        groupedLessons.sortedWith(lessonComparator).mapIndexed { index, lesson ->
            KeyedImportedLesson(
                sourceKey = stableScheduleKey(semesterId, groupKey, index.toString()),
                lesson = lesson,
            )
        }
    }

fun ScheduledCourse.toDraft(): ScheduleLessonDraft = ScheduleLessonDraft(
    courseName = course.name,
    teacher = course.teacher,
    room = meeting.room,
    weekday = meeting.weekday,
    startPeriod = meeting.startPeriod,
    endPeriod = meeting.endPeriod,
    weeks = meeting.weeks,
)

fun applyScheduleOverrides(
    semester: Semester,
    courses: List<ScheduledCourse>,
    overrides: List<ScheduleOverride>,
): List<ScheduledCourse> {
    val bySourceKey = overrides.associateBy(ScheduleOverride::sourceKey)
    val matchedKeys = mutableSetOf<String>()
    val effective = courses.mapNotNull { scheduled ->
        val override = bySourceKey[scheduled.meeting.sourceKey] ?: return@mapNotNull scheduled
        matchedKeys += override.sourceKey
        when (override.action) {
            ScheduleOverrideAction.HIDE -> null
            ScheduleOverrideAction.REPLACE -> override.toScheduledCourse(semester)
        }
    }.toMutableList()

    overrides.asSequence()
        .filter { it.sourceKey !in matchedKeys && it.action == ScheduleOverrideAction.REPLACE }
        .map { it.toScheduledCourse(semester) }
        .forEach(effective::add)

    return effective.sortedWith(scheduledCourseComparator)
}

fun applyScheduleExceptionsForWeek(
    semester: Semester,
    courses: List<ScheduledCourse>,
    exceptions: List<ScheduleException>,
    week: Int,
): List<ScheduledCourse> {
    if (week !in 1..semester.totalWeeks) return emptyList()
    val weekStart = semester.startDate.plusWeeks((week - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val originalExceptions = exceptions.filter { it.originalDate in weekStart..weekEnd }
    val removedKeys = originalExceptions.map(ScheduleException::sourceKey).toSet()
    val effective = courses
        .asSequence()
        .filter { it.meeting.occursIn(week) }
        .filterNot { it.meeting.sourceKey in removedKeys }
        .toMutableList()
    val coursesByKey = courses.associateBy { it.meeting.sourceKey }

    exceptions.asSequence()
        .filter { it.kind == ScheduleExceptionKind.RESCHEDULE }
        .filter { it.replacementDate in weekStart..weekEnd }
        .mapNotNull { exception ->
            val original = coursesByKey[exception.sourceKey] ?: return@mapNotNull null
            val replacementDate = requireNotNull(exception.replacementDate)
            val daysFromStart = ChronoUnit.DAYS.between(semester.startDate, exception.originalDate)
            if (daysFromStart !in 0 until semester.totalWeeks * 7L) return@mapNotNull null
            val originalWeek = (daysFromStart / 7 + 1).toInt()
            if (exception.originalDate.dayOfWeek.value != original.meeting.weekday) return@mapNotNull null
            if (!original.meeting.occursIn(originalWeek)) return@mapNotNull null
            original.copy(
                meeting = original.meeting.copy(
                    id = "${original.meeting.id}@${exception.id}",
                    weekday = replacementDate.dayOfWeek.value,
                    startPeriod = requireNotNull(exception.startPeriod),
                    endPeriod = requireNotNull(exception.endPeriod),
                    room = exception.roomOverride?.trim()?.takeIf(String::isNotEmpty) ?: original.meeting.room,
                    weeks = setOf(week),
                ),
                isDateException = true,
                originalDate = exception.originalDate,
            )
        }
        .forEach(effective::add)

    return effective.sortedWith(scheduledCourseComparator)
}

fun stableScheduleKey(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("\u001f").toByteArray(StandardCharsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun ScheduleOverride.toScheduledCourse(semester: Semester): ScheduledCourse {
    val value = requireNotNull(draft)
    val courseId = "override-course-$sourceKey"
    return ScheduledCourse(
        semester = semester,
        course = Course(
            id = courseId,
            name = value.courseName,
            teacher = value.teacher,
            colorIndex = colorIndex,
            source = CourseSource.IMPORTED,
        ),
        meeting = Meeting(
            id = "override-meeting-$sourceKey",
            courseId = courseId,
            weekday = value.weekday,
            startPeriod = value.startPeriod,
            endPeriod = value.endPeriod,
            room = value.room,
            weeks = value.weeks,
            sourceKey = sourceKey,
        ),
        isLocalOverride = true,
    )
}

private fun ImportedLesson.normalized(): ImportedLesson = copy(
    courseName = courseName.trim().replace(Regex("\\s+"), " "),
    teacher = teacher.trim().replace(Regex("\\s+"), " "),
    room = room.trim().replace(Regex("\\s+"), " "),
    weeks = weeks.filter { it > 0 }.toSortedSet(),
)

private fun normalizeKeyText(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase()

private val lessonComparator = compareBy<ImportedLesson>(
    { it.weekday },
    { it.startPeriod },
    { it.endPeriod },
    { normalizeKeyText(it.courseName) },
    { normalizeKeyText(it.teacher) },
    { normalizeKeyText(it.room) },
    { it.weeks.sorted().joinToString(",") },
)

private val scheduledCourseComparator = compareBy<ScheduledCourse>(
    { it.meeting.weekday },
    { it.meeting.startPeriod },
    { it.meeting.endPeriod },
    { it.course.name },
)
