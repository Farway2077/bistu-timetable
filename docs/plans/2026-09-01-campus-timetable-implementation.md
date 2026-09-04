# Campus Timetable Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Android weekly timetable that imports a BISTU semester schedule through an in-app school login and stores the parsed result locally.

**Architecture:** A single-activity Jetpack Compose app separates timetable domain logic, Room persistence, and a WebView-based import adapter. Page extraction stays local, parsing is pure Kotlin, and a validated import replaces one semester atomically.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Room, Coroutines, Android WebView, JUnit, Android Gradle Plugin.

---

### Task 1: Bootstrap the Android project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

**Step 1: Add the minimal Gradle and Android configuration**

Configure one `:app` module, API 26 minimum, Compose, Room/KSP, coroutines, lifecycle and navigation dependencies.

**Step 2: Add a smoke test**

Create `app/src/test/java/cn/edu/bistu/timetable/ExampleUnitTest.kt` and assert the test runtime loads.

**Step 3: Run the test**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Implement and test the timetable domain

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/timetable/domain/Models.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/domain/WeekPattern.kt`
- Test: `app/src/test/java/cn/edu/bistu/timetable/domain/WeekPatternTest.kt`

**Step 1: Write failing week-pattern tests**

Cover `1-16周`, `1-15周(单)`, `2-16周(双)`, comma-separated ranges and individual weeks.

**Step 2: Implement the immutable domain models and parser**

Model semesters, courses and meetings. Convert week text to a set of week numbers and expose `occursIn(week)`.

**Step 3: Run the focused test**

Run: `./gradlew testDebugUnitTest --tests '*WeekPatternTest'`

Expected: all cases pass.

### Task 3: Implement and test page-text parsing

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/timetable/importer/ExtractedPage.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/importer/TimetableTextParser.kt`
- Test: `app/src/test/java/cn/edu/bistu/timetable/importer/TimetableTextParserTest.kt`

**Step 1: Add a sanitized BISTU-style fixture**

Include a table-shaped fixture and a card-shaped fixture with course, teacher, room, weekday, period and week-range text.

**Step 2: Verify tests fail before implementation**

Run the focused parser test and expect missing parser symbols.

**Step 3: Implement layered recognition**

Prefer structured cell records emitted by JavaScript, then use normalized text blocks. Return warnings instead of silently inventing missing values.

**Step 4: Verify parser behavior**

Run: `./gradlew testDebugUnitTest --tests '*TimetableTextParserTest'`

Expected: fixtures produce exact meetings and malformed input returns an actionable warning.

### Task 4: Add transactional local persistence

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/timetable/data/local/Entities.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/data/local/ScheduleDao.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/data/local/AppDatabase.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/data/ScheduleRepository.kt`

**Step 1: Define Room entities and relations**

Use stable generated IDs and unique import keys. Add semester, course and meeting tables with cascading deletes.

**Step 2: Implement atomic replacement**

Inside one transaction, validate the non-empty import, replace only the selected semester, and leave prior data untouched when validation fails.

**Step 3: Add repository mapping tests**

Verify week filtering and conflict preservation using pure mapper tests.

### Task 5: Build the weekly timetable UI

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/timetable/MainActivity.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/App.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/timetable/TimetableViewModel.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/timetable/TimetableScreen.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/theme/Theme.kt`

**Step 1: Render preview/sample state**

Provide deterministic sample data for Compose previews and UI tests.

**Step 2: Implement week navigation and grid layout**

Show seven weekday columns, a period gutter, spanning course cards, current-day emphasis, loading/error/empty states and accessible descriptions.

**Step 3: Add UI tests**

Verify empty state, sample course text, week switching and import action semantics.

### Task 6: Add secure WebView import

**Files:**
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/importer/ImportScreen.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/ui/importer/ImportViewModel.kt`
- Create: `app/src/main/java/cn/edu/bistu/timetable/importer/BistuPageExtractor.kt`
- Create: `app/src/main/res/xml/network_security_config.xml`

**Step 1: Lock navigation to approved HTTPS hosts**

Allow BISTU teaching and authentication hosts in WebView. Open unexpected external links outside the embedded session. Disable file access, content access, password saving and release WebView debugging.

**Step 2: Inject the read-only extractor**

After page load, scan visible tables, cells and same-origin frames; serialize only normalized course-like text and structural hints through a narrow JavaScript bridge.

**Step 3: Parse, preview and confirm**

Run parsing off the main thread. Never replace stored data before validation and explicit confirmation.

**Step 4: Add security and extraction tests**

Test host allowlisting, redaction of credential-shaped fields and empty/non-course page handling.

### Task 7: Verify and package

**Files:**
- Create: `README.md`
- Create: `.gitignore`

**Step 1: Run static and automated checks**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: all tasks complete successfully with no lint errors.

**Step 2: Install and inspect the debug build**

Run on an available emulator/device. Verify empty state, import navigation, week switching, course details and process-restart persistence.

**Step 3: Record limitations**

Document that exact production parsing still requires one real, user-authorized, successfully logged-in course page; include a safe diagnostics workflow without credentials or cookies.

