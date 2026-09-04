# Course Card Font Size Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a persistent four-level text-size control for weekly and daily course cards without increasing the timetable header height.

**Architecture:** Model the supported sizes as a stable enum with safe storage parsing, persist the selected enum name in app-private SharedPreferences, and keep preference state local to the timetable presentation layer. Replace the direct first-week header action with one settings dialog that contains both first-week date and course-card text-size controls.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, SharedPreferences, JUnit 4

---

### Task 1: Define and test font-size options

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableDisplayPreferences.kt`
- Create: `app/src/test/java/cn/edu/bistu/kebiao/ui/timetable/CourseCardTextSizeTest.kt`

**Step 1:** Write failing tests for known enum names and null/unknown fallback.

**Step 2:** Run the focused test and confirm the missing model fails compilation.

**Step 3:** Implement four stable scale values and safe parsing, then rerun the test.

### Task 2: Persist the selection

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableDisplayPreferences.kt`

**Step 1:** Read the stored enum name from app-private SharedPreferences.

**Step 2:** Save changes asynchronously and keep “标准” as the default.

### Task 3: Add settings UI and scale course cards

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1:** Replace the direct first-week button with a compact “设置” entry.

**Step 2:** Build a dialog with the existing first-week date row, four accessible size choices, and a live course-card preview.

**Step 3:** Pass the selected scale through weekly and today views and scale all text inside course cards while retaining overflow limits.

### Task 4: Verify

**Files:**
- Modify: `README.md`
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/importer/**`

**Step 1:** Document the display setting.

**Step 2:** Run `testDebugUnitTest lintDebug assembleDebug` with the project-local Gradle and Android user homes.

**Step 3:** Confirm test totals, Lint errors, APK hash, and unchanged importer endpoints.
