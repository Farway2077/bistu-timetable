package cn.edu.bistu.kebiao.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS study_tasks (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                kind TEXT NOT NULL,
                courseName TEXT NOT NULL,
                dueAt TEXT NOT NULL,
                notes TEXT NOT NULL,
                completed INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE courses ADD COLUMN source TEXT NOT NULL DEFAULT 'IMPORTED'")
        db.execSQL("ALTER TABLE meetings ADD COLUMN source_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE meetings SET source_key = id WHERE source_key = ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schedule_overrides (
                source_key TEXT NOT NULL,
                semester_id TEXT NOT NULL,
                action TEXT NOT NULL,
                course_name TEXT NOT NULL,
                teacher TEXT NOT NULL,
                color_index INTEGER NOT NULL,
                weekday INTEGER NOT NULL,
                start_period INTEGER NOT NULL,
                end_period INTEGER NOT NULL,
                room TEXT NOT NULL,
                weeks_csv TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(source_key),
                FOREIGN KEY(semester_id) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_schedule_overrides_semester_id " +
                "ON schedule_overrides(semester_id)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schedule_exceptions (
                id TEXT NOT NULL,
                semester_id TEXT NOT NULL,
                source_key TEXT NOT NULL,
                original_date TEXT NOT NULL,
                kind TEXT NOT NULL,
                replacement_date TEXT,
                start_period INTEGER,
                end_period INTEGER,
                room_override TEXT,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(semester_id) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_schedule_exceptions_semester_id " +
                "ON schedule_exceptions(semester_id)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_exceptions_source_key_original_date " +
                "ON schedule_exceptions(source_key, original_date)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS import_snapshots (
                semester_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(semester_id),
                FOREIGN KEY(semester_id) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
