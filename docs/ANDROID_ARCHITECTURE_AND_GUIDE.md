# Meloberry Android Architecture & Reference (`v2.0.0`)

Technical specification covering module structure, SSOT versioning, `.mbypkg` v1.0/v2.0 ingestion, layout & grading parity with Meloberry CLI `v2.0.0`, player interaction, analysis schema, and persistence guarantees.

---

## 1. Module Dependency Graph & SSOT Versioning

```
:app
 ├── :feature:library  ──┐
 ├── :feature:player   ──┼──▶ :domain ──▶ :core:model
 ├── :feature:history  ──┘       ▲
 │                               │
 ├── :data:workspace  ───────────┼──▶ :core:database ──▶ :core:model
 ├── :data:analysis   ───────────┘
 └── :core:ui ──▶ :core:model
```

- **SSOT Versioning (`gradle/libs.versions.toml` → `GenerateMeloberryVersionInfoTask`)**:
  - `meloberryAppVersion = "2.0.0"` derives `versionCode = 20000` (`MAJOR * 10000 + MINOR * 100 + PATCH`).
  - `meloberrySupportedPackageVersions = "1.0,2.0"` populates `MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS` used by `MbypkgArchiveDataSource`.
  - `meloberryTargetPackageVersion = "2.0"` and `meloberryAnalysisSchemaVersion = "1.0"` keep package exports, bundled samples, and UI version surfaces synchronized from a single source of truth.
- **Tech Stack**: Kotlin 2.0, Jetpack Compose Material 3 (Adaptive `NavigationSuiteScaffold`), Hilt, Room, DataStore, `kotlinx.serialization`, Coil (`coil-compose` + `coil-svg` + `coil-gif`), `ru.noties:jlatexmath-android` (zero-WebView native LaTeX), JUnit 4 + `kotlinx.coroutines.test`.
- **Release R8 Configuration**: `app/proguard-rules.pro` preserves `kotlinx.serialization` serializers, Room entities/DAOs, Hilt generated factories, `ViewModel` constructors, and `jlatexmath-android` reflection/font classes when `isMinifyEnabled = true`.

---

## 2. `.mbypkg` v1.0 & v2.0 Import & Storage Pipeline

`MbypkgArchiveDataSource.stageAndValidate` / `extractAndParse` unpacks ZIP archives safely:

1. **Path, Image & Size Validation**:
   - Accepts `packageVersion` in `MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS` (`"1.0"`, `"2.0"`).
   - Normalizes entry separators, rejects rooted paths and `"."`/`".."` traversal segments (`assets/fig..1.png` is valid), and enforces streaming decompressed caps of **50 MB** per entry and **250 MB** per archive.
   - Verifies every `assets/*` payload via `ImageAssetVerifier` (header-only magic-byte validation for large rasters PNG/JPEG/GIF/WebP/BMP; size-capped strict-UTF8 SVG sanitization rejecting `<script>`, `<foreignObject>`, `on*=`, `javascript:`, `vbscript:`, `data:text/html`) and confines all referenced paths inside `assets/`.
   - Validates workspace-wide case-insensitive `refKey` uniqueness (rejecting reserved positional keys `c\d+` / `q\d+`).
   - Decodes and validates all 4 article block kinds (`Text`, `Image`, `Callout`, `Table`) and all 8 question kinds (`Mcq`, `Flashcard` w/ `isPositionExplicit`, `FillBlank`, `AssertionReason`, `SpotError`, `Differentiation`, `Mechanism`, `RelabelImage` w/ normalized box bounds `x + w <= 1.001`, `y + h <= 1.001`).
2. **Pre-Commit Validation & Atomic Swap**:
   - Validates `manifest.json`, `workspace.json`, and `layouts.json` completely in staging before touching existing files in `filesDir/workspaces/<id>/`.
   - `DefaultWorkspaceRepository` writes Room metadata first (`upsertWorkspace`), then commits staged assets atomically so failed DB transactions never leave corrupted asset directories.
3. **Persistence & Cascade Deletion**:
   - Seeds the v2.0 showcase workspace once from `BundledSampleDataSource` on first launch.
   - `DefaultWorkspaceRepository.deleteWorkspace(id)` removes the `workspaces` row, deletes `filesDir/workspaces/<id>/`, deletes matching `analysis_runs` rows via `AnalysisRunDao.deleteByWorkspaceId(id)`, and removes `filesDir/reports/<id>/`.

---

## 3. Navigation, Player, Search & Rendering

### Adaptive Chrome & App Theme (`MainActivity`, `MeloberryApp`)
- `MainActivity` handles incoming `ACTION_VIEW` intents for `.mbypkg` files and observes `PreferencesDataStoreSource.appThemeMode` (`BERRY`, `LIGHT`, `DARK`, `PAPER`), selectable from `LibraryScreen`'s palette menu alongside the `v2.0.0` version badge.
- `MeloberryApp` hosts `NavigationSuiteScaffold`, switching `layoutType` to `NavigationSuiteType.None` on `PlayerDest` so `LibraryDest` and `HistoryDest` back-stack state stays intact.

