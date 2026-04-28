# InfiniteCalc

A calculator for Android with infinite persistent history and full expression evaluation.

---

## Features

- **Full expression evaluation** — powered by mXparser; supports nested parentheses, operator precedence, and complex expressions
- **Two evaluation modes**:
  - *Standard mode* — follows mathematical convention: `−2² = −4`
  - *Negation-first mode* — matches Casio behaviour: `−2² = 4`
- **Infinite history** — every evaluation is saved permanently to a local SQLite database
- **Accordion history** — entries collapse and expand; grouped by date with separators
- **History export** — export the full SQLite database for backup or analysis
- **Config import/export** — save and restore button mappings and settings as JSON
- **Remappable buttons** — reassign any calculator button to a custom function or constant
- **Themes** — dark, light, and system-default
- **Samsung DeX / multi-window** — fully resizable; adapts to desktop and split-screen layouts
- **No ads, no cloud, no tracking**

---

## Building

InfiniteCalc is a native Android app written in Java.

### Prerequisites

- x86-64 Linux (AAPT2 is x86-64 only — ARM build hosts are not supported)
- Python 3
- Java 11 or later
- Android SDK with platform-tools, android-34, and build-tools/34.0.0 (`ANDROID_HOME` must be set)
- Gradle (system-installed, e.g. via SDKMAN)
- imagemagick (`sudo apt install imagemagick`)

### Build

```bash
python3 build_server.py --repo-dir . --output-dir ./out
```

Artifacts will be copied to `./out/`:
- `app-release.apk` — unsigned release APK (sign with your own key for distribution)
- `app-release.aab` — Android App Bundle for Play Store upload

Add `--clean` to force a full scaffold rebuild.

### Running unit tests

The `calculator-logic` module has a pure-JVM test suite that runs without a device or emulator:

```bash
./gradlew :calculator-logic:test
```

---

## Project Structure

```
InfiniteCalc/
├── build.json                        # Build configuration (version, SDK targets)
├── build_server.py                   # Build script — run standalone or as a service
├── icon-512.png                      # Source icon (512×512, used to generate all densities)
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/github/zaegan/infinitecalc/
│       │   ├── MainActivity.java     # UI, button handling, history display
│       │   ├── CalculatorViewModel.java # State management, LiveData
│       │   ├── CalculatorEditText.java  # Custom EditText for expression input
│       │   ├── HistoryAdapter.java   # RecyclerView adapter for history list
│       │   ├── HistoryDatabase.java  # SQLite database (SQLiteOpenHelper)
│       │   ├── HistoryGroup.java     # Date-grouped history entry
│       │   ├── HistoryItem.java      # Single history record
│       │   ├── HistoryListItem.java  # Union type for RecyclerView items
│       │   ├── RemapActivity.java    # Button remapping UI
│       │   ├── RemapConfig.java      # Button mapping persistence (SharedPreferences)
│       │   ├── SettingsDialog.java   # Settings bottom sheet
│       │   ├── TutorialContent.java  # Tutorial step definitions
│       │   └── TutorialManager.java  # Tutorial state and sequencing
│       └── res/
│           ├── layout/               # activity_main, activity_remap, dialog_settings,
│           │                         #   item_history, item_history_step, item_date_separator
│           └── values[-night]/       # colors, strings, themes (dark/light)
└── calculator-logic/                 # Pure Java library module — no Android dependencies
    ├── build.gradle
    └── src/
        ├── main/java/com/github/zaegan/infinitecalc/
        │   ├── MxEvaluator.java      # mXparser wrapper; expression preprocessing and evaluation
        │   ├── CalculatorState.java  # Immutable expression buffer, cursor, smartNegate
        │   └── ExpressionEvaluator.java # Public API used by the app module
        └── test/java/com/github/zaegan/infinitecalc/
            ├── MxEvaluatorTest.java  # 200+ evaluation tests covering both modes
            └── CalculatorStateTest.java # smartNegate, cursor, and input tests
```

---

## Tech Stack

- **Language**: Java (source/target compatibility: Java 8)
- **Evaluation engine**: mXparser 5.2.1
- **UI**: AndroidX — ConstraintLayout, RecyclerView, CardView, ViewModel, LiveData
- **Persistence**: SQLite via `SQLiteOpenHelper` (no Room)
- **Minimum SDK**: API 21 (Android 5.0)
- **Target SDK**: API 34 (Android 14)

---

## License

MIT
