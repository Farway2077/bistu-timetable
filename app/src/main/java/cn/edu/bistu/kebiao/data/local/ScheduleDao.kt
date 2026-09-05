package cn.edu.bistu.kebiao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {
    @Query("SELECT * FROM study_tasks ORDER BY completed, dueAt, title, id")
    abstract fun observeStudyTasks(): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStudyTask(task: StudyTaskEntity)

    @Query("UPDATE study_tasks SET completed = :completed WHERE id = :id")
    abstract suspend fun setStudyTaskCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    abstract suspend fun deleteStudyTask(id: String)

    @Query("SELECT * FROM semesters ORDER BY updated_at DESC LIMIT 1")
    abstract fun observeLatestSemester(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    abstract suspend fun getSemester(semesterId: String): SemesterEntity?

    @Query(
        """
        SELECT
            s.id AS semester_id,
            s.name AS semester_name,
            s.start_date AS start_date,
            s.total_weeks AS total_weeks,
            c.id AS course_id,
            c.name AS course_name,
            c.teacher AS teacher,
            c.color_index AS color_index,
            c.source AS course_source,
            m.id AS meeting_id,
            m.weekday AS weekday,
            m.start_period AS start_period,
            m.end_period AS end_period,
            m.room AS room,
            m.weeks_csv AS weeks_csv,
            m.source_key AS source_key
        FROM meetings m
        JOIN courses c ON c.id = m.course_id
        JOIN semesters s ON s.id = c.semester_id
        WHERE s.id = (SELECT id FROM semesters ORDER BY updated_at DESC LIMIT 1)
        ORDER BY m.weekday, m.start_period, c.name
        """,
    )
    abstract fun observeLatestMeetingRows(): Flow<List<MeetingRow>>

    @Query(
        """
        SELECT
            s.id AS semester_id,
            s.name AS semester_name,
            s.start_date AS start_date,
            s.total_weeks AS total_weeks,
            c.id AS course_id,
            c.name AS course_name,
            c.teacher AS teacher,
            c.color_index AS color_index,
            c.source AS course_source,
            m.id AS meeting_id,
            m.weekday AS weekday,
            m.start_period AS start_period,
            m.end_period AS end_period,
            m.room AS room,
            m.weeks_csv AS weeks_csv,
            m.source_key AS source_key
        FROM meetings m
        JOIN courses c ON c.id = m.course_id
        JOIN semesters s ON s.id = c.semester_id
        WHERE s.id = :semesterId AND c.source = 'IMPORTED'
        ORDER BY m.weekday, m.start_period, c.name
        """,
    )
    abstract suspend fun getImportedMeetingRows(semesterId: String): List<MeetingRow>

    @Query(
        """
        SELECT * FROM schedule_overrides
        WHERE semester_id = (SELECT id FROM semesters ORDER BY updated_at DESC LIMIT 1)
        ORDER BY weekday, start_period, course_name
        """,
    )
    abstract fun observeLatestOverrides(): Flow<List<ScheduleOverrideEntity>>

    @Query(
        """
        SELECT * FROM schedule_exceptions
        WHERE semester_id = (SELECT id FROM semesters ORDER BY updated_at DESC LIMIT 1)
        ORDER BY original_date, source_key
        """,
    )
    abstract fun observeLatestExceptions(): Flow<List<ScheduleExceptionEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM import_snapshots
            WHERE semester_id = (SELECT id FROM semesters ORDER BY updated_at DESC LIMIT 1)
        )
        """,
    )
    abstract fun observeCanUndoLatestImport(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSemesterIfMissing(entity: SemesterEntity): Long

    @Query(
        """
        UPDATE semesters
        SET name = :name, start_date = :startDate, total_weeks = :totalWeeks, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    protected abstract suspend fun updateSemester(
        id: String,
        name: String,
        startDate: String,
        totalWeeks: Int,
        updatedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCourses(entities: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMeetings(entities: List<MeetingEntity>)

    @Query("DELETE FROM courses WHERE semester_id = :semesterId AND source = 'IMPORTED'")
    protected abstract suspend fun deleteImportedCourses(semesterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSnapshot(entity: ImportSnapshotEntity)

    @Query("SELECT * FROM import_snapshots WHERE semester_id = :semesterId LIMIT 1")
    protected abstract suspend fun getSnapshot(semesterId: String): ImportSnapshotEntity?

    @Query("DELETE FROM import_snapshots WHERE semester_id = :semesterId")
    protected abstract suspend fun deleteSnapshot(semesterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOverride(entity: ScheduleOverrideEntity)

    @Query("DELETE FROM schedule_overrides WHERE source_key = :sourceKey")
    abstract suspend fun deleteOverride(sourceKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertException(entity: ScheduleExceptionEntity)

    @Query("DELETE FROM schedule_exceptions WHERE id = :id")
    abstract suspend fun deleteException(id: String)

    @Query("DELETE FROM schedule_exceptions WHERE source_key = :sourceKey")
    abstract suspend fun deleteExceptionsForSourceKey(sourceKey: String)

    @Query("DELETE FROM courses WHERE id = :courseId AND source = 'MANUAL'")
    abstract suspend fun deleteManualCourse(courseId: String): Int

    @Query("UPDATE semesters SET start_date = :startDate WHERE id = :semesterId")
    abstract suspend fun updateSemesterStartDate(semesterId: String, startDate: String): Int

    @Transaction
    open suspend fun saveManualLesson(course: CourseEntity, meeting: MeetingEntity) {
        require(course.source == "MANUAL") { "Manual editor can only write manual courses" }
        insertCourses(listOf(course))
        insertMeetings(listOf(meeting))
    }

    @Transaction
    open suspend fun replaceImportedSemester(
        semester: SemesterEntity,
        courses: List<CourseEntity>,
        meetings: List<MeetingEntity>,
    ) {
        require(courses.isNotEmpty() && meetings.isNotEmpty()) {
            "A validated sync must contain at least one imported course and meeting"
        }
        val previousSemester = getSemester(semester.id)
        val previousRows = getImportedMeetingRows(semester.id)
        if (previousSemester != null && previousRows.isNotEmpty()) {
            insertSnapshot(
                ImportSnapshotCodec.encode(
                    semester = previousSemester,
                    rows = previousRows,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } else {
            deleteSnapshot(semester.id)
        }

        upsertSemesterPreservingChildren(semester)
        deleteImportedCourses(semester.id)
        insertCourses(courses)
        insertMeetings(meetings)
    }

    @Transaction
    open suspend fun restoreLastImport(semesterId: String): Boolean {
        val snapshot = getSnapshot(semesterId) ?: return false
        val restored = ImportSnapshotCodec.decode(snapshot)
        upsertSemesterPreservingChildren(
            restored.semester.copy(updatedAt = System.currentTimeMillis()),
        )
        deleteImportedCourses(semesterId)
        insertCourses(restored.courses)
        insertMeetings(restored.meetings)
        deleteSnapshot(semesterId)
        return true
    }

    private suspend fun upsertSemesterPreservingChildren(entity: SemesterEntity) {
        insertSemesterIfMissing(entity)
        check(
            updateSemester(
                id = entity.id,
                name = entity.name,
                startDate = entity.startDate,
                totalWeeks = entity.totalWeeks,
                updatedAt = entity.updatedAt,
            ) == 1,
        ) { "Semester could not be saved" }
    }
}
