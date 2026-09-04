# Edge-to-Edge and First Week Date Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the mismatched top system-bar strip, make the timetable header more compact, and let users persist the Monday that starts teaching week one.

**Architecture:** Use AndroidX Activity edge-to-edge configuration for transparent system bars and keep Compose responsible for safe-area padding. Reuse the existing semester `start_date` column through a focused DAO update, expose it through the repository and timetable ViewModel, and present a Material 3 date picker from the timetable header.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Activity, Room, StateFlow, java.time, JUnit 4

---

### Task 1: Date selection rules

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableSummary.kt`
- Test: `app/src/test/java/cn/edu/bistu/kebiao/ui/timetable/TimetableSummaryTest.kt`

**Step 1:** Add failing tests for UTC date-picker conversion and Monday validation.

**Step 2:** Run the focused test and confirm unresolved helpers fail compilation.

**Step 3:** Implement UTC-safe `LocalDate`/millisecond conversion and week-start validation.

**Step 4:** Rerun the focused test and confirm it passes.

### Task 2: Persist the first-week date

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/local/ScheduleDao.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/data/ScheduleRepository.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableViewModel.kt`

**Step 1:** Add a single-column Room update returning its affected-row count.

**Step 2:** Validate Monday input in the repository and fail if the semester no longer exists.

**Step 3:** Launch the update from the ViewModel and reset selected week so today is recalculated.

### Task 3: Fix system bars and compact the timetable header

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1:** Replace the manual decor-fit call with AndroidX `enableEdgeToEdge()` and transparent XML system-bar colors.

**Step 2:** Reduce header and status spacing without shrinking touch targets.

**Step 3:** Add a “首周 M/d” action and a Monday-only Material date picker.

### Task 4: Verify

**Files:**
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/importer/**`
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/ui/importer/**`

**Step 1:** Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` with project-local Gradle and Android user homes.

**Step 2:** Confirm the APK exists and importer endpoint strings are unchanged.

**Step 3:** If a device is available, install and capture the timetable; otherwise report the missing runtime visual check explicitly.
