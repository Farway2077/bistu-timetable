package cn.edu.bistu.kebiao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SemesterEntity::class, CourseEntity::class, MeetingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}

