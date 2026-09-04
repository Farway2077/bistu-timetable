# Week Pager Gesture Implementation Plan

**Goal:** Make weekly timetable paging track the finger continuously and settle naturally after release, while keeping buttons and application state synchronized.

**Architecture:** Keep `selectedWeek` as the application source of truth and use Compose Foundation `HorizontalPager` as the gesture source. Observe `settledPage` to publish completed gestures to the ViewModel, and animate the pager when the selected week changes externally. Render every page from its own week number.

**Tech Stack:** Kotlin, Jetpack Compose Foundation Pager, Material 3, Kotlin Flow, JUnit 4

---

### Task 1: Pager mapping model

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/WeekTransition.kt`
- Modify: `app/src/test/java/cn/edu/bistu/kebiao/ui/timetable/WeekTransitionTest.kt`

**Step 1:** Replace direction tests with failing week-to-page and page-to-week mapping tests, including invalid boundary values.

**Step 2:** Implement minimal clamped mapping helpers and rerun the focused test.

### Task 2: State synchronization

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`

**Step 1:** Add a clamped `selectWeek` entry point to the ViewModel and pass it to the weekly body.

**Step 2:** Observe the pager's settled page and publish the corresponding teaching week.

**Step 3:** Animate to the selected week when navigation buttons or semester settings change it externally.

### Task 3: Interactive paging

**Files:**
- Modify: `app/src/main/java/cn/edu/bistu/kebiao/ui/timetable/TimetableScreen.kt`
- Modify: `app/build.gradle.kts`

**Step 1:** Replace `AnimatedContent` and the manual drag threshold with `HorizontalPager`.

**Step 2:** Preload one neighboring page and keep each page's vertical scrolling independent.

**Step 3:** Remove obsolete animation imports and the no-longer-needed explicit animation dependency.

### Task 4: Verify

**Files:**
- Modify: `README.md`
- Verify only: `app/src/main/java/cn/edu/bistu/kebiao/importer/**`

**Step 1:** Document finger-tracking week paging and snap-back behavior.

**Step 2:** Run `testDebugUnitTest lintDebug assembleDebug` with project-local Gradle and Android user homes.

**Step 3:** Record test totals, Lint errors, APK hash, and unchanged importer endpoints.