### Article & Interactive Question Cards (`:feature:player`)
- **Greeting Screen (`SessionGreetingScreen`)**: Displays `MELOBERRY` tab, `title`, `description`, `<N> component(s) • <M> item(s)`, and starts session/dwell timers on `Start Session →`.
- **Upgraded Top Bar & In-Session Search (`SessionSearchModal`)**: Displays active slide position (`current / total`), live graded score pill, and a Search trigger (also bound to `Ctrl+K` / `/`) backed by `SessionSearchIndex` across text, callouts, tables, and all 8 question payloads with highlighted snippets and direct jump to nested quiz questions.
- **Articles (`ArticleSlideCard`)**:
  - `TEXT`: Full GFM Markdown + native LaTeX (`MarkdownText` + `NativeLatexFormula`).
  - `IMAGE`: Zoomable `MeloberryAssetImage` with `alt` accessibility description and Markdown/LaTeX `caption`.
  - `CALLOUT` (`CalloutBlockCard`): Toned left accent bar & icon for `pearl`, `clinical`, and `warning` across all 4 themes.
  - `TABLE` (`InteractiveTableBlockCard`): Horizontally scrollable grid with `stickyFirstCol` split layout and `hideableCols` active-recall toggle pills + tap-to-peek cells.
- **Interactive Question Cards**:
  - `McqSlideCard`: Single/multi-correct options, prompt/option media, 1–2 column grid, post-check explanation.
  - `FlashcardSlideCard`: 3D Y-axis flip card honoring `side.isPositionExplicit` before falling back to `layout.defaultTextPosition`, plus `Got It` / `Needs Review` ratings.
  - `FillBlankSlideCard`: Inline/stacked slot inputs with exact/fuzzy badges, per-slot & question `"Override: Mark Correct"`, and math/code-preserving badge rendering.
  - `AssertionReasonSlideCard`: Styled Assertion/Reason banners with `4opt` / `5opt` choices.
  - `SpotErrorSlideCard`: Flow-wrapped tappable candidate spans, `"No error"` toggle, dynamic `requireFix` inputs, and strikethrough → correction reveal.
  - `DifferentiationSlideCard`: Tailored comparison table for `fill` (with word bank + override), `spot-wrong` (with `allowNoError`), and `rearrange` (tap-to-swap shuffled cells).
  - `MechanismSlideCard`: Deterministically shuffled step bank, sequence builder with Up/Down/Remove controls, distractor exclusion, and parallel `orderGroup` grading.
  - `RelabelImageSlideCard`: Aspect-ratio-locked diagram with normalized box overlays across `solid`, `blur`, `pixelate`, and `whiteout` mask styles, `reveal`/`bank`/`recall` modes, and a zoomable lightbox preserving mask overlays.

### Layout Resolution & Grading Engine (`:domain`)
- `BuildSlideDeckUseCase` + `LayoutBoundsCatalog` merge `BaseDefaults -> GlobalLayouts -> ComponentOverrides -> QuestionOverrides`. All 6 v2.0 question types inherit `LayoutTargetType.MCQ`; tables and callouts inherit `LayoutTargetType.ARTICLE`.
- `RecallAnswerMatcher` (Greek symbol normalization, punctuation/dash compaction, Levenshtein threshold `length >= 6 && dist <= 1`) and `DeterministicShuffler` (FNV-1a + LCG) match CLI `SlidingWebAppGenerator.cs` bit-for-bit.
- Dedicated evaluators (`EvaluateMcqAnswerUseCase`, `EvaluateFillBlankUseCase`, `EvaluateAssertionReasonUseCase`, `EvaluateSpotErrorUseCase`, `EvaluateDifferentiationUseCase`, `EvaluateMechanismUseCase`, `EvaluateRelabelImageUseCase`) run purely on the JVM.

---

## 4. Session Analysis & Export Lifecycle

- **`SessionAnalysisReport` Schema (`schemaVersion = "1.0"`)**: Aggregates `GRADED_QUESTION_KINDS` (`mcq`, `fill-blank`, `assertion-reason`, `spot-error`, `diff`, `mechanism`, `relabel`) into summary counters, `weakItemIds`, and CLI-identical Markdown (`- **Graded Questions:** ...`).
- **Telemetry & Process-Death Survival (`SessionPlayerViewModel`)**: Pauses dwell timers on `ON_PAUSE`/`ON_STOP`, persists full runtime state for all 8 question kinds into `SavedStateHandle`, and updates the existing `savedRunId` in place when re-finishing a session.
- **Local & SAF Export (`DefaultAnalysisRepository`, Routes)**: Writes `${runId}.analysis.json` and `${runId}.analysis.md` under `filesDir/reports/<workspaceId>/`, filters and aggregates graded runs in `AnalysisHistoryScreen`, and preserves SAF export payloads across configuration changes via `PendingDocumentExportEffect`.
