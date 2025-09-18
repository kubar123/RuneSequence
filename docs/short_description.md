# Short Description Documentation

This document provides a brief, at-a-glance summary of the classes in the RuneSequence project.

---

## Core Classes

### `Main`
The application's entry point. It loads all configurations and caches all image assets on startup.

### `TemplateCache`
Loads ability images from disk into a thread-safe memory cache for fast access during detection.

### `ScreenCapture`
A cross-platform utility that captures the screen at high performance using FFmpeg and hardware acceleration.

---

## Configuration (`config` package)

### `ConfigManager`
Loads all JSON configuration files from the user's data directory, creating default files on the first run.

### `AppSettings`
A data class that holds global application settings loaded from `settings.json`.

### `AbilityConfig`
A data class that holds properties for all abilities (e.g., cooldowns) from `abilities.json`.

### `RotationConfig`
A data class that holds named ability sequence `expressions` loaded from `rotations.json`.

### `ScalingConverter`
A simple utility that converts a system's display scaling percentage into an integer for selecting correctly-sized assets.

---

## Screen Capture & Detection (`capture` & `detection` packages)

### `DetectionEngine`
The application's main real-time loop. It runs on a scheduled thread to capture the screen, detect images, and update the sequence state.

### `DetectionResult`
A simple data object that holds the result of a single image search, including its location and confidence.

### `TemplateDetector`
Uses OpenCV to find template images within the screen capture. It supports non-rectangular matching using the image's alpha channel as a mask.

---

## Sequence Management (`sequence` package)

### `SequenceManager`
A thread-safe, high-level class for managing all defined ability sequences and controlling which one is currently active.

### `ActiveSequence`
A stateful object that represents the currently running sequence, tracking its progress and deciding when to advance.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
Immutable classes that act as nodes in an Abstract Syntax Tree (AST) to represent the grammatical structure of a parsed sequence.

### `SequenceParser`
A recursive descent parser that transforms a sequence expression string into its corresponding Abstract Syntax Tree structure.

### `StepTimer`
Enforces ability cooldowns and step durations, ensuring the sequence's timing is synchronized with in-game mechanics.
