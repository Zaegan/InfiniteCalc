# Android Calculator with Infinite History

A calculator app with standard features plus infinite history of all evaluations.

## Features

- **Standard calculator operations**: +, -, *, /, %, =
- **Decimal support**: Enter decimal numbers
- **Infinite history**: All evaluations saved permanently
- **Accordion history**: Collapsible history entries
- **Smart saving**: Only saves valid evaluations (no syntax errors)
- **No delete**: History cannot be deleted, preserves all use from beginning
- **Draft filtering**: Partial drafts not saved, only complete valid expressions

## History Behavior

- When you hit **"Clear"**: Current draft is saved to history as a new entry
- When you hit **"Enter" / "="**: Current expression is evaluated and result saved
- Multi-digit typing doesn't flood history (each valid evaluation only)
- Syntax errors before hitting Enter are NOT saved
- Once evaluated successfully, the result is saved to history

## Project Structure

```
android-calculator/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/calculator/
│   │       │   ├── MainActivity.java
│   │       │   ├── HistoryAdapter.java
│   │       │   ├── HistoryItem.java
│   │       │   └── ExpressionEvaluator.java
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   └── item_history.xml
│   │       │   └── values/
│   │       │       ├── strings.xml
│   │       │       └── themes.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Tech Stack

- Language: Java
- Minimum SDK: API 21 (Android 5.0)
- Target SDK: API 34 (Android 14)

## Usage

1. Enter a mathematical expression using the calculator buttons
2. Tap "=" to evaluate - result appears and is saved to history
3. Tap "Clear" - current entry is saved to history (even if incomplete)
4. Swipe down on history to expand/collapse entries
5. History persists forever - no delete option

## Dependencies

- None required (uses AndroidX Core)
