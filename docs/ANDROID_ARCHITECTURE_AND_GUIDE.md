# Meloberry Android Architecture & Reference

Technical specification covering module structure, `.mbypkg` ingestion, layout parity, player interaction, analysis schema, and persistence guarantees.

---

## 1. Module Dependency Graph

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

- **Tech Stack**: Kotlin, Jetpack Compose Material 3 (Adaptive `NavigationSuiteScaffold`), Hilt, Room, DataStore, `kotlinx.serialization`, Coil (`coil-compose` + `coil-svg` + `coil-gif`), `ru.noties:jlatexmath-android` (zero-WebView native LaTeX), JUnit 4 + `kotlinx.coroutines.test`.
- **Release R8 Configuration**: `app/proguard-rules.pro` preserves `kotlinx.serialization` serializers, Room entities/DAOs, Hilt generated factories, `ViewModel` constructors, and `jlatexmath-android` reflection/font classes when `isMinifyEnabled = true`.

---

## 2. `.mbypkg` Import & Storage Pipeline

`MbypkgArchiveDataSource.extractAndParse` unpacks ZIP archives safely:

1. **Path, Image & Size Validation**:
   - Normalizes entry separators and verifies path segments contain neither `"."` nor `".."` (`assets/fig..1.png` is valid; traversal segments fail).
   - Enforces streaming decompressed byte caps of **50 MB** per ZIP entry and **250 MB** per archive while writing `assets/<filename>` directly to a temporary staging folder on disk.
   - Verifies every `assets/*` payload via `ImageAssetVerifier` (magic bytes for PNG, JPEG, GIF, WebP, BMP, and strict UTF-8 SVG rejecting `<script>`, `<foreignObject>`, `on*=`, `javascript:`, `vbscript:`, `data:text/html`) and requires referenced paths to stay inside `assets/`.
   - Rejects invalid or escaping `manifest.id` values (blank, `"."`, containing `/`, `\`, or `".."`), and parses component `title`, standalone quiz `name`/`description`, and MCQ `promptImagePath`/`explanation`.
2. **Pre-Commit Validation & Atomic Swap**:
   - Decodes `manifest.json`, `workspace.json`, and optional `layouts.json`, then executes `parseWorkspace(...)` completely before touching any existing workspace files in `filesDir/workspaces/<id>/`.
   - Moves staged assets via temporary commit/backup swap directories so failed imports leave pre-existing workspace data intact.
3. **Persistence & Cascade Deletion**:
   - Stores workspace metadata and raw JSON snapshots in `workspaces` (`WorkspaceEntity`), seeded once from `BundledSampleDataSource` on first launch.
   - `DefaultWorkspaceRepository.deleteWorkspace(id)` removes the `workspaces` row, deletes `filesDir/workspaces/<id>/`, deletes matching `analysis_runs` rows via `AnalysisRunDao.deleteByWorkspaceId(id)`, and removes `filesDir/reports/<id>/`.

---

## 3. Navigation, Player & Rendering

### Adaptive Chrome (`MeloberryApp`)
`MeloberryApp` renders a single `MeloberryNavHost` composable inside `NavigationSuiteScaffold`, switching `layoutType` to `NavigationSuiteType.None` on `PlayerDest` so `LibraryDest` and `HistoryDest` back-stack state remains preserved across player transitions.

### Greeting, Pager & Quiz Cards
- **Greeting Screen (`SessionGreetingScreen`)**: Displays the CLI-parity Greeting Card (`MELOBERRY` tab, `title`, `description`, `<N> component(s) • <M> item(s)`, `Start Session →`) and starts session/dwell timers on `Start Session →`.
- **Slide Tracking (`SessionPlayerScreen`)**: Observes `pagerState.settledPage` on both top-level session components and nested `QuizBundleSlideContent` pagers (displaying optional component `title`s), preventing intermediate animation pages from overwriting `currentSlideIndex` or inflating visit counters.
- **MCQ Cards (`McqSlideCard`)**: Supports single-correct and multi-correct answer evaluation, optional `promptImageFilePath`, post-check `explanation` box (matching CLI re-toggle semantics), and 1 or 2 columns (`optionColumns`) spaced by `optionGapDp`.
- **Flashcard Cards (`FlashcardSlideCard`)**: 3D Y-axis flip card (`flipDurationMs`), `Front`/`Back` faces honoring explicit side position (`TextAboveImage`) or inherited `layout.defaultTextPosition`, plus `Got It` / `Needs Review` self-rating buttons.
- **Markdown, Native LaTeX & Assets**:
  - `MarkdownText` + `NativeLatexFormula` (`jlatexmath-android` via Compose `Canvas` & `InlineTextContent`, zero WebView) supports `#`..`######` headings (scaled by `promptFontSizeSp` in articles), `$$...$$` display & `$...$` inline math, bulleted/numbered/nested lists, task lists (`- [ ]`/`- [x]`), multi-line blockquotes, horizontal rules (`---`/`***`/`___`), horizontally scrollable fenced code blocks, aligned GFM tables, strikethrough (`~~...~~`), sanitized clickable links, and inline `***bold italic***`, `**bold**`/`__bold__`, `*italic*`/`_italic_`, and `` `code` `` honoring `contentAlign` (`LEFT`, `CENTER`, `RIGHT`).
  - `MeloberryAssetImage` reuses a singleton `ImageLoader` configured with `SvgDecoder.Factory()` and GIF decoding (`coil-gif`) and opens a full-screen pinch/double-tap zoom dialog on tap.

### Layout Resolution (`BuildSlideDeckUseCase` & `LayoutBoundsCatalog`)
Merges `BaseDefaults -> GlobalLayouts -> ComponentOverrides -> QuestionOverrides`:
- Deck `themeMode` (`berry`, `light`, `dark`, `paper`) and `slideTransitionMs` (`100..1200`) resolve across `mcq`, `article`, and `flashcard` profiles (preferring customized non-default values when any profile omits them).

---

## 4. Session Analysis & Export Lifecycle

- **`SessionAnalysisReport` Schema**: Compatible with Meloberry CLI (`schemaVersion = "1.0"`). When `mcqTotal == 0` (flashcard/article decks), `mcqAccuracyPercent` defaults to `100.0`.
- **Local & SAF Export (`DefaultAnalysisRepository`, Routes)**:
  - Each finished session saves a row in `analysis_runs` and writes `${runId}.analysis.json` and `${runId}.analysis.md` under `filesDir/reports/<workspaceId>/`.
  - `DefaultAnalysisRepository.deleteRun(runId)` removes both the DB record and the corresponding `.analysis.json` / `.analysis.md` files.
  - `AnalysisHistoryRoute` and `SessionPlayerRoute` retain export readiness across configuration changes (`rememberSaveable` awaiting flag + `ViewModel.onExportConsumed()` only after the `CreateDocument` result callback) and wrap share `Intent` launches in `runCatching`.
