"""Host SQLite check of migration 2 -> 3 against Room's exported schemas.

Run with Python 3: python scripts/verify_study_task_migration.py
This verifies schema/data behavior; it does not replace an Android upgrade test.
"""
from contextlib import closing
import json
from pathlib import Path
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/cn.edu.bistu.kebiao.data.local.AppDatabase"


def schema(version):
    return json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]


def create_schema(db, exported):
    for entity in exported["entities"]:
        table = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity["indices"]:
            db.execute(index["createSql"].replace("${TABLE_NAME}", table))


def signatures(db, table):
    return (
        db.execute(f'PRAGMA table_info("{table}")').fetchall(),
        db.execute(f'PRAGMA foreign_key_list("{table}")').fetchall(),
        sorted(db.execute(f'PRAGMA index_list("{table}")').fetchall()),
    )


def verify():
    old, new = schema(2), schema(3)
    source = (ROOT / "app/src/main/java/cn/edu/bistu/kebiao/data/local/DatabaseMigrations.kt").read_text(encoding="utf-8")
    migration = source.split("val MIGRATION_2_3 =", 1)[1].split("val MIGRATION_1_2 =", 1)[0]
    statements = re.findall(r'"""(.*?)"""\.trimIndent\(\)', migration, re.S)
    assert len(statements) == 1, "Update verifier if the migration format changes"

    with tempfile.TemporaryDirectory(prefix="kebiao-migration-") as directory:
        path = Path(directory) / "test.db"
        with closing(sqlite3.connect(path)) as db, closing(sqlite3.connect(":memory:")) as expected:
            db.execute("PRAGMA foreign_keys = ON")
            create_schema(db, old)
            create_schema(expected, new)
            before = {}
            for entity in old["entities"]:
                table = entity["tableName"]
                fields = entity["fields"]
                columns = ", ".join('"' + field["columnName"] + '"' for field in fields)
                values = [1 if field["affinity"] == "INTEGER" else "fixture" for field in fields]
                placeholders = ", ".join("?" for _ in values)
                db.execute(f'INSERT INTO "{table}" ({columns}) VALUES ({placeholders})', values)
                before[table] = db.execute(f'SELECT * FROM "{table}"').fetchall()
            for statement in statements:
                db.execute(statement)
            for entity in new["entities"]:
                table = entity["tableName"]
                assert signatures(db, table) == signatures(expected, table), table
            for table, rows in before.items():
                assert db.execute(f'SELECT * FROM "{table}"').fetchall() == rows, table
            db.execute("INSERT INTO study_tasks VALUES (?, ?, ?, ?, ?, ?, ?)",
                       ("task", "Report", "HOMEWORK", "Physics", "2026-09-08T23:59", "Chapter 2", 0))
            db.execute("UPDATE study_tasks SET completed = 1 WHERE id = 'task'")
            # Simulate removal/replacement of imported semester data.
            db.execute("DELETE FROM semesters")
            assert db.execute("SELECT completed FROM study_tasks").fetchone() == (1,)
            assert db.execute("PRAGMA foreign_key_check").fetchall() == []
            db.commit()
        with closing(sqlite3.connect(path)) as reopened:
            assert reopened.execute("SELECT title, notes, completed FROM study_tasks").fetchone() == ("Report", "Chapter 2", 1)
            reopened.execute("UPDATE study_tasks SET completed = 0 WHERE id = 'task'")
            assert reopened.execute("SELECT completed FROM study_tasks").fetchone() == (0,)
            reopened.execute("DELETE FROM study_tasks WHERE id = 'task'")
            assert reopened.execute("SELECT count(*) FROM study_tasks").fetchone() == (0,)
    print("PASS: v2 data preserved, v3 schema matches, tasks survive semester removal and reopening, CRUD verified.")


if __name__ == "__main__":
    verify()
