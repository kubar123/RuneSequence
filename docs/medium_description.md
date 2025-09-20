# Medium Description Documentation

This document provides a mid-level overview of the classes in the RuneSequence project.

---
## Core (`com.lansoftprogramming.runeSequence`)

### `Main`
The application's main entry point. It orchestrates the startup by initializing configurations, caching image templates, setting up the core detection and rendering components, and activating a default ability sequence. It ensures a graceful shutdown by using a runtime hook to release all resources.

### `TemplateCache`
A thread-safe cache that loads ability images (templates) from the correct, OS-specific, and scale-specific directory. It stores them as OpenCV `Mat` objects in a `ConcurrentHashMap`, ensuring they are readily available for the detection engine. It clones `Mat` objects to ensure data integrity.

---
## Configuration (`...config`)

### `ConfigManager`
The central class for managing all configuration. It loads and saves settings, abilities, and rotation data from JSON files. On first launch, it automatically creates these files from embedded defaults, ensuring a seamless user setup in the appropriate OS-specific data directory.

### `AppSettings`
A POJO data container for `settings.json`. It holds all global application settings, neatly organized into nested classes for detection parameters (`DetectionSettings`) and the capture area (`RegionSettings`).

### `AbilityConfig`
A data class for `abilities.json`. It dynamically loads definitions for any number of abilities into a map. This allows new abilities, with properties like cooldowns, to be added without changing the Java code.

### `RotationConfig`
A data class for `rotations.json` that stores predefined ability sequences ("presets"). Each preset has a name and a string `expression` that defines the sequence's logic.

### `ScalingConverter`
A simple utility that maps a display scaling percentage to a specific integer value. This value is used by `TemplateCache` to locate the folder containing correctly-sized image assets, ensuring reliable detection on high-DPI displays.

---
## Screen Capture & Detection (`...capture` & `...detection`)

### `ScreenCapture`
A high-performance, cross-platform screen capture utility using FFmpeg. It intelligently selects the best capture technology for the OS and attempts to use hardware acceleration. It captures the full screen and performs software cropping to efficiently handle changes in the capture region.

### `TemplateDetector`
Performs the core computer vision task using OpenCV. Its key feature is the ability to use a PNG's alpha channel as a mask for `matchTemplate`. This allows for precise, non-rectangular matching of uniquely shaped ability icons. It also includes an optimization to search in the last known location first.

### `DetectionEngine`
The core of the application's real-time loop. It runs on a scheduled background thread, periodically capturing the screen, directing the `TemplateDetector` to find the required ability icons, and passing the results to the `SequenceManager` to update the sequence state and `OverlayRenderer` to update the UI.

### `DetectionResult`
An immutable value object that cleanly encapsulates the results of a template search. It uses static factory methods (`found`/`notFound`) to clearly indicate whether an ability was located, and bundles the name, location, and confidence of the match.

### `OverlayRenderer`
Creates a transparent, click-through `JWindow` to display colored borders around detected abilities. It uses different colors and thicknesses to distinguish between "current", "next", and "alternative" (OR) abilities, providing clear, non-intrusive visual feedback on the sequence state.

---
## Sequence Management (`...sequence`)

### `SequenceManager`
A thread-safe, high-level manager for all ability sequences. It holds a library of all defined sequences and controls which one is currently active. It acts as the primary interface for the `DetectionEngine`, delegating all processing to the current `ActiveSequence`.

### `ActiveSequence`
A stateful object representing a live, running sequence. It tracks the current step, determines which abilities need to be detected, and uses detection results and the `StepTimer` to decide when it's time to advance to the next step in the sequence.

### `SequenceParser`
A standard recursive descent parser responsible for transforming an expression string from `RotationConfig` into a `SequenceDefinition` Abstract Syntax Tree (AST). It handles operator precedence and parenthesized groups, forming the bridge between the user's configuration and the application's runtime representation.

### `StepTimer`
A class that manages the cooldown or duration of each step in a sequence. It calculates the required delay based on the cooldowns of the abilities in the current step (including GCDs), ensuring the sequence's timing respects in-game mechanics.

### `Tokenizer`
A class that breaks down the raw sequence expression string into a list of `Token` objects. It correctly handles ability names with spaces and supports multiple operator symbols (e.g., `->` and `→`).

### `Token`
A `sealed interface` with `record` implementations that provides a strongly-typed, immutable representation of the parts of a sequence expression (e.g., `Ability`, `Operator`).

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
A set of immutable classes that form the nodes of a parsed Abstract Syntax Tree (AST). They directly represent the grammar of an ability sequence expression, from the root `SequenceDefinition` down to individual `Alternative`s.
