# Long Form Documentation

This document provides a detailed description of the classes and methods in the RuneSequence project.

---

## Core Components

### `Main`
The `Main` class is the application's entry point. It orchestrates the entire setup process by initializing and wiring together all major components. It begins by loading configurations (`ConfigManager`) and caching image templates (`TemplateCache`). Subsequently, it sets up core services like `ScreenCapture`, `TemplateDetector`, and `OverlayRenderer`. A crucial role is parsing the ability rotation presets from the configuration files and feeding them into the `SequenceManager`. Finally, it starts the main `DetectionEngine` loop and registers a shutdown hook to ensure all native resources and threads are gracefully terminated on exit, preventing resource leaks.

- **`main(String[] args)`**: The static main method that starts the application. It handles the high-level flow of initialization, starts the detection engine, and blocks until the application is terminated.
- **`populateTemplateCache()`**: Initializes the `TemplateCache`.
- **`populateSettings()`**: Initializes the `ConfigManager` and loads all necessary settings from JSON files.

### `DetectionEngine`
The `DetectionEngine` is the central processing unit of the application, running a continuous loop to analyze the screen for ability icons. It uses the `ScreenCapture` service to grab frames from the screen at a configurable interval. Each frame is then passed to the `TemplateDetector` to find all relevant ability icons. The detection results are fed into the `SequenceManager`, which evaluates them against the active ability rotation logic. Finally, it uses the `OverlayRenderer` to draw visual feedback on the screen, such as highlighting the next ability to be used. The engine runs on a dedicated thread, which can be started and stopped cleanly.

- **`start()`**: Begins the detection loop in a new thread.
- **`stop()`**: Stops the detection loop and waits for the thread to terminate.
- **`processFrame()`**: The core logic of the loop: captures a screen frame, runs detection, processes the results through the sequence manager, and updates the UI overlays.
- **`updateOverlays()`**: Gathers the current state from the `SequenceManager` and passes it to the `OverlayRenderer` to be displayed.

---

## Configuration (`com.lansoftprogramming.runeSequence.config`)

### `ConfigManager`
This class is responsible for managing all application configurations. It handles loading, creating, and saving settings, ability data, and rotation presets from JSON files stored in a dedicated application directory (`%APPDATA%/RuneSequence` on Windows). On first run, it creates default configuration files from resources bundled with the application. It uses the Jackson library for JSON serialization and deserialization, configured to handle modern Java types like `Instant`. The manager provides a single point of access for other components to retrieve configuration values, such as detection thresholds or rotation logic.

- **`initialize()`**: Sets up the configuration directory and loads all configuration files, creating them from defaults if they don't exist.
- **`saveSettings()` / `saveRotations()` / `saveAbilities()`**: Persists the respective configuration objects to their JSON files on disk.
- **`getAppDataPath()`**: A static utility method that determines the correct application data directory based on the operating system (Windows, macOS, or Linux).
- **`loadDefaultResource(String, Class)`**: A generic helper method to load and parse default JSON configuration files from the application's resources.

### `AppSettings`
A simple Plain Old Java Object (POJO) that models the main application settings (`settings.json`). It holds information about the version, window region settings, and detection parameters. It is designed for easy serialization and deserialization to/from JSON using Jackson. Includes getters and setters for all properties.

### `AbilityConfig` and `AbilityData`
`AbilityConfig` is a container for all known abilities, mapping ability names (e.g., "limitless") to their `AbilityData`. This class is populated from `abilities.json`. `AbilityData` is a POJO that stores metadata for a single ability, such as its cooldown, cast duration, and whether it triggers the global cooldown (GCD). This information is crucial for the `SequenceManager` to make decisions. It also allows for a custom detection threshold per ability, overriding the global default.

### `RotationConfig` and `PresetData`
`RotationConfig` acts as a container for different ability rotation presets, loaded from `rotations.json`. It maps a preset name (e.g., "debug-limitless") to its `PresetData`. `PresetData` holds the name of the preset and the string-based expression that defines the sequence of abilities (e.g., "Ability1 > (Ability2 | Ability3) + Ability4"). This expression is what the `SequenceParser` consumes to build a runnable sequence.

---

## Screen Capture and Detection (`.capture`, `.detection`)

### `ScreenCapture`
This class handles the task of capturing the screen content. It uses the `JavaCV` (a wrapper for FFmpeg) library to grab frames from the primary display. It includes logic to detect the operating system and enable hardware acceleration where possible to improve performance. The class can capture the full screen or a specific rectangular region. The captured frames are returned as OpenCV `Mat` objects, which is the standard format used by the `TemplateDetector` for image processing. It runs its capture logic in a managed way, allowing it to be started and stopped.

