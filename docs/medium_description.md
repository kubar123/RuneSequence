# Medium Form Documentation

This document provides a concise description of the classes and methods in the RuneSequence project.

---

## Core Components

### `Main`
The application's entry point. It initializes all core components like configuration, template caching, screen capture, and the detection engine. It parses ability rotations, starts the main detection loop, and ensures a graceful shutdown of all resources.

- **`main(String[] args)`**: Orchestrates the application startup, wires all components together, and starts the detection process.
- **`populateTemplateCache()`**: Initializes the template cache from image assets.
- **`populateSettings()`**: Initializes the configuration manager from JSON files.

### `DetectionEngine`
The heart of the application, running a loop to capture the screen, detect ability icons via `TemplateDetector`, process the findings against the active rotation in `SequenceManager`, and update the on-screen `OverlayRenderer`. It operates on a dedicated thread for non-blocking performance.

- **`start()`**: Starts the main detection thread.
- **`stop()`**: Gracefully stops the detection thread.
- **`processFrame()`**: The core method executed each cycle to perform detection and update state.

---

## Configuration (`.config`)

### `ConfigManager`
Manages loading and saving of all JSON configurations (settings, abilities, rotations). It creates default configs on first launch and provides a central access point for all configuration data. It intelligently finds the correct OS-specific folder for storing its data.

- **`initialize()`**: Loads all configurations from disk or creates them from bundled defaults.
- **`getAppDataPath()`**: A utility to locate the system's standard application data directory.

### `AppSettings`, `AbilityConfig`, `RotationConfig`
These are POJO classes that map directly to the JSON configuration files (`settings.json`, `abilities.json`, `rotations.json`). They act as structured, type-safe containers for application settings, ability metadata (cooldowns, etc.), and defined rotation presets, respectively.

---

## Screen Capture and Detection (`.capture`, `.detection`)

### `ScreenCapture`
A service responsible for grabbing frames from the screen using FFmpeg via JavaCV. It can capture the full screen or a specified region and provides the output as an OpenCV `Mat` object, ready for image processing.

- **`captureScreen()`**: The primary method to perform a screen grab and return the image data.

### `TemplateDetector`
Finds ability icons on the screen by matching them against pre-loaded template images. It uses OpenCV's `matchTemplate` and employs optimizations like searching in the last known location first to improve performance. It also correctly handles templates with transparency.

- **`detectTemplate(Mat, String)`**: Searches the screen for a given template by name.
- **`findBestMatch(...)`**: The internal worker method that executes the core OpenCV matching logic and manages native memory.

### `TemplateCache`
Loads all template images from a directory into memory at startup to avoid repeated disk access. It stores them as OpenCV `Mat` objects in a simple map, providing fast, on-demand access for the `TemplateDetector`.

### `OverlayRenderer`
Draws visual guides on the screen, such as borders around abilities, to provide feedback to the user. It uses a transparent, always-on-top window to render these overlays without interfering with other applications.

---

## Sequence Logic (`.sequence`)

### `SequenceParser`
Transforms a rotation defined as a string (e.g., "Ability1 > Ability2") into a structured `SequenceDefinition` object. It uses a tokenizer and a recursive descent algorithm to build an abstract syntax tree representing the complex rotation logic.

### `SequenceManager`
Manages the state of the active ability sequence. It holds all parsed rotations and uses the `ActiveSequence` to track progress. It receives detection results from the `DetectionEngine` and determines the current and next abilities to be highlighted.

### `ActiveSequence`
Represents the live, running instance of a `SequenceDefinition`. It tracks which step of the sequence is currently active and advances to the next step when its conditions (i.e., required abilities are detected) are met.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
A set of data classes that form the nodes of the abstract syntax tree for a rotation. They represent the hierarchical structure of a sequence, from the whole rotation down to individual, alternative abilities, enabling complex logic.
