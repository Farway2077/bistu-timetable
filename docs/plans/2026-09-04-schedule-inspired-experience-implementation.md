# Schedule-Inspired Timetable Experience Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a polished weekly/today timetable experience with live current-course context while preserving the existing BISTU import implementation and database schema.

**Architecture:** Keep repository and ViewModel ownership unchanged. Add pure Kotlin summary functions for date/time calculations, then rebuild the Compose timetable screen around a shared header, two presentation modes, adaptive weekly grid, daily cards, and a common course-detail dialog.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, StateFlow, java.time, JUnit 4

---

### Task 1: Add testable today-summary logic

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableSummary.kt`
- Create: `app/src/test/java/cn/edu/bistu/kebiao/ui/timetable/TimetableSummaryTest.kt`

**Step 1: Write failing tests**

Cover dates inside and outside the semester, weekday/week filtering, upcoming class minutes, active class minutes, completed day, and no-course day.

**Step 2: Run the focused tests and verify failure**

Run: `./gradlew.bat testDebugUnitTest --tests "cn.edu.bistu.kebiao.ui.timetable.TimetableSummaryTest"`

Expected: FAIL because `buildTodayScheduleSummary` and `teachingWeekForDate` do not exist.

**Step 3: Implement the minimal pure Kotlin model**

Add `TodayPhase`, `TodayScheduleSummary`, `teachingWeekForDate`, and `buildTodayScheduleSummary`. Resolve clock times through `BistuPeriodTimes` and round partial minutes upward for user-facing countdowns.

**Step 4: Run the focused tests**

Run the command from Step 2.

Expected: all `TimetableSummaryTest` cases PASS.

### Task 2: Rebuild the timetable information hierarchy

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1: Add the shared header and mode state**

Add a current-date heading, semester/week metadata, today-status card, sync action, and saveable “本周 / 今日” mode switch. Refresh the foreground status once per minute.

**Step 2: Add the today view**

Render sorted daily course cards with clock time, section range, room, teacher, current/upcoming state, empty state, and shared course selection.

**Step 3: Make the weekly grid adaptive**

Use `BoxWithConstraints` to divide available width between the period gutter and seven day columns. Preserve vertical scrolling, course span geometry, current-day emphasis, and conflict markers.

**Step 4: Share course detail presentation**

Move the detail dialog above both views so a course opened from either mode shows the same complete data and conflict warning.

### Task 3: Refine the visual system

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/theme/Theme.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1: Define surface and outline roles for both themes**

Keep the paper-and-ink identity while improving dark surfaces, secondary containers, outlines, and error contrast.

**Step 2: Add paired course palettes**

Provide stable muted course colors and readable content colors for light and dark themes. Do not add a user settings schema in this iteration.

**Step 3: Check Compose previews**

Maintain populated light, populated dark, and today-view previews at 390 x 844 dp so Android Studio can visually inspect core states.

### Task 4: Verify behavior and protected boundaries

**Files:**
- Modify: `README.md`
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/importer/**`
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/ui/importer/**`

**Step 1: Update the feature list**

Document weekly/today modes and the foreground next-class status. Do not claim notifications, widgets, background countdowns, or editable themes.

**Step 2: Run the full quality gate**

Run: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: unit tests pass, lint completes with no errors, and `app/build/outputs/apk/debug/app-debug.apk` exists.

**Step 3: Audit the change boundary**

Confirm no file under `importer/` or `ui/importer/` changed, and no endpoint strings or Room entities were modified.

