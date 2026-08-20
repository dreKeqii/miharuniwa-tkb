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

This is a personal project built to solve a real workflow problem: the school's website is
terribly designed and slow, the class schedule and grade sheets are all PDF files embedded in
iframes that link out to Google Drive, and the images the web renders couldn't look worse —
if you want them sharp, you have to open them on Google Drive itself.

Tired of a tedious routine just to view and screenshot a schedule, I made this app. It shows
the latest schedule right in a native Android app, no need to open the web. It's much smoother
than the website — loading is still slow, but that's about the limit of what the school's site
allows. Apart from that, you also get grade sheets, student notes, and the school's
administrative forms.

This is a personal project with exactly one user, nothing commercial, and the one person who
uses it will only be using it until mid-2027. After graduation, it gets ditched too.

Nearly all of the code was written with AI, [details here](#ai-assisted-development).

That's about it — everything below here is still AI-written. You can read it or not; I really
don't mind.

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
- **Almost no automated tests** — the repo has a few leftover test files (Robolectric +
  Roborazzi), but from day one all testing has been done by hand on real devices; most bugs
  are found by the user.

## Building

Prerequisites: Android Studio (or the Android SDK + JDK). The build uses `JAVA_HOME=~/.local/java`.

```bash
# Build + install + launch on a connected device (recommended)
./build.sh

# Gradle directly
./gradlew assembleDebug
```

## License

[MIT License](./LICENSE)
