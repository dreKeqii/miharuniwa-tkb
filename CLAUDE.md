# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

"MiharuNiwa - TKB" is a native Android app (Kotlin + Jetpack Compose) for students to view their weekly class schedule (TKB = *thời khóa biểu*). It scrapes a WordPress site with Jsoup, caches everything locally for offline viewing, renders Google-Drive-hosted PDFs, and adds an AI layer that reads schedule/transcript PDFs via the Gemini API.

The app, its comments, and UI strings are in Vietnamese. No backend exists — all scraping happens on-device, which makes the app brittle to HTML structure changes on the target WordPress site.

This is **not** a git repository.

## Build, Test, Install

- **Build + install + launch on a connected device (the sanctioned way):**
  `./build.sh` — sets `JAVA_HOME=~/.local/java`, runs `./gradlew clean assembleDebug --no-daemon`, then `adb install -r` on the resulting APK and launches it via monkey. Use this for any "build and show it to me" request; do not use a different adb/install invocation.
- **Gradle directly** (JVM is at `~/.local/java`; export it first if gradle complains about the JDK):
  - Assemble debug: `./gradlew assembleDebug`
  - Unit tests (Robolectric + Roborazzi): `./gradlew testDebugUnitTest`
  - Single test: `./gradlew testDebugUnitTest --tests "com.example.ExampleUnitTest"`
  - Instrumented tests: `./gradlew connectedAndroidTest`
- The `secrets` Gradle plugin reads `GEMINI_API_KEY` from `.env` (falls back to `.env.example`). The key is also storable per-device via `AppPreferences.saveGeminiApiKey`.

## Architecture

No DI framework (no Hilt/Koin). `MainActivity` manually constructs the object graph in `onCreate`: `AppPreferences`, `AppDatabase` (Room singleton), `ScheduleRepository`, an `OkHttpClient`, and `PdfDownloader` — then passes them into the `TkbApp` composable.

### Data layer (`app/src/main/java/com/miharuniwa/tkb/data/`)

- **`AppDatabase.kt`** — Room DB (`schedule-db`, version 6, `exportSchema = false`, `fallbackToDestructiveMigration`). Entities: `WeekItemEntity`, `ScheduleDetailEntity`, `FormItemEntity`, `AlarmEntity`, `ClassGradeEntity`, `GradeSubjectEntity`. Each entity has a DAO living in the same file or a sibling (`ScheduleDao`/`FormDao` in AppDatabase.kt, `AlarmDao`, `GradeDao`).
- **`ScheduleRepository.kt`** — central repo. Does the Jsoup scraping (`.wp-block-post-title a` for weeks, `.wp-block-post-content p` + `iframe` for schedule details), offline PDF download, Gemini PDF parsing, and grade/form caching. All network calls are wrapped in `withContext(Dispatchers.IO)`.
- **`AppPreferences.kt`** — Jetpack DataStore (`app_prefs`). All user config (root URL, followed systems, alarm config, Gemini key/model, debug mode, dashboard classes) flows through here as `Flow`s. UI observes these flows; writes happen via `saveXxx`/`toggleXxx`/`resetXxx` suspend functions.
- **`GeminiClient.kt`** — wraps `com.google.genai` SDK; `parseScheduleFromPdf` and `parseGradesFromPdf` render PDF pages to Bitmaps via `PdfRenderer` and send them to Gemini asking for strict JSON (`.responseMimeType("application/json")`). Prompts are long Vietnamese system-prompts defined inline here.
- **`GradeDataModels.kt`** — parsed-grade data classes + `GradeJsonParser` (org.json based).

### UI layer (`.../ui/`)

- **`TkbApp.kt`** — single manual `NavHost`. Top-level "main" destination is a `HorizontalPager` (3 pages: Lịch học / Bảng điểm / Biểu mẫu) with a bottom `NavigationBar`; sub-destinations are `detail/{weekId}`, `pdf/{fileId}/{fileName}`, `grade_detail/{classId}`, `student_overview/{name}/{dob}`, `student_grades/{fileId}/{subjectName}`. Route params are URL-encoded strings, decoded with `URLDecoder` at each destination.
- Theme is dark-only and hand-rolled: colors are hard-coded in `ui/theme/Color.kt` (`BgDark`, `SurfaceDark`, `PrimaryDark`, `TextPrimary`…) and applied in `Theme.kt` via a `darkColorScheme`. There is no light mode and no dynamic color — components reference the `Color.kt` vals directly rather than `MaterialTheme` in many cases.
- Screens are in `ui/screens/` (`MainScreen`, `DetailScreen`, `PdfViewerScreen`, `GradeClassesScreen`, `ClassGradeDetailScreen`, `StudentGradeScreen`, `StudentOverviewScreen`, `FormsScreen`, `SettingsBottomSheet`, alarm dialogs). `ui/components/ImageCropper.kt` holds crop UI.

### Background work (`data/` + `widget/`)

- `ScheduleUpdateWorker` — WorkManager periodic hourly job that only actually scrapes on Friday/Saturday, diffs Google Drive file IDs, and pushes a notification on change.
- `ScheduleAlarmReceiver` / `AlarmScheduler` — exact-alarm reminders (`SCHEDULE_EXACT_ALARM` permission).
- `widget/` package — Glance-based home-screen widget (`ScheduleWidget`, `ScheduleWidgetConfigActivity`, `WidgetDailyUpdateWorker`, receivers).

### PDF flow

PdfViewerScreen takes a Google Drive `fileId`; `PdfDownloader`/`ScheduleRepository.downloadPdf` fetch it via `https://drive.google.com/uc?export=download&id={fileId}` and cache under `context.cacheDir` as `schedule_{fileId}.pdf` (background HEAD-check for updates when already cached). Users can crop-and-export a region to PNG via SAF.

## Key conventions

- **No ViewModels** — screens hold state locally via `remember`/`LaunchedEffect`, observing `AppPreferences` flows and calling repository suspend functions directly.
- **Offline-first everywhere**: Room + DataStore + cached PDFs are read first; network refreshes happen opportunistically and are `try/catch`-swallowed.
- **Hard-coded HTML selectors** (`.wp-block-post-title a`, `.wp-block-post-content p`, Google Drive iframe URL parsing) are the app's main fragility — see `FEATURES.md` §3 and the scraping methods in `ScheduleRepository.kt` if scraping breaks.
- Screen orientation is locked portrait in `AndroidManifest.xml`.

## Useful references in-repo

- `FEATURES.md` — feature map, scraping flow, pros/cons (Vietnamese).
- `build.sh` — the build-and-install contract.
