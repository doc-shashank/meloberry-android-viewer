# Meloberry Android

Android client application (`v2.0.1`) for studying offline `.mbypkg` (`v1.0` & `v2.0`) study packages exported by the Meloberry CLI (`mby`), featuring adaptive navigation, themed Markdown/LaTeX/SVG rendering, interactive active-recall tables & callouts, all 8 native question types, in-session search, and persistent session analysis tracking.

## Documentation

- [ANDROID_ARCHITECTURE_AND_GUIDE.md](docs/ANDROID_ARCHITECTURE_AND_GUIDE.md) — Module graph, SSOT versioning, `.mbypkg` v1.0/v2.0 ingestion and staging pipeline, grading engine parity, player UX, and Room/SAF persistence.

---

## Modules

| Module | Role |
| :--- | :--- |
| `:app` | Entry activity (`ACTION_VIEW` `.mbypkg` handler), persisted app theme shell, Hilt application, adaptive `NavigationSuiteScaffold` (`MeloberryApp`), release ProGuard rules |
| `:core:model` | SSOT generated `MeloberryVersionInfo` (`2.0.1`), immutable domain models (`StudyWorkspace`, `SlideUiModel`, `QuestionPayloads`, `SessionAnalysisReport`, layouts) |
| `:core:database` | Room entities/DAOs (`workspaces`, `analysis_runs`) and DataStore preferences (`sample_seeded`, `app_theme_mode`) |
| `:core:ui` | Compose themes (`berry`, `light`, `dark`, `paper`) with semantic callout & fuzzy-recall tokens, `MarkdownText`, native LaTeX (`jlatexmath-android`), shared Coil SVG/GIF `MeloberryAssetImage` lightbox |
| `:data:workspace` | Staged `.mbypkg` v1.0/v2.0 archive parser (`MbypkgArchiveDataSource`), `BundledSampleDataSource` v2.0 showcase, Room persistence, cascade workspace cleanup |
| `:data:analysis` | `SessionAnalysisReport` JSON/Markdown serialization, local report storage, SAF export |
| `:domain` | `BuildSlideDeckUseCase`, `RecallAnswerMatcher`, `DeterministicShuffler`, all 7 question evaluators (`EvaluateMcqAnswerUseCase` + 6 v2.0 `Evaluate*UseCase`), `BuildAnalysisReportUseCase`, `AggregateAnalysisHistoryUseCase` |
| `:feature:library` | `.mbypkg` SAF importer, version badge (`v2.0.1`), app theme selector, bundled sample seeding, workspace library & delete confirmation |
| `:feature:player` | Fullscreen session player (`HorizontalPager`), in-session full-text search (`Ctrl+K` / `/`), upgraded `ArticleSlideCard` (Text, Image+caption, Callouts, Sticky/Hideable Tables), all 8 interactive question cards, finish summary |
| `:feature:history` | Analysis run browser, per-workspace filter, cross-run accuracy aggregation (graded runs only), SAF `.json`/`.md`/Share export & delete cascade |

---

## Key Capabilities & CLI v2.0.0 Parity

- **Single Source of Truth Versioning**: `gradle/libs.versions.toml` defines `meloberryAppVersion = "2.0.1"` (`versionCode = 20001`), `meloberrySupportedPackageVersions = "1.0,2.0"`, and `meloberryAnalysisSchemaVersion = "1.0"`, generating `MeloberryVersionInfo` at build time.
- **Zero-WebView Native Rendering**: 100% Jetpack Compose Material 3 with native `jlatexmath-android` LaTeX math (`$...$` and `$$...$$`), Coil SVG + animated GIF decoding, and full GFM Markdown (task lists, strikethrough, sanitized links, aligned tables).
- **All 4 Article Block Types**: `Text`, `Image` (with `alt` + Markdown/LaTeX `caption`), `Callout` (`pearl`, `clinical`, `warning`), and interactive `Table` (`stickyFirstCol` + active-recall `hideableCols` with tap-to-peek).
- **All 8 Interactive Question Types**: `Mcq` (single & multi-correct), 3D `Flashcard` (honoring `isPositionExplicit`), `FillBlank` (Greek/fuzzy matching + per-slot & question overrides), `AssertionReason` (`4opt` / `5opt`), `SpotError` (`requireFix` + `allowNoError`), `Differentiation` (`fill` w/ word bank, `spot-wrong`, `rearrange`), `Mechanism` (ordered step builder supporting parallel `orderGroup` steps & distractors), and `RelabelImage` (aspect-ratio-locked `solid`/`blur`/`pixelate`/`whiteout` masks across `reveal`/`bank`/`recall` modes + zoomable lightbox).
- **Safe `.mbypkg` Ingestion & Atomic Commits**: Validates `packageVersion` (`1.0`/`2.0`), manifest/component/question IDs, workspace-wide case-insensitive `refKey` uniqueness, payload invariants, normalized `RelabelBox` bounds, `assets/` confinement, magic-byte & sanitized-SVG checks (`ImageAssetVerifier`), and 50 MB / 250 MB decompressed caps inside a staging folder before atomic swap.
- **In-Session Search & Telemetry Parity**: Instant tokenized full-text search (`SessionSearchModal`) jumping directly to articles or nested quiz questions; dwell time pauses on `ON_PAUSE`/`ON_STOP`, survives process death via `SavedStateHandle` JSON snapshots, and exports CLI-identical `.analysis.json` / `.analysis.md` reports across all 7 graded question kinds.

---

## Build & Verification

```bash
# Run JVM unit tests across all modules (from Project/Android)
./gradlew :core:model:test :domain:test :data:workspace:testDebugUnitTest :data:analysis:testDebugUnitTest :feature:player:testDebugUnitTest

# Assemble debug APK
./gradlew :app:assembleDebug

# Assemble R8-minified release APK (uses app/proguard-rules.pro)
./gradlew :app:assembleRelease
```
