# Reliable Sync Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add source-aware timetable synchronization with an import diff, one-step rollback, persistent manual edits, and date-specific cancellation or rescheduling.

**Architecture:** Keep the school extractor unchanged and evolve the Room database from version 1 to 2. Imported meetings remain the replaceable baseline; manual courses, full-term overrides, and date exceptions are independent local layers composed by the repository and pure domain functions.

**Tech Stack:** Kotlin, Jetpack Compose, ViewModel/StateFlow, Room 2.6.1, JUnit 4, Android Gradle Plugin.

---

### Task 1: Define and test synchronization rules

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/kebiao/domain/ReliableSchedule.kt`
- Create: `app/src/test/java/cn/edu/bistu/kebiao/domain/ReliableScheduleTest.kt`

**Step 1:** Write failing tests for added/removed/changed/unchanged import records, full-term replacement/hide overrides, single-date cancellation, same-week rescheduling, and cross-week rescheduling.

**Step 2:** Run `./gradlew.bat testDebugUnitTest --tests "cn.edu.bistu.kebiao.domain.ReliableScheduleTest"` and verify the missing types/functions fail compilation.

**Step 3:** Implement immutable models, normalization, diff calculation, override composition, and week-specific exception application.

**Step 4:** Re-run the focused test and expect all cases to pass.

### Task 2: Add Room version 2 and migration

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/local/Entities.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/local/ScheduleDao.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/local/AppDatabase.kt`
- Create: `app/src/main/java/cn/edu/bistu/kebiao/data/local/DatabaseMigrations.kt`
- Create: `app/src/main/java/cn/edu/bistu/kebiao/data/local/ImportSnapshotCodec.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/KebiaoApplication.kt`
- Modify: `app/build.gradle.kts`

**Step 1:** Add source and stable-key columns plus override, exception, and snapshot entities.

**Step 2:** Add DAO queries and transactions that replace only imported courses, preserve all local layers, snapshot the previous imported baseline, and restore it once.

**Step 3:** Register `MIGRATION_1_2`, enable schema export, and build Room generated sources to verify schema consistency.

### Task 3: Implement repository operations and import diff preview

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/domain/Models.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/ScheduleRepository.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/importer/ImportViewModel.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/importer/ImportScreen.kt`
- Create: `app/src/test/java/cn/edu/bistu/kebiao/data/ImportEntityFactoryTest.kt`

**Step 1:** Test deterministic imported keys and preservation of course colors.

**Step 2:** Expose effective courses, exceptions, undo availability, preview diff, transactional apply/undo, and manual edit operations from the repository.

**Step 3:** Calculate the diff after page parsing and change the confirmation dialog from destructive replacement language to explicit synchronization counts.

**Step 4:** Run importer, domain, and data unit tests.

### Task 4: Add course management and date exceptions UI

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/kebiao/ui/editor/ScheduleEditorViewModel.kt`
- Create: `app/src/main/java/cn/edu/bistu/kebiao/ui/editor/ScheduleEditorScreen.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/App.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1:** Add a management route and list effective schedule entries with source labels.

**Step 2:** Add validated forms for manual add/edit/delete and restoring an imported version.

**Step 3:** Add cancellation/reschedule forms and a list of active temporary adjustments with restore actions.

**Step 4:** Feed week and today views from the same exception-aware schedule function and add undo confirmation to settings.

### Task 5: Documentation and release gate

**Files:**
- Modify: `README.md`
- Generated: `app/schemas/cn.edu.bistu.kebiao.data.local.AppDatabase/2.json`

**Step 1:** Document source-aware sync, manual management, temporary changes, and rollback without changing the stated credential boundary.

**Step 2:** Run `./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon` with project-local Gradle and Android caches.

**Step 3:** Confirm all tests pass, Lint has zero errors, the debug APK exists, and importer endpoint strings are unchanged.
