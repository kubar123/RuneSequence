# Long Description Documentation

This document provides a detailed explanation of the classes in the RuneSequence project.

---
## Core (`com.lansoftprogramming.runeSequence`)

### `Main`
The `Main` class is the primary entry point for the application, responsible for orchestrating a clean startup sequence. It initializes and wires together all core components, starting with configuration and asset loading, followed by the setup of the main detection and rendering pipeline. It establishes a `SequenceManager` with all predefined rotations from the configuration. A crucial role is setting a specific "debug" sequence as active, which is hardcoded. Finally, it starts the `DetectionEngine`'s main loop and registers a JVM shutdown hook to ensure all native resources and threads are gracefully terminated, preventing memory leaks or orphaned processes.
*   **`main(String[] args)`**: The static entry point that executes on startup. It follows a strict initialization order: first, it calls `populateSettings()` to load all configurations, then `populateTemplateCache()` to load images. It then instantiates core components like `ScreenCapture` and `TemplateDetector`. A key step is parsing all rotation presets from the config into `SequenceDefinition` objects and activating a hardcoded debug sequence. Finally, it constructs and starts the `DetectionEngine` and adds a shutdown hook to ensure graceful resource cleanup. The main thread then waits indefinitely, keeping the application alive.
*   **`populateTemplateCache()` / `populateSettings()`**: Simple helper methods that instantiate and initialize the `TemplateCache` and `ConfigManager` respectively.

### `TemplateCache`
Manages the loading and in-memory storage of image templates used for detection. It determines the correct OS-specific application data directory and selects the appropriate subfolder based on the display scaling factor (e.g., 100%, 150%) provided by `ScalingConverter`. Templates are loaded from the filesystem as OpenCV `Mat` objects and stored in a `ConcurrentHashMap` for thread-safe access. The nested `TemplateData` class ensures that each `Mat` is cloned upon storage, preventing memory corruption from concurrent access and simplifying resource management, as the original `Mat` can be closed immediately after loading.
*   **`initialize()`**: Scans the designated application image directory for supported image files (`.png`, `.jpg`, etc.). For each file found, it extracts the filename without the extension to use as the template's unique name (e.g., "Ability1"). It then calls `cacheTemplate` to load the image data into an OpenCV `Mat` and store it in the cache. This method is non-recursive, only loading images from the top level of the configured directory.
*   **`getTemplate(String abilityName)`**: Retrieves a template `Mat` from the cache by its name.
*   **`shutdown()`**: Releases all OpenCV `Mat` objects in the cache to prevent memory leaks and shuts down its internal thread pool.

---
## Configuration (`...config`)

### `ConfigManager`
This class is the central hub for all application configuration. It is robustly designed to handle the persistence of settings across sessions. On initialization, it determines the correct OS-specific directory for application data (`%APPDATA%`, `~/.config`, etc.) and ensures it exists. For each required config file (`settings.json`, `abilities.json`, `rotations.json`), it checks for its presence. If a file is missing, it copies a default version from the application's embedded resources, ensuring the app works out-of-the-box. It uses the Jackson library to serialize and deserialize the configuration POJOs.
*   **`initialize()`**: The core setup method that creates the config directory and calls the `loadOrCreate...()` helpers for each configuration file.
*   **`save...()` / `get...()` methods**: Provide standard save/load and access functionality for the configuration objects (`AppSettings`, `AbilityConfig`, `RotationConfig`).
*   **`getDetectionInterval()` / `getConfidenceThreshold()`**: These methods provide direct access to specific, frequently used settings, acting as convenient shortcuts so other classes don't need to navigate the nested `AppSettings` object structure.

### `AppSettings`
A Plain Old Java Object (POJO) that serves as a data container for the contents of `settings.json`. It uses Jackson annotations to map JSON fields to class members. For better organization, it contains nested static classes `RegionSettings` (for the screen capture area) and `DetectionSettings` (for detection interval, confidence, etc.), making the settings structure clear and modular.

### `AbilityConfig`
A data container for `abilities.json`. Its most notable feature is the use of Jackson's `@JsonAnySetter`. This allows the JSON file to contain an arbitrary list of abilities, each as a named object, which are then collected into a `Map<String, AbilityData>`. This provides great flexibility, as new abilities can be defined purely in the JSON file without requiring any changes to the Java source code. The nested `AbilityData` class holds gameplay-relevant properties like cooldowns.

