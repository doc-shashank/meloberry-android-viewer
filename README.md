# Meloberry Android

Android client application for studying offline `.mbypkg` study packages exported by the Meloberry CLI (`mby`), featuring adaptive navigation, themed Markdown/SVG rendering, 3D flashcard flipping, interactive MCQ evaluation, and persistent session analysis tracking.

## Documentation

- [ANDROID_ARCHITECTURE_AND_GUIDE.md](docs/ANDROID_ARCHITECTURE_AND_GUIDE.md) — Module graph, `.mbypkg` ingestion and staging pipeline, layout resolution, player telemetry, and Room/SAF persistence.

---

## Modules

| Module | Role |
| :--- | :--- |
| `:app` | Entry activity, Hilt application, adaptive `NavigationSuiteScaffold` (`MeloberryApp`), release ProGuard rules |
| `:core:model` | Immutable domain models (`StudyWorkspace`, `SlideUiModel`, `SessionAnalysisReport`, layouts) |
| `:core:database` | Room entities/DAOs (`workspaces`, `analysis_runs`) and DataStore preferences |
| `:core:ui` | Compose themes (`berry`, `light`, `dark`, `paper`), `MarkdownText`, shared Coil SVG `MeloberryAssetImage` lightbox |
| `:data:workspace` | Staged `.mbypkg` archive parser (`MbypkgArchiveDataSource`), Room persistence, cascade workspace cleanup |
| `:data:analysis` | `SessionAnalysisReport` JSON/Markdown serialization, local report storage, SAF export |
| `:domain` | `BuildSlideDeckUseCase`, `EvaluateMcqAnswerUseCase`, `BuildAnalysisReportUseCase`, `AggregateAnalysisHistoryUseCase` |
| `:feature:library` | `.mbypkg` SAF importer, bundled sample seeding, workspace library & delete actions |
| `:feature:player` | Fullscreen session player (`HorizontalPager`), 1–2 column MCQ cards, 3D flashcard cards, finish summary |
| `:feature:history` | Analysis run browser, cross-run accuracy aggregation, SAF export & delete cascade |

---

## Key Capabilities & Parity

- **Safe `.mbypkg` Ingestion**: Validates manifest workspace IDs against path escapes and `.` / `..`, parses and validates `workspace.json` inside a staging directory before modifying existing files, streams assets directly to disk with **50 MB** per-entry and **250 MB** total decompressed limits, and permits double-dot asset filenames (e.g., `fig..1.png`).
- **Layout Resolution**: Applies 3-tier layout merging (`base -> global -> component -> question`) across `themeMode`, `slideTransitionMs` (resolving across `mcq`, `article`, `flashcard`), 1–2 column MCQ option grids (`optionColumns`), and flashcard `defaultTextPosition`.
- **Player & Export Lifecycle**: Preserves navigation/list state across fullscreen player transitions, commits page telemetry on `settledPage`, survives configuration changes during SAF export via `rememberSaveable` + `ViewModel`, and cascades deletions across Room rows and `filesDir/reports/`.

---

## Build & Verification

```bash
# Run JVM unit tests across all modules (from Project/Android)
./gradlew testDebugUnitTest --offline

# Assemble debug APK
./gradlew :app:assembleDebug

# Assemble R8-minified release APK (uses app/proguard-rules.pro)
./gradlew :app:assembleRelease
```
