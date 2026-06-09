# Rytm - Intelligent Habit & Hydration Tracker

**Rytm** is a technical-first Android application designed for high-reliability habit tracking and hydration monitoring. It emphasizes data integrity, battery efficiency, and a reactive architecture to ensure user consistency across all device states.

## Features

### Habit Management
- **Deterministic Scheduling**: Reminders are calculated based on local "wall-clock" time, ensuring consistency across system reboots.
- **Dynamic UX**: Real-time list sorting by priority/time and completion status (completed habits move to the bottom with a strike-through).
- **Manual Recovery**: Supports "back-filling" habit completions for routines missed while the device was off.
- **Intentional Friction Design**: Habit reminders can be completed or postponed, but not skipped directly. This design choice was made to encourage conscious decision-making rather than automatic avoidance.
- **Universal Time Audit**: Audits history on app launch and system reboot, alerting users of missed routines (Habits & Water) that occurred while the device was inactive.

### Hydration Tracker
- **Smart Reminders**: Automated alerts for water intake with a clean, emoji-free notification design.
- **Fulfillment Tracking**: A dedicated `WaterReminderLog` system tracks the status (Completed/Missed) of every scheduled reminder.
- **Hydration Safety**: Enforces daily hydration targets (sum of active reminders or 2000ml minimum) and prevents logging beyond the target to maintain data accuracy.
- **Progress Persistence**: Uses a daily-logging strategy with goal-matching celebration triggers (Confetti).

### Reactive Analytics
- **Live-Update Engine**: UI reacts instantly to database changes using Kotlin Flows.
- **Long-term Performance**: Calculates weekly histograms, current/best streaks, and monthly comparative stats (Current Month vs. Previous Month).
- **Data Portability**: Full JSON backup and restore functionality to safeguard tracking history.

## Architecture

The project implements **Clean Architecture** principles with a focus on the **MVVM + Repository** pattern:

- **View (UI)**: Activities and Fragments using `ViewBinding` for type-safe UI interaction.
- **ViewModel**: State management using `StateFlow` and `SharedFlow` (for one-time events like target alerts).
- **Repository**: Acts as the single source of truth, abstracting Room DAOs and providing unified data streams.
- **Data (Local)**: 
- **Room Database**: Relational storage for habits, reminders, logs, settings, and water logs.
- **No SharedPreferences**: All local flags are stored in a structured `AppSettings` entity in Room for better maintainability and transactional safety.

## Tech Stack

- **Kotlin**: Coroutines and Flow for asynchronous, non-blocking operations.
- **Room**: Persistent storage with support for Auto-Migrations, `@Transaction` operations, and custom `TypeConverters`.
- **Dagger Hilt**: Dependency injection for a modular and testable codebase.
- **MPAndroidChart**: Performance visualization histograms.
- **Material Design 3**: Modern component library for a premium design language.
- **Konfetti**: Particle system for goal-achievement celebrations.

## Challenges Solved

- **Battery Optimization (Lazy Scheduling)**: Implemented a smart scheduling engine that stores the last set time in Room. The app skips redundant system calls if the trigger time hasn't changed, significantly reducing CPU wakeups and preserving battery.
- **The Sleep-Wake Problem**: Implemented `setExactAndAllowWhileIdle` with `Full-Screen Intents` to ensure alarms trigger and display even when the device is in Deep Sleep or locked.
- **Notification Recovery (Missed Reminders)**: Created a startup "Time Audit" logic that detects missed scheduled windows while the device was powered off. This was recently expanded to include Water Reminders, using a new logging table to distinguish between missed and completed intervals.
- **UI Race Conditions (Double-Logging)**: Fixed a critical issue where rapid taps on the "Done" button in the alarm screen would log multiple glasses of water. Implemented a "processing lock" and immediate button disabling to ensure data integrity.
- **Hydration Over-Logging**: Developed a validation layer in the repository to prevent logging water beyond the daily target, providing user feedback via a reactive event bus (`SharedFlow`).
- **Timezone/DST Drift**: Developed a `TimeChangeReceiver` that listens for `ACTION_TIMEZONE_CHANGED`, instantly recalculating UTC trigger times to keep reminders synced globally.
- **Redundant Notification Suppression**: Optimized the audit engine to automatically log missed events upon detection, preventing duplicate alerts on subsequent app launches.
- **Data Import Resilience**: Hardened the JSON restoration logic to handle schema evolution, ensuring backups from older versions of the app remain compatible with new features (like Water Logs).
- **Data Integrity**: Hardened the database layer with `@Transaction` annotations in DAOs, ensuring multi-step updates are atomic and corruption-proof.

## Installation

1.  **Clone**: `git clone https://github.com/yourusername/rytm.git`
2.  **Prerequisites**: Android Studio Jellyfish (or newer) and JDK 17.
3.  **Sync**: Open the project and perform a `Gradle Sync`.
4.  **Permissions**: Ensure `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` are granted within the app startup prompts.

## Future Improvements

- **Cloud Sync**: Integration with Firebase/Supabase for cross-device backup.
- **Home Screen Widgets**: Quick-log buttons for water and top habits.
- **WearOS Support**: Sync reminders and logging to smartwatches.
- **Custom Categories**: Tag habits (Health, Work, Social) with distinct themes.

---

## License

Copyright (c) 2026 Hari. All rights reserved. 
This source code is the exclusive property of the author.