### `RotationConfig`
A data container for `rotations.json`, which stores predefined ability sequences, or "presets." Each preset has a name and an `expression` string (e.g., `"Ability1 -> Ability2 + Ability3"`). This class holds the raw string representation of sequences, which is fed into the `SequenceParser` to create the `SequenceDefinition` objects used by the `SequenceManager`.

### `ScalingConverter`
A simple but important utility class. It provides a static `getScaling` method that acts as a lookup table, converting a display scaling percentage (like 100%, 150%) into a specific integer value. This value corresponds to a folder name where appropriately sized image assets are stored. This mechanism allows the `TemplateCache` to load the correct set of images that will match what is actually on the screen, which is crucial for accurate detection on high-DPI displays.

---
## Screen Capture & Detection (`...capture` & `...detection`)

### `ScreenCapture`
This class is a powerful, platform-aware wrapper around `FFmpegFrameGrabber` designed for high-performance screen capture. It abstracts away the complexity of using different capture technologies for Windows (`gdigrab`), macOS (`avfoundation`), and Linux (`x11grab`). A key design decision is to always capture the entire screen and then perform cropping within OpenCV. This is more efficient than re-initializing the grabber every time the capture region changes. It also attempts to enable platform-specific hardware acceleration (e.g., DXVA2, VideoToolbox) to reduce CPU load.
*   **`captureScreen()`**: The main method called by the detection loop. It grabs a frame from FFmpeg, converts it to an OpenCV `Mat` object, and then crops it to the desired region of interest before returning it. It manages the lifecycle of the grabber, initializing it on the first call.
*   **`setRegion(Rectangle region)`**: Sets the rectangular area of the screen to be captured. Since cropping is done in software, this operation is fast and does not require re-initializing the native grabber.

### `TemplateDetector`
This class contains the core computer vision logic. It uses OpenCV's `matchTemplate` function to find given ability icons within a screen capture. It features an optimization where it first searches in the area the template was last seen; if not found, it searches the entire screen. It also dynamically fetches the detection threshold for each ability from `ConfigManager`. A critical feature is its handling of transparent images, where it automatically extracts the alpha channel to use as a mask, enabling precise, non-rectangular matching of icons.
*   **`detectTemplate(Mat screen, String templateName)`**: The main public method. It orchestrates the detection process, first attempting a fast search in a small, previously known region and falling back to a full-screen search if that fails.
*   **`findBestMatch(...)`**: This private method contains the most critical logic. It performs the template matching using `TM_SQDIFF_NORMED`, which is reliable for finding a single best match. It carefully manages native OpenCV resources (`Mat`, `Point`, etc.) to prevent memory leaks, closing every object it creates.
*   **`ensureColorConsistency(...)`**: A helper that handles color format differences between the screen capture and the template (e.g., BGRA vs. BGR), ensuring the `matchTemplate` function receives compatible data.

### `DetectionEngine`
This is the heart of the application's real-time functionality. It orchestrates the entire detection process in a continuous loop. It uses a `ScheduledExecutorService` to run the `processFrame` method at a fixed interval defined in the application's settings. Running the detection loop on a separate, dedicated thread is crucial for performance and prevents the application from becoming unresponsive.
*   **`start()` / `stop()`**: Methods to start and stop the scheduled detection loop.
*   **`processFrame()`**: This method executes on each tick of the scheduler. It calls `ScreenCapture` to get an image, asks `SequenceManager` for the list of abilities it currently needs to find, passes the image and list to `TemplateDetector`, and finally sends the results back to the `SequenceManager` to update the sequence state. It also calls `updateOverlays` to refresh the on-screen display.

### `DetectionResult`
An immutable value object designed to hold the results of a single template detection operation. It uses static factory methods (`found`, `notFound`) to create instances, which is a best practice that makes the code more readable and ensures that the objects are always in a valid state. This class neatly bundles all relevant information (name, location, confidence, bounding box), preventing "primitive obsession" where many loose variables would otherwise be passed around.

### `OverlayRenderer`
Creates a transparent, click-through `JWindow` that spans the entire screen to draw borders around detected abilities. This provides a non-intrusive UI. It defines different border styles (`BorderType` enum) for "current" (green) and "next" (red) abilities, with alternative colors for "OR" groups (purple). It uses a `ConcurrentHashMap` to manage the list of active borders, making it safe to update from the detection thread. All drawing is handled within a custom `OverlayPanel` on the Swing Event Dispatch Thread to ensure thread safety with the UI.
*   **`updateOverlays(...)`**: This is the main entry point, called by `DetectionEngine`. It clears all previous borders, then iterates through the lists of current and next abilities, adding a new border for each one that was found. It then shows or hides the main overlay window based on whether any borders are active.
*   **`shutdown()`**: Disposes of the Swing window and its resources.

