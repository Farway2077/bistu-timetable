package cn.edu.bistu.kebiao.data

import cn.edu.bistu.kebiao.data.local.CourseEntity
import cn.edu.bistu.kebiao.data.local.MeetingEntity
import cn.edu.bistu.kebiao.data.local.MeetingRow
import cn.edu.bistu.kebiao.data.local.ScheduleDao
import cn.edu.bistu.kebiao.data.local.ScheduleExceptionEntity
import cn.edu.bistu.kebiao.data.local.ScheduleOverrideEntity
import cn.edu.bistu.kebiao.data.local.SemesterEntity
import cn.edu.bistu.kebiao.data.local.toEntity
import cn.edu.bistu.kebiao.domain.StudyTask
import cn.edu.bistu.kebiao.domain.Course
import cn.edu.bistu.kebiao.domain.CourseSource
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.Meeting
import cn.edu.bistu.kebiao.domain.ScheduleDiff
import cn.edu.bistu.kebiao.domain.ScheduleDiffCalculator
import cn.edu.bistu.kebiao.domain.ScheduleException
import cn.edu.bistu.kebiao.domain.ScheduleExceptionKind
import cn.edu.bistu.kebiao.domain.ScheduleLessonDraft
import cn.edu.bistu.kebiao.domain.ScheduleOverride
import cn.edu.bistu.kebiao.domain.ScheduleOverrideAction
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import cn.edu.bistu.kebiao.domain.applyScheduleOverrides
import cn.edu.bistu.kebiao.domain.stableScheduleKey
import cn.edu.bistu.kebiao.domain.toDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScheduleRepository(private val dao: ScheduleDao) {
    val studyTasks: Flow<List<StudyTask>> = dao.observeStudyTasks().map { rows -> rows.map { it.toDomain() } }

    suspend fun saveStudyTask(task: StudyTask) = dao.upsertStudyTask(task.validated().toEntity())

    suspend fun setStudyTaskCompleted(id: String, completed: Boolean) = dao.setStudyTaskCompleted(id, completed)

    suspend fun deleteStudyTask(id: String) = dao.deleteStudyTask(id)

    private val writeMutex = Mutex()
    private val semesterEntities = dao.observeLatestSemester()
    private val meetingRows = dao.observeLatestMeetingRows()
    private val overrideEntities = dao.observeLatestOverrides()

    val activeSemester: Flow<Semester?> = semesterEntities.map { it?.toDomain() }

    val scheduleExceptions: Flow<List<ScheduleException>> =
        dao.observeLatestExceptions().map { entities -> entities.map { it.toDomain() } }

    val canUndoLastImport: Flow<Boolean> = dao.observeCanUndoLatestImport()

    val scheduleOverrides: Flow<List<ScheduleOverride>> =
        overrideEntities.map { entities -> entities.map { it.toDomain() } }

    val scheduledCourses: Flow<List<ScheduledCourse>> = combine(
        semesterEntities,
        meetingRows,
        overrideEntities,
    ) { semesterEntity, rows, overrides ->
        val semester = semesterEntity?.toDomain() ?: return@combine emptyList()
        applyScheduleOverrides(
            semester = semester,
            courses = rows.map { it.toDomain() },
            overrides = overrides.map { it.toDomain() },
        )
    }

    suspend fun previewImport(schedule: ImportedSchedule): ScheduleDiff {
        val existingSemester = dao.getSemester(schedule.semester.id)
        val existingRows = dao.getImportedMeetingRows(schedule.semester.id)
        return compareImport(schedule, existingSemester, existingRows)
    }

    suspend fun applyImport(schedule: ImportedSchedule): ScheduleDiff = writeMutex.withLock {
        require(schedule.lessons.isNotEmpty()) { "没有识别到可同步的课程" }
        val existingSemester = dao.getSemester(schedule.semester.id)
        val existingRows = dao.getImportedMeetingRows(schedule.semester.id)
        val diff = compareImport(schedule, existingSemester, existingRows)
        if (!diff.hasChanges) return@withLock diff

        val entities = ImportedScheduleMapper.map(
            schedule = schedule,
            existingSemester = existingSemester,
            existingRows = existingRows,
            updatedAt = System.currentTimeMillis(),
        )
        dao.replaceImportedSemester(entities.semester, entities.courses, entities.meetings)
        diff
    }

    suspend fun replaceSchedule(schedule: ImportedSchedule) {
        applyImport(schedule)
    }

    suspend fun undoLastImport(semesterId: String): Boolean = writeMutex.withLock {
        dao.restoreLastImport(semesterId)
    }

    suspend fun saveLesson(
        semesterId: String,
        original: ScheduledCourse?,
        draft: ScheduleLessonDraft,
    ) = writeMutex.withLock {
        val semester = dao.getSemester(semesterId)?.toDomain()
            ?: error("当前学期不存在，请重新导入课表")
        validateDraft(draft, semester)
        when {
            original == null -> saveNewManualLesson(semester, draft)
            original.course.source == CourseSource.MANUAL && !original.isLocalOverride -> {
                dao.saveManualLesson(
                    course = CourseEntity(
                        id = original.course.id,
                        semesterId = semester.id,
                        name = draft.courseName.trim(),
                        teacher = draft.teacher.trim(),
                        colorIndex = original.course.colorIndex,
                        source = CourseSource.MANUAL.name,
                    ),
                    meeting = MeetingEntity(
                        id = original.meeting.id,
                        courseId = original.course.id,
                        weekday = draft.weekday,
                        startPeriod = draft.startPeriod,
                        endPeriod = draft.endPeriod,
                        room = draft.room.trim(),
                        weeksCsv = draft.weeks.sorted().joinToString(","),
                        sourceKey = original.meeting.sourceKey,
                    ),
                )
            }
            else -> dao.upsertOverride(original.toOverrideEntity(semester.id, draft, ScheduleOverrideAction.REPLACE))
        }
    }

    suspend fun deleteLesson(scheduled: ScheduledCourse) = writeMutex.withLock {
        dao.deleteExceptionsForSourceKey(scheduled.meeting.sourceKey)
        if (scheduled.course.source == CourseSource.MANUAL && !scheduled.isLocalOverride) {
            check(dao.deleteManualCourse(scheduled.course.id) == 1) { "手动课程不存在或已删除" }
        } else {
            dao.upsertOverride(
                scheduled.toOverrideEntity(
                    semesterId = scheduled.semester.id,
                    draft = scheduled.toDraft(),
                    action = ScheduleOverrideAction.HIDE,
                ),
            )
        }
    }

    suspend fun restoreImportedVersion(sourceKey: String) = writeMutex.withLock {
        dao.deleteOverride(sourceKey)
    }

    suspend fun saveException(
        scheduled: ScheduledCourse,
        originalDate: LocalDate,
        kind: ScheduleExceptionKind,
        replacementDate: LocalDate? = null,
        startPeriod: Int? = null,
        endPeriod: Int? = null,
        roomOverride: String? = null,
    ) = writeMutex.withLock {
        val semester = dao.getSemester(scheduled.semester.id)?.toDomain()
            ?: error("当前学期不存在，请重新导入课表")
        val originalWeek = teachingWeekForDate(semester, originalDate)
            ?: throw IllegalArgumentException("原上课日期不在当前学期内")
        require(originalDate.dayOfWeek.value == scheduled.meeting.weekday) {
            "原日期必须是${weekdayLabel(scheduled.meeting.weekday)}"
        }
        require(scheduled.meeting.occursIn(originalWeek)) { "该日期没有这门课程" }

        if (kind == ScheduleExceptionKind.RESCHEDULE) {
            val targetDate = requireNotNull(replacementDate) { "请选择调课后的日期" }
            require(teachingWeekForDate(semester, targetDate) != null) { "调课日期不在当前学期内" }
            require(startPeriod != null && startPeriod in 1..14) { "调课开始节次应为 1–14" }
            require(endPeriod != null && endPeriod in startPeriod..14) { "调课结束节次无效" }
        }

        val id = stableScheduleKey(semester.id, scheduled.meeting.sourceKey, originalDate.toString())
        dao.upsertException(
            ScheduleExceptionEntity(
                id = id,
                semesterId = semester.id,
                sourceKey = scheduled.meeting.sourceKey,
                originalDate = originalDate.toString(),
                kind = kind.name,
                replacementDate = replacementDate?.toString(),
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                roomOverride = roomOverride?.trim()?.takeIf(String::isNotEmpty),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteException(id: String) = writeMutex.withLock {
        dao.deleteException(id)
    }

    suspend fun updateSemesterStartDate(semesterId: String, startDate: LocalDate) = writeMutex.withLock {
        require(startDate.dayOfWeek == DayOfWeek.MONDAY) { "第一周第一天必须是星期一" }
        check(dao.updateSemesterStartDate(semesterId, startDate.toString()) == 1) {
            "当前学期不存在，请重新导入课表"
        }
    }

    private fun compareImport(
        schedule: ImportedSchedule,
        existingSemester: SemesterEntity?,
        existingRows: List<MeetingRow>,
    ): ScheduleDiff = ScheduleDiffCalculator.compare(
        semesterId = schedule.semester.id,
        previous = ImportedScheduleMapper.rowsToLessons(existingRows),
        incoming = schedule.lessons,
        semesterMetadataChanged = existingSemester != null && (
            existingSemester.name != schedule.semester.name ||
                existingSemester.totalWeeks != schedule.semester.totalWeeks
            ),
    )

    private suspend fun saveNewManualLesson(semester: Semester, draft: ScheduleLessonDraft) {
        val token = UUID.randomUUID().toString()
        val courseId = stableScheduleKey(semester.id, "manual-course", token)
        val meetingId = stableScheduleKey(semester.id, "manual-meeting", token)
        val colorIndex = stableScheduleKey(semester.id, "manual-color", token).takeLast(2).toInt(16) % 8
        dao.saveManualLesson(
            course = CourseEntity(
                id = courseId,
                semesterId = semester.id,
                name = draft.courseName.trim(),
                teacher = draft.teacher.trim(),
                colorIndex = colorIndex,
                source = CourseSource.MANUAL.name,
            ),
            meeting = MeetingEntity(
                id = meetingId,
                courseId = courseId,
                weekday = draft.weekday,
                startPeriod = draft.startPeriod,
                endPeriod = draft.endPeriod,
                room = draft.room.trim(),
                weeksCsv = draft.weeks.sorted().joinToString(","),
                sourceKey = meetingId,
            ),
        )
    }

    private fun validateDraft(draft: ScheduleLessonDraft, semester: Semester) {
        require(draft.courseName.isNotBlank()) { "课程名称不能为空" }
        require(draft.weekday in 1..7) { "星期应为 1–7" }
        require(draft.startPeriod in 1..14) { "开始节次应为 1–14" }
        require(draft.endPeriod in draft.startPeriod..14) { "结束节次不能早于开始节次" }
        require(draft.weeks.isNotEmpty()) { "至少选择一个上课周" }
        require(draft.weeks.all { it in 1..semester.totalWeeks }) {
            "周次必须在 1–${semester.totalWeeks} 周内"
        }
    }

    private fun SemesterEntity.toDomain(): Semester = Semester(
        id = id,
        name = name,
        startDate = LocalDate.parse(startDate),
        totalWeeks = totalWeeks,
    )

    private fun MeetingRow.toDomain(): ScheduledCourse = ScheduledCourse(
        semester = Semester(semesterId, semesterName, LocalDate.parse(startDate), totalWeeks),
        course = Course(
            id = courseId,
            name = courseName,
            teacher = teacher,
            colorIndex = colorIndex,
            source = CourseSource.fromStorage(courseSource),
        ),
        meeting = Meeting(
            id = meetingId,
            courseId = courseId,
            weekday = weekday,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            room = room,
            weeks = weeksCsv.split(',').mapNotNull(String::toIntOrNull).toSet(),
            sourceKey = sourceKey.ifBlank { meetingId },
        ),
    )

    private fun ScheduleOverrideEntity.toDomain(): ScheduleOverride {
        val actionValue = ScheduleOverrideAction.valueOf(action)
        return ScheduleOverride(
            sourceKey = sourceKey,
            action = actionValue,
            draft = ScheduleLessonDraft(
                courseName = courseName,
                teacher = teacher,
                room = room,
                weekday = weekday,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weeks = weeksCsv.split(',').mapNotNull(String::toIntOrNull).toSet(),
            ),
            colorIndex = colorIndex,
        )
    }

    private fun ScheduleExceptionEntity.toDomain(): ScheduleException = ScheduleException(
        id = id,
        sourceKey = sourceKey,
        originalDate = LocalDate.parse(originalDate),
        kind = ScheduleExceptionKind.valueOf(kind),
        replacementDate = replacementDate?.let(LocalDate::parse),
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        roomOverride = roomOverride,
    )

    private fun ScheduledCourse.toOverrideEntity(
        semesterId: String,
        draft: ScheduleLessonDraft,
        action: ScheduleOverrideAction,
    ): ScheduleOverrideEntity = ScheduleOverrideEntity(
        sourceKey = meeting.sourceKey,
        semesterId = semesterId,
        action = action.name,
        courseName = draft.courseName.trim(),
        teacher = draft.teacher.trim(),
        colorIndex = course.colorIndex,
        weekday = draft.weekday,
        startPeriod = draft.startPeriod,
        endPeriod = draft.endPeriod,
        room = draft.room.trim(),
        weeksCsv = draft.weeks.sorted().joinToString(","),
        updatedAt = System.currentTimeMillis(),
    )

    private fun teachingWeekForDate(semester: Semester, date: LocalDate): Int? {
        val days = ChronoUnit.DAYS.between(semester.startDate, date)
        if (days < 0 || days >= semester.totalWeeks * 7L) return null
        return (days / 7 + 1).toInt()
    }

    private fun weekdayLabel(weekday: Int): String =
        listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[weekday - 1]
}
