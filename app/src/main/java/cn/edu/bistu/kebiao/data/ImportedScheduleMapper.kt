package cn.edu.bistu.kebiao.data

import cn.edu.bistu.kebiao.data.local.CourseEntity
import cn.edu.bistu.kebiao.data.local.MeetingEntity
import cn.edu.bistu.kebiao.data.local.MeetingRow
import cn.edu.bistu.kebiao.data.local.SemesterEntity
import cn.edu.bistu.kebiao.domain.CourseSource
import cn.edu.bistu.kebiao.domain.ImportedLesson
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.keyedImportedLessons
import cn.edu.bistu.kebiao.domain.stableScheduleKey

data class ImportedScheduleEntities(
    val semester: SemesterEntity,
    val courses: List<CourseEntity>,
    val meetings: List<MeetingEntity>,
)

object ImportedScheduleMapper {
    fun map(
        schedule: ImportedSchedule,
        existingSemester: SemesterEntity?,
        existingRows: List<MeetingRow>,
        updatedAt: Long,
    ): ImportedScheduleEntities {
        val semester = SemesterEntity(
            id = schedule.semester.id,
            name = schedule.semester.name,
            startDate = (existingSemester?.startDate ?: schedule.semester.startDate.toString()),
            totalWeeks = schedule.semester.totalWeeks,
            updatedAt = updatedAt,
        )
        val previousColors = existingRows.associate { row ->
            courseIdentity(row.courseName, row.teacher) to row.colorIndex
        }
        val keyedLessons = keyedImportedLessons(schedule.semester.id, schedule.lessons)
        val previousSourceKeys = previousSourceKeys(schedule.semester.id, existingRows)
        val grouped = keyedLessons.groupBy { keyed ->
            keyed.lesson.courseName.trim() to keyed.lesson.teacher.trim()
        }
        val courseIds = grouped.keys.associateWith { (name, teacher) ->
            stableScheduleKey(schedule.semester.id, "imported-course", normalize(name), normalize(teacher))
        }
        val courses = grouped.keys
            .sortedWith(compareBy({ normalize(it.first) }, { normalize(it.second) }))
            .map { (name, teacher) ->
                val identity = courseIdentity(name, teacher)
                CourseEntity(
                    id = courseIds.getValue(name to teacher),
                    semesterId = semester.id,
                    name = name,
                    teacher = teacher,
                    colorIndex = previousColors[identity] ?: stableColorIndex(semester.id, identity),
                    source = CourseSource.IMPORTED.name,
                )
            }
        val meetings = keyedLessons.map { keyed ->
            val lesson = keyed.lesson
            val courseId = courseIds.getValue(lesson.courseName.trim() to lesson.teacher.trim())
            val sourceKey = previousSourceKeys[keyed.sourceKey] ?: keyed.sourceKey
            MeetingEntity(
                id = sourceKey,
                courseId = courseId,
                weekday = lesson.weekday,
                startPeriod = lesson.startPeriod,
                endPeriod = lesson.endPeriod,
                room = lesson.room.trim(),
                weeksCsv = lesson.weeks.sorted().joinToString(","),
                sourceKey = sourceKey,
            )
        }
        return ImportedScheduleEntities(semester, courses, meetings)
    }

    fun rowsToLessons(rows: List<MeetingRow>): List<ImportedLesson> = rows.map { row ->
        ImportedLesson(
            courseName = row.courseName,
            teacher = row.teacher,
            room = row.room,
            weekday = row.weekday,
            startPeriod = row.startPeriod,
            endPeriod = row.endPeriod,
            weeks = row.weeksCsv.split(',').mapNotNull(String::toIntOrNull).toSet(),
        )
    }

    private fun courseIdentity(name: String, teacher: String): String =
        "${normalize(name)}\u001f${normalize(teacher)}"

    private fun previousSourceKeys(
        semesterId: String,
        rows: List<MeetingRow>,
    ): Map<String, String> {
        val availableRows = rows.groupBy(::rowSignature)
            .mapValues { (_, matches) -> matches.toMutableList() }
            .toMutableMap()
        return keyedImportedLessons(semesterId, rowsToLessons(rows)).mapNotNull { keyed ->
            val signature = lessonSignature(keyed.lesson)
            val row = availableRows[signature]?.removeFirstOrNull() ?: return@mapNotNull null
            keyed.sourceKey to row.sourceKey.ifBlank { row.meetingId }
        }.toMap()
    }

    private fun rowSignature(row: MeetingRow): String = lessonSignature(
        ImportedLesson(row.courseName, row.teacher, row.room, row.weekday, row.startPeriod, row.endPeriod,
            row.weeksCsv.split(',').mapNotNull(String::toIntOrNull).toSet()),
    )

    private fun lessonSignature(lesson: ImportedLesson): String = listOf(
        normalize(lesson.courseName), normalize(lesson.teacher), normalize(lesson.room), lesson.weekday,
        lesson.startPeriod, lesson.endPeriod, lesson.weeks.sorted().joinToString(","),
    ).joinToString("\u001f")

    private fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun stableColorIndex(semesterId: String, identity: String): Int =
        stableScheduleKey(semesterId, "color", identity).takeLast(2).toInt(16) % 8
}
