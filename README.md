# MiharuNiwa — TKB

**Offline-first Android app for viewing class schedules and transcripts.**

---

## Overview

MiharuNiwa — TKB is a native Android app for students to view their weekly class schedule
(TKB = *thời khóa biểu*) and course transcripts. It scrapes a WordPress site on-device,
caches everything locally so schedules and PDFs remain viewable offline, and can optionally
use the Gemini API to read schedule and grade-sheet PDFs automatically.

The application, its UI, and its comments are written in Vietnamese.

## Why this project exists

This is a personal project built to solve a real workflow problem: the official way to check
class schedules required repeated browsing of a slow, structure-dependent website. The goal
was a fast, offline-capable Android client that surfaces the same information with less
friction — not a commercial product.

## Features

- **Schedule scraping** — parses the weekly schedule pages (`.wp-block-post-title a`) with
  Jsoup and stores the results in a local Room database.
- **Offline-first** — weeks, schedule details, PDFs, and grades are cached; the app opens and
  shows cached data immediately, then refreshes opportunistically.
- **Pin schedules** — pin a specific class schedule (e.g. *CS1 / CS2* + system) to the top of
  the home screen for one-tap access.
- **PDF viewer with zoom** — Google Drive-hosted PDFs are downloaded and rendered with
  multi-touch zoom and inertia.
- **Crop & export** — select a region of a schedule (e.g. just your own class) and save it as
  a full-resolution PNG via the Storage Access Framework.
- **Background update alerts** — a periodic worker checks for new/changed Google Drive file IDs
  (only on Fridays and Saturdays) and posts a notification when something changed.
- **Grades & transcripts** — browse class grade sheets and student overviews; optionally
  auto-parse grade PDFs with the Gemini API.
- **Forms** — download and view administrative forms PDFs.
- **Home-screen widget** — a Glance-based widget for at-a-glance schedule info.
- **Class reminders** — configurable morning/afternoon alarms per tracked class.

## Architecture

A single-activity Compose app with no DI framework. `MainActivity` builds the object graph by
hand — `AppPreferences`, `AppDatabase` (Room), `ScheduleRepository`, `OkHttpClient`, and
`PdfDownloader` — and passes it into the `TkbApp` composable.

```
MainActivity  ── builds ──►  AppPreferences / AppDatabase / ScheduleRepository / PdfDownloader
        │
        └──►  TkbApp (NavHost + HorizontalPager) ──►  screens in ui/screens/
                        │
        data/ = Room + DataStore + Jsoup scraping + Gemini PDF parsing
        widget/ = Glance home-screen widget + daily updater
```

- **Data layer** (`data/`): Room entities/DAOs, `AppPreferences` (DataStore) for all user
  config, `ScheduleRepository` for scraping/offline logic, `GeminiClient` for AI PDF parsing.
- **UI layer** (`ui/`): hand-rolled dark-only Material 3 theme (`ui/theme/Color.kt`), screen
  composables in `ui/screens/`.
- **Background** (`data/` + `widget/`): WorkManager workers for update checks and the widget,
  `AlarmManager` for class reminders.

See `CLAUDE.md` for the full architectural breakdown that future contributors are expected to
read.

## Tech Stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room, DataStore Preferences, WorkManager
- Jsoup (HTML scraping), OkHttp + Retrofit + Moshi
- Google GenAI SDK (Gemini API)
- Glance (app widget), Coil, `net.engawapg.lib:zoomable`, `vanniktech/android-image-cropper`
- Robolectric + Roborazzi (unit/UI screenshot tests)

## AI-assisted development

This repository was developed with extensive AI assistance. AI generated and refactored much
of the Kotlin code and contributed to architecture discussions. The human author was
responsible for defining product requirements, deciding workflows, evaluating architecture,
testing on real devices, rejecting incorrect implementations, deciding UX and priorities, and
iterating based on real-world usage.

AI usage is documented openly here rather than hidden.

## Limitations

- **Fragile scraping** — the app depends directly on the target site's HTML structure
  (`.wp-block-post-title`, `.wp-block-post-content p`). A redesign by the site owner breaks
  schedule/grade fetching until the app is updated.
- **No backend** — all scraping happens client-side, so server-side rate-limiting or
  Cloudflare protection can block requests.
- **Dark theme only** — there is no light mode or dynamic color.
- **Single-purpose** — the app is tailored to one specific institution's WordPress site.

## Building

Prerequisites: Android Studio (or the Android SDK + JDK). The build uses `JAVA_HOME=~/.local/java`.

```bash
# Build + install + launch on a connected device (recommended)
./build.sh

# Gradle directly
./gradlew assembleDebug
./gradlew testDebugUnitTest        # unit tests (Robolectric/Roborazzi)
./gradlew connectedAndroidTest     # instrumented tests
```

## License

[MIT License](./LICENSE)
