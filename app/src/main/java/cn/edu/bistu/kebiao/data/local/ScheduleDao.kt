package cn.edu.bistu.kebiao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {
    @Query("SELECT * FROM semesters ORDER BY updated_at DESC LIMIT 1")
    abstract fun observeLatestSemester(): Flow<SemesterEntity?>

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
            m.id AS meeting_id,
            m.weekday AS weekday,
            m.start_period AS start_period,
            m.end_period AS end_period,
            m.room AS room,
            m.weeks_csv AS weeks_csv
        FROM meetings m
        JOIN courses c ON c.id = m.course_id
        JOIN semesters s ON s.id = c.semester_id
        WHERE s.id = (SELECT id FROM semesters ORDER BY updated_at DESC LIMIT 1)
        ORDER BY m.weekday, m.start_period, c.name
        """,
    )
    abstract fun observeLatestMeetingRows(): Flow<List<MeetingRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSemester(entity: SemesterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCourses(entities: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMeetings(entities: List<MeetingEntity>)

    @Query("UPDATE semesters SET start_date = :startDate WHERE id = :semesterId")
    abstract suspend fun updateSemesterStartDate(semesterId: String, startDate: String): Int

    @Query("DELETE FROM semesters WHERE id = :semesterId")
    protected abstract suspend fun deleteSemester(semesterId: String)

    @Transaction
    open suspend fun replaceSemester(
        semester: SemesterEntity,
        courses: List<CourseEntity>,
        meetings: List<MeetingEntity>,
    ) {
        require(courses.isNotEmpty() && meetings.isNotEmpty()) {
            "A validated import must contain at least one course and meeting"
        }
        deleteSemester(semester.id)
        insertSemester(semester)
        insertCourses(courses)
        insertMeetings(meetings)
    }
}
