# ClassFlow

**A timetable app for SZTU students that logs into the school's academic system and pulls the whole semester for you.**

> ### 📱 [Download the APK](https://github.com/ZhengySa/SZTU-kebiao/releases/latest)
> Open the latest release, grab `ClassFlow-v*.apk` from **Assets**, and install it (allow "unknown sources").
> No Android Studio, no toolchain setup. Requires Android 8.0 (API 26)+.
> Self-signed build — future versions install right over it and keep your data.

No screenshot-OCR, no CSV massaging, no typing 30 courses by hand. One tap on the school's own login page (captcha and all), and your week is on screen — with a liquid-glass UI, a home-screen widget, grades, and a campus bus timetable on top.

[中文说明](README.md) · [Feature tour](#features) · [How it works](#how-it-works) · [Build](#build)
---

## Features

### Login by opening the school's own page

The app contains **no username field, no password field, and no captcha field**. It opens `auth.sztu.edu.cn` in an embedded WebView; you log in there exactly as you would in a browser (captcha included), and the app takes over the session once it lands in the academic system. Passwords never pass through app code.

### Sync pulls the whole semester

Courses, teachers, classrooms, buildings, week ranges — plus two things most timetable apps make you fill in by hand:

- **Class times**: the school runs 15 periods starting 08:30. They're imported along with the courses, so period 11 doesn't show the wrong time.
- **Semester start**: derived from the school's academic calendar page, so week 1 is correct without you knowing the date.

You get a preview (course count / session count / conflicts) before anything is written, then choose *replace* or *merge*.

### Five tabs

| Tab | What it does |
|------|--------------|
| **Today** | Today's classes as glass cards. Swipe sideways to move through **every day of the semester as one continuous strip** — swipe past Sunday and you land on next Monday. |
| **Week** | The whole week on one screen: 7 days across, 15 periods down, spanning courses as a single block, a red now-line on today's column. Swipe, drag the header, or tap the arrows to change week. |
| **Bus** | Campus shuttle 655 with both weekday and rest-day timetables, next-departure countdown, past departures greyed out. |
| **Map** | Three tabs: campus guide, per-floor classroom maps for C-5 (1F–5F), and the 13 floors of the Sino-German School of Intelligent Manufacturing — searchable by room number. Pinch-zoom and double-tap reset. |
| **Settings** | Semester, period times, sync, grades, notices, backup, widget guide, privacy. |

### Grades

Course name, score, grade, GPA per row. Above the table: total GPA, credits earned, course count, and your official major ranking. A semester chip lets you switch terms; stale sessions fall back to the last cached copy instead of showing nothing.

### Notices (公文通)

The university's announcement board (`nbw.sztu.edu.cn`) built as a native list rather than a WebView:

- Filter by the five channels — Academic / Research / Admin / Student Affairs / Campus — pull to refresh, auto-load the next page at the bottom;
- Open a notice to read the body and attachments; attachments hand off to the system browser;
- **Only reachable from the campus network** (the host resolves to `10.1.12.148`), so the latest 20 notices and anything you've opened are cached — it still works once you leave campus.

The site is a server-rendered BoardWare CMS; parsing keys off `<li>` classes rather than DOM order. If the markup changes, the app says so explicitly instead of quietly showing an empty list.

### Home-screen widget

Jetpack Glance, adapts to 4×2 / 2×4 / 4×4. Shows today's classes with per-course progress and a semester progress bar.

Getting a widget to roll over at midnight is the part nobody warns you about — see [docs/15](docs/15-widget-day-change-and-week-swipe.md).

### And some small things

- **Couple mode**: import your partner's timetable by logging into *their* account once. Sessions are stored separately, their password is never persisted, and a pill in the header flips between the two timetables.
- **Luck box**: a 0–100 daily number that stays stable for the whole day. Entertainment only.
- **Backup**: full JSON export / restore.
- **Class reminders**: exact alarms 10 minutes before class, degrading to a ±10-minute window if the alarm permission is denied.

---

## How it works

**Kotlin · Jetpack Compose (M3) · MVVM + light Clean Architecture · Room · DataStore · Hilt · Coroutines/Flow**

The parts worth reading about:

**Week membership is a bitmask.** `WeekMask` packs weeks 1–60 into a `Long`. "Does this course run in week N?" is `(bits shr (n-1)) and 1`; "do these two sessions overlap in weeks?" is `(a and b) != 0`. Conflict detection needs no nested loops, and no `session_week` join table.

**Times are never hard-coded.** `ScheduleBuilder` recomputes every course's start/end from the *current* period table at render time. Change period 3 from 10:00 to 10:30 in settings and the week grid, the today page, and the widget all shift together.

**The current week is derived, but overridable.** `WeekResolver` uses `floorDiv`, so days before the semester start never produce "week 0" or "week -1". A manual override wins when the calendar page lies.

**`course_session` deliberately has no foreign key to `time_slot`.** It stores `slot_index` only, so editing a period's time can't cascade-destroy historical courses.

**The widget doesn't use WorkManager.** Its delayed jobs get dropped outright by MIUI's background cleaner — that was the actual cause of "it's Wednesday but the widget still shows Tuesday". Instead: `updatePeriodMillis = 30min` (system polling, immune to OEM policies) + a 00:02 AlarmManager tick + a refresh on app start. There's also a real trap here: `java.util.TimeZone` caches the default zone in a static field, so after a timezone change `LocalDate.now()` keeps returning the old date unless you call `TimeZone.setDefault(null)` first.

**Pager state must not depend on the value the pager reports.** `rememberPagerState(initialPage = ...)` sets `initialPage` as a *saveable key* — feed it the current page and every swipe recreates the pager, resetting the page and killing the gesture. Pin the starting page once with `remember`. And never let a `LaunchedEffect(state.week)` scroll the pager while finger-driven swipes also write `state.week`: the two directions fight and you get "swipe, spring back, swipe, spring back". Settled on one direction only — Pager → ViewModel — plus a one-shot request for "jump back to this week".

---

## Security boundaries

| Rule | How it's enforced |
|------|-------------------|
| Only two hosts are reachable | `jwxt.sztu.edu.cn` / `auth.sztu.edu.cn` allow-list, checked on every request **including 302 redirects**; anything else throws |
| Cleartext HTTP only for those two | The school's HTTPS homepage 302s to HTTP, so a scoped `network_security_config.xml` permits it for those domains only |
| **Passwords never touch app code** | Login happens on the school's own page; locally only the session cookie remains |
| Captcha is the school's own | Displayed and validated by their page. No bypass, no OCR |
| No middleman server | The phone talks to the school directly; there is no backend |
| No third-party network SDK | Plain `HttpURLConnection` — nothing to silently exfiltrate data |

The domain list, endpoints, and parsers are **SZTU-specific**. Porting to another school means rewriting `SztuConfig` and the three parsers under `data/remote/sztu/`.

---

## Project layout

```
app/src/main/java/com/classflow/timetable/
├── core/       security (Keystore AES/GCM), time (WeekMath, AppClock), di, ui helpers
├── domain/     pure-Kotlin: models, repositories (interfaces), use cases, import parsers
├── data/
│   ├── remote/sztu/   SztuConfig, DesEcb, MiniHttp, SztuAuthClient,
│   │                  ZfKbParser (timetable), ZfCalendarParser (calendar), ZfGradeParser
│   ├── local/         Room entities/DAOs/DB, DataStore stores
│   ├── mapper/
│   └── repository/
├── widget/     Glance widget, refresh scheduling, system event receivers
├── reminder/   AlarmManager + notifications
├── backup/     JSON export / restore
└── ui/
    ├── theme/  Glass.kt — the liquid-glass design system
    └── screen/ today / week / edit / detail / settings / semester / slots /
                imports / backup / help / sztu / grade / luck
```

Five tables: `semester`, `time_slot`, `course`, `course_session`, `import_source`. `fallbackToDestructiveMigration()` is forbidden — schema changes need a real `Migration`.

---

## Build

| | |
|---|---|
| JDK | 25 (Android Studio's bundled JBR works) |
| Gradle | 9.8.2 |
| AGP | 9.4.1 |
| Kotlin | 2.3.21 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

```bash
./gradlew :app:assembleDebug        # or build.bat on Windows
./gradlew :app:testDebugUnitTest    # 109 unit tests
```

`local.properties` needs `sdk.dir`. The project was built against an Android Studio 2026.2 / JDK 25 toolchain — on an older toolchain you'll need to downgrade AGP and the Gradle wrapper together; see [docs/07](docs/07-build-environment.md) for the version matrix and the traps (AGP 9 bundles Kotlin, so the `kotlin-android` plugin must not be added).

> The distribution URL in `gradle-wrapper.properties` points at a Tencent Cloud mirror, because `services.gradle.org` is unreliable from mainland China. Switch `distributionUrl` back to `https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip` if you prefer the official host.
> `build.bat` only locates `JAVA_HOME` and forwards to `gradlew.bat`; feel free to just run `./gradlew`.

---

## Docs

Design notes live in [`docs/`](docs/) — most of them are post-mortems of specific bugs, because that's where the useful detail is:

| | |
|---|---|
| [01–06](docs/01-prd-and-questions.md) | PRD, tech design, DB schema, pages & navigation, acceptance list |
| [07](docs/07-build-environment.md) | Build environment and version traps |
| [10](docs/10-sztu-sync-and-glass.md) | Login flow, parsing rules, security boundary, the glass design system |
| [12](docs/12-captcha-login-and-partner-timetable.md) | Captcha login, couple mode and owner-scoped data migration |
| [14](docs/14-grades-and-motion.md) | Grades, GPA, ranking; and why the background was re-rasterizing 5 full-screen gradients every frame |
| [15](docs/15-widget-day-change-and-week-swipe.md) | Widget midnight rollover, RemoteViews limits, week swiping |
| [16](docs/16-luck-box-swipe-and-privacy.md) | Pager snap thresholds, the luck distribution, rewriting the privacy page |
| [17](docs/17-continuous-day-pager.md) | Making the day pager one continuous strip instead of 7 pages |
| [18](docs/18-week-swipe-real-root-causes.md) | Why week-swiping "didn't work": pager recreation, two-way feedback, drag distance wiped, stale lambda capture |

---

## License

No license file yet. Until one is added, assume all rights reserved — open an issue if you'd like to use it.