- **`captureScreen()`**: Grabs the current screen content and returns it as an OpenCV `Mat`. If a specific region is set, it crops the full-screen capture to that region.

### `TemplateDetector`
This class is responsible for finding specific template images (ability icons) within a larger screen capture. It uses OpenCV's `matchTemplate` function. To improve performance, it maintains a cache of last known locations for each template and searches in that vicinity first before resorting to a full-screen scan. It also handles complexities like images with alpha channels (transparency), using the alpha channel as a mask for more accurate matching. The detection threshold can be configured globally or on a per-ability basis.

- **`detectTemplate(Mat, String)`**: The main public method to find a template on the screen. It orchestrates the search, trying a cached location first.
- **`findBestMatch(Mat, Mat, String, double)`**: The core private method that performs the OpenCV `matchTemplate` operation. It handles resource management for the native `Mat` objects involved in the process and converts the raw match score into a normalized confidence value. It is designed to be verbose in its resource management to prevent memory leaks.

### `TemplateCache`
Manages the loading and caching of template images (e.g., ability icons) from disk. On initialization, it scans a designated directory for image files (`.png`, `.jpg`), loads them into memory as OpenCV `Mat` objects, and stores them in a map, keyed by the ability name (derived from the filename). This prevents the costly I/O operation of reading image files from disk during every detection cycle. It includes a shutdown hook to release the native memory associated with the cached `Mat` objects.

- **`initialize()`**: Scans the template directory, loads all images, and populates the cache.
- **`getTemplate(String)`**: Retrieves a cached `Mat` object for a given ability name.

### `OverlayRenderer`
This class manages drawing visual feedback on the screen. It creates a transparent, always-on-top `JWindow` that spans the entire screen. Within this window, it can draw borders around detected abilities to indicate their status (e.g., current ability, next ability). The rendering logic is designed to be efficient, only redrawing when the state of detected abilities changes. It runs on the AWT Event Dispatch Thread (EDT) to ensure thread-safe UI updates.

- **`updateOverlays(List<DetectionResult>, List<DetectionResult>)`**: Clears any existing overlays and redraws new ones based on the latest detection results provided by the `DetectionEngine`. It determines the correct border color and position for each ability.

---

## Sequence Logic (`.sequence`)

### `SequenceParser`
A classic recursive descent parser that transforms a human-readable string expression into a structured `SequenceDefinition` object. It first uses a `Tokenizer` to break the input string into a stream of tokens (e.g., identifiers, operators like `+`, `|`, `>`). The parser then consumes this token stream, building a tree of `Step`, `Term`, and `Alternative` objects that mirrors the logical structure of the rotation expression. This allows the application to understand complex ability sequences with branching and concurrent requirements.

- **`parse(String)`**: The main static entry point that takes a string and returns a fully parsed `SequenceDefinition`.

### `SequenceManager`
This component manages the state of the active ability rotation. It holds a collection of all available, parsed sequences (`SequenceDefinition` objects) and keeps track of which one is currently active. During each tick of the `DetectionEngine`, it receives the list of detected abilities and passes them to the `ActiveSequence` instance for processing. Its primary role is to orchestrate the sequence logic, activating new sequences and resetting them as needed, while exposing the current and next abilities to the `DetectionEngine` for overlay rendering.

- **`processDetection(List<DetectionResult>)`**: The main entry point for the `DetectionEngine` to update the sequence state with the latest detected abilities.
- **`activateSequence(String)`**: Sets a named sequence as the active one, creating a new `ActiveSequence` instance to track its progress.

### `ActiveSequence`
Represents the runtime state of a `SequenceDefinition`. It keeps track of the current step in the sequence and uses a `StepTimer` to handle time-based conditions (e.g., waiting for a global cooldown or cast time). It advances through the steps of the sequence as the required abilities are detected on screen. The core logic resides in `processDetections`, which checks if the currently detected abilities satisfy the requirements of the current step, advancing the sequence if they do.

- **`processDetections(List<DetectionResult>)`**: Evaluates the latest detection results against the current step's requirements.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
These are the data structure classes that form the Abstract Syntax Tree (AST) of a parsed rotation.
- **`SequenceDefinition`**: A list of `Step` objects, representing the entire rotation.
- **`Step`**: Represents one or more abilities that must be executed, linked by an operator (`+` for concurrent, `>` for sequential). A step is composed of `Term`s.
- **`Term`**: A list of `Alternative`s, representing abilities that can be used interchangeably (e.g., `(AbilityA | AbilityB)`).
- **`Alternative`**: The basic unit, which can be either a single ability token (a string) or a nested sub-group, allowing for complex, recursive definitions.
