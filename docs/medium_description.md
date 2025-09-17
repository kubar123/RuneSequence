# Medium Description Documentation

This document provides a mid-level overview of the classes in the RuneSequence project.

**Note on Missing Components:** The project references `OverlayRenderer`, `Alternative`, and an expression parser, but their source files were not found. Their functionality (drawing overlays, handling OR logic in sequences, and parsing expression strings) has been inferred from usage.

---

## Core Classes

### `Main`
The application's main entry point. It orchestrates the startup by initializing the `ConfigManager` to load all settings and the `TemplateCache` to pre-load all image assets into memory, ensuring the application is fully prepared before the detection loop begins.

### `TemplateCache`
This class loads all ability images from an OS-specific folder into a thread-safe cache. It stores templates as OpenCV `Mat` objects, making them readily available for the `TemplateDetector`. It uses `ScalingConverter` to select the correctly-sized images for the user's display scaling.

### `ScreenCapture`
A high-performance, cross-platform screen capture utility using FFmpeg. It intelligently selects the best capture technology for the OS and attempts to use hardware acceleration. It captures the full screen and performs software cropping to efficiently handle changes in the capture region.

---

## Configuration (`config` package)

### `ConfigManager`
The central class for managing all configuration. It loads and saves settings, abilities, and rotation data from JSON files. On first launch, it automatically creates these files from embedded defaults, ensuring a seamless user setup in the appropriate OS-specific data directory.

### `AppSettings`
A POJO data container for `settings.json`. It holds all global application settings, neatly organized into nested classes for detection parameters (`DetectionSettings`) and the capture area (`RegionSettings`). Jackson is used to map this class to the JSON file.

### `AbilityConfig`
A data class for `abilities.json`. It dynamically loads definitions for any number of abilities into a map, using Jackson's `@JsonAnySetter`. This allows new abilities, with properties like cooldowns, to be added without changing the Java code.

### `RotationConfig`
A data class for `rotations.json` that stores predefined ability sequences ("presets"). Each preset has a name and a string `expression` that defines the sequence's logic. This class provides the raw string input for the (missing) sequence parser.

### `ScalingConverter`
A simple utility that maps a display scaling percentage to a specific integer value. This value is used by `TemplateCache` to locate the folder containing correctly-sized image assets, ensuring reliable detection on high-DPI displays.

---

## Screen Capture & Detection (`capture` & `detection` packages)

### `DetectionEngine`
The core of the application's real-time loop. It runs on a scheduled background thread, periodically capturing the screen, directing the `TemplateDetector` to find the required ability icons, and passing the results to the `SequenceManager` to update the sequence state.

### `DetectionResult`
An immutable value object that cleanly encapsulates the results of a template search. It uses static factory methods (`found`/`notFound`) to clearly indicate whether an ability was located, and bundles the name, location, and confidence of the match.

### `TemplateDetector`
Performs the core computer vision task using OpenCV. Its key feature is the ability to use a PNG's alpha channel as a mask for `matchTemplate`. This allows for precise, non-rectangular matching of uniquely shaped ability icons from the screen capture.

---

## Sequence Management (`sequence` package)

### `SequenceManager`
A thread-safe, high-level manager for all ability sequences. It holds a library of all defined sequences and controls which one is currently active. It acts as the primary interface for the `DetectionEngine`, delegating all processing to the current `ActiveSequence`.

### `ActiveSequence`
A stateful object representing a live, running sequence. It tracks the current step, determines which abilities need to be detected, and uses detection results and the `StepTimer` to decide when it's time to advance to the next step in the sequence.

### `SequenceDefinition`, `Step`, `Term`
These are immutable classes that form the nodes of a parsed Abstract Syntax Tree (AST). They directly represent the grammar of an ability sequence expression, with `SequenceDefinition` as the root, `Step` for `->` steps, and `Term` for `+` (AND) conditions.

### `StepTimer`
A class that manages the cooldown or duration of each step in a sequence. It calculates the required delay based on the cooldowns of the abilities in the current step (including GCDs), ensuring the sequence's timing respects in-game mechanics.