---
## Sequence Management (`...sequence`)

This package as a whole implements a parser and an Abstract Syntax Tree (AST) that represents the grammatical structure of an ability sequence.

### `SequenceManager`
This class serves as a high-level, thread-safe facade for all sequence-related operations. It holds a map of all named `SequenceDefinition`s (the blueprints) loaded from config and manages the single `activeSequence`. By synchronizing all its public methods, it ensures that the `DetectionEngine` thread and any other potential threads (like a GUI thread) can interact with the sequence state without causing race conditions. It delegates all the complex state transition logic to the `ActiveSequence` instance.
*   **`activateSequence(String name)`**: Sets a sequence from the internal map as the `activeSequence`, creating a new stateful instance of it.
*   **`getRequiredTemplates()`**: Called by `DetectionEngine` to get the list of all ability icons that need to be found in the current and next steps of the sequence.
*   **`processDetection(...)`**: Forwards the list of detection results from the `DetectionEngine` to the `activeSequence` for state processing.

### `ActiveSequence`
This class represents a live, running instance of an ability sequence. It holds a reference to an immutable `SequenceDefinition` and maintains the current state (i.e., `currentStepIndex`). It is responsible for advancing the sequence based on detection results and timers.
*   **`processDetections(...)`**: The key method where the sequence state is managed. It stores the latest detection results and then checks with the `StepTimer` if the time-based conditions for the current step have been met. If so, it advances the `currentStepIndex` to the next step.
*   **`getRequiredTemplates()`**: Traverses the AST for the current and next steps to determine exactly which ability icons the `DetectionEngine` needs to search for, preventing wasted effort looking for irrelevant templates.
*   **`getCurrentAbilities()` / `getNextAbilities()`**: Traverses the AST for the current/next step and returns the corresponding `DetectionResult` objects from the last detection cycle, which are then used by the `OverlayRenderer`.

### `SequenceParser`
This class implements a standard recursive descent parser to translate a human-readable expression string from `RotationConfig` into the `SequenceDefinition` Abstract Syntax Tree (AST) used by the `SequenceManager`. It is instantiated for a single parse operation. The grammar is defined with clear operator precedence (`/` then `+` then `→`) and support for nested, parenthesized sub-expressions.
*   **`parse(String input)`**: The static entry point that tokenizes the input string and kicks off the parsing process.
*   **`parseExpression()`, `parseStep()`, `parseTerm()`, `parseAlternative()`**: These private methods are the core of the parser. Each method is responsible for parsing one level of the grammar's hierarchy. This recursive structure allows it to naturally handle nested expressions.

### `StepTimer`
This class manages the time-based component of a sequence. It is not just a simple delay. When a step begins, `calculateStepDuration` intelligently inspects all abilities within that step, finds the one with the longest cooldown (or applies a default Global Cooldown), and sets a timer for that duration. The `isStepSatisfied` method then returns true only after this amount of time has passed, ensuring the sequence respects in-game ability timings.

### `Tokenizer`
Responsible for breaking a raw expression string into a stream of `Token` objects. It performs a two-stage process. First, it adds whitespace padding around all operators and parentheses to simplify splitting. Second, it splits the string by this whitespace and then intelligently merges consecutive non-operator parts into single `Token.Ability` records. This approach correctly handles ability names that contain spaces. It also correctly interprets both `->` and `→` as the same operator.

### `Token`
A modern Java `sealed interface` with `record` implementations (`Ability`, `Operator`, `LeftParen`, `RightParen`). This provides a strongly-typed, immutable representation of the different parts of a sequence expression. Using records significantly reduces boilerplate code while ensuring the tokens are simple, transparent data carriers.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
These are simple, immutable classes that form the nodes of the AST, directly corresponding to the ability expression grammar. Their structure defines the hierarchy of the sequence logic.
*   **`SequenceDefinition`**: The root of the tree, representing a full sequence (`A → B`). It holds a list of `Step`s.
*   **`Step`**: Represents one part of the sequence between `→` arrows (`A + B`). It holds a list of `Term`s.
*   **`Term`**: Represents an "AND" condition (`A / B`). It holds a list of `Alternative`s.
*   **`Alternative`**: Represents an "OR" condition or a single token. It can hold either a final token (an ability name string) or a nested `SequenceDefinition` for parenthesized groups.
