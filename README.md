# Rytm - Intelligent Habit & Hydration Tracker

**Rytm** is a technical-first Android application designed for high-reliability habit tracking and hydration monitoring. It emphasizes data integrity, battery efficiency, and a reactive architecture to ensure user consistency across all device states.

## 🚀 Features

### 📅 Habit Management
- **Deterministic Scheduling**: Reminders are calculated based on local "wall-clock" time, ensuring consistency across system reboots.
- **Dynamic UX**: Real-time list sorting by priority/time and completion status (completed habits move to the bottom with a strike-through).
- **Manual Recovery**: Supports "back-filling" habit completions for routines missed while the device was off.
- **Notification Recovery**: Audits history on app launch/reboot and alerts users of missed routines.

### 💧 Hydration Tracker
- **Granular Logging**: Supports specific water volume targets at user-defined time intervals.
- **Progress Persistence**: Uses a daily-logging strategy with goal-matching celebration triggers (Confetti).

### 📊 Reactive Analytics
- **Live-Update Engine**: UI reacts instantly to database changes using Kotlin Flows.
- **Long-term Performance**: Calculates weekly histograms, current/best streaks, and monthly comparative stats (Current Month vs. Previous Month).

## 🏗️ Architecture

The project implements **Clean Architecture** principles with a focus on the **MVVM + Repository** pattern:

- **View (UI)**: Activities and Fragments using `ViewBinding` for type-safe UI interaction.
- **ViewModel**: State management using `StateFlow` and `combine` to merge multiple data sources into a single reactive UI state.
- **Repository**: Acts as the single source of truth, abstracting Room DAOs and providing unified data streams.
- **Data (Local)**: 
    - **Room Database**: Relational storage for habits, reminders, logs, and settings.
    - **No SharedPreferences**: Migrated all local flags to a structured `AppSettings` entity in Room for better maintainability and transactional safety.

## 🛠️ Tech Stack

- **Kotlin**: Coroutines and Flow for asynchronous, non-blocking operations.
- **Room**: Persistent storage with support for Auto-Migrations and `@Transaction` operations.
- **Dagger Hilt**: Dependency injection for a modular and testable codebase.
- **MPAndroidChart**: performance visualization histograms.
- **Material Design 3**: Modern component library for a premium design language.
- **Konfetti**: Particle system for goal-achievement celebrations.

## 🧠 Challenges Solved

- **Battery Optimization (Lazy Scheduling)**: Implemented a smart scheduling engine that stores the last set time in Room. The app skips redundant system calls if the trigger time hasn't changed, significantly reducing CPU wakeups and preserving battery.
- **The Sleep-Wake Problem**: Implemented `setExactAndAllowWhileIdle` with `Full-Screen Intents` to ensure alarms trigger and display even when the device is in Deep Sleep or locked.
- **Refined Notification UX**: Optimized notification strings to eliminate redundancy. Titles follow a clean `[Emoji] Time for [Name]` format, and body text was removed to focus on the full-screen prompt, reducing cognitive load.
- **Timezone/DST Drift**: Developed a `TimeChangeReceiver` that listens for `ACTION_TIMEZONE_CHANGED`, instantly recalculating UTC trigger times to keep reminders synced globally.
- **Notification Recovery**: Created a startup "Time Audit" logic that detects missed scheduled windows while the device was powered off, triggering a summary notification to the user.
- **Data Race Conditions**: Hardened the database layer with `@Transaction` annotations in DAOs, ensuring multi-step updates are atomic and corruption-proof.

## ⚙️ Installation

1.  **Clone**: `git clone https://github.com/yourusername/rytm.git`
2.  **Prerequisites**: Android Studio Jellyfish (or newer) and JDK 17.
3.  **Sync**: Open the project and perform a `Gradle Sync`.
4.  **Permissions**: Ensure `SCHEDULE_EXACT_ALARM` is granted within the app startup prompt.

## 🔮 Future Improvements

- **Cloud Sync**: Integration with Firebase/Supabase for cross-device backup.
- **Home Screen Widgets**: Quick-log buttons for water and top habits.
- **WearOS Support**: Sync reminders and logging to smartwatches.
- **Custom Categories**: Tag habits (Health, Work, Social) with distinct themes.

---

## 📜 License

Copyright (c) 2026 Hari. All rights reserved. 
This source code is the exclusive property of the author.
