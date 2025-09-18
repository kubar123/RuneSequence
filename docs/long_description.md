# Long Description Documentation

This document provides a detailed explanation of the classes in the RuneSequence project.

---

## Core Classes

### `Main`
The `Main` class is the primary entry point for the application. Its main responsibility is to orchestrate the initial setup and launch the core components of the application.

-   **`main(String[] args)`**: This static method kicks off the entire application. It calls helper methods to load all necessary configurations from files and to pre-load all image assets into the template cache, ensuring that the application is fully ready before the detection engine starts.
-   **`populateSettings()`**: This method initializes the `ConfigManager`, which handles the logic of finding, loading, or creating the `settings.json` file. By delegating to the `ConfigManager`, it ensures that application settings are loaded correctly and default settings are created on the first run.
-   **`populateTemplateCache()`**: This method creates an instance of the `TemplateCache` to load all ability images from the disk into memory. It includes a `Thread.sleep(1000)`, which suggests it might be waiting for an asynchronous process, though the current `TemplateCache` implementation loads files synchronously. This might be a remnant of a previous design or a safeguard.

---

## Configuration (`config` package)

### `ConfigManager`
This class is the central hub for all application configuration. It is robustly designed to handle the persistence of settings across sessions, providing a smooth user experience, especially on the first launch. It manages three separate configuration files: `settings.json`, `abilities.json`, and `rotations.json`.

-   **`initialize()`**: This is the core method. It determines the appropriate OS-specific directory for application data (`%APPDATA%` on Windows, `~/Library/Application Support` on macOS, etc.). It then checks if the configuration files exist. If not, it copies default versions from the application's embedded resources, ensuring the app works out-of-the-box.
-   **`save...()` methods**: These methods use the Jackson `ObjectMapper` library to serialize the corresponding configuration POJOs (`AppSettings`, `AbilityConfig`, `RotationConfig`) into JSON format and write them to disk.
-   **`get...()` methods**: Provide access to the loaded configuration objects.

### `AppSettings`
A Plain Old Java Object (POJO) that serves as a data container for the contents of `settings.json`. It uses Jackson annotations (`@JsonProperty`) to map JSON fields to class members. For better organization, it contains nested static classes `RegionSettings` (for the screen capture area) and `DetectionSettings` (for detection interval, confidence, etc.), making the settings structure clear and modular.

### `AbilityConfig`
A data container for `abilities.json`. Its most notable feature is the use of Jackson's `@JsonAnySetter`. This allows the JSON file to contain an arbitrary list of abilities, each as a named object, which are then collected into a `Map<String, AbilityData>`. This provides great flexibility, as new abilities can be defined purely in the JSON file without requiring any changes to the Java source code. The nested `AbilityData` class holds gameplay-relevant properties like cooldowns.

### `RotationConfig`
A data container for `rotations.json`, which stores predefined ability sequences, or "presets." Each preset has a name and an `expression` string (e.g., `"Ability1 -> Ability2 + Ability3"`). This class holds the raw string representation of sequences, which is fed into the `SequenceParser` to create the `SequenceDefinition` objects used by the `SequenceManager`.

### `ScalingConverter`
A simple but important utility class. It provides a static `getScaling` method that acts as a lookup table, converting a display scaling percentage (like 100%, 150%) into a specific integer value. This value corresponds to a folder name where appropriately sized image assets are stored. This mechanism allows the `TemplateCache` to load the correct set of images that will match what is actually on the screen, which is crucial for accurate detection on high-DPI displays.

---

## Screen Capture & Detection (`capture` & `detection` packages)

### `ScreenCapture`
This class is a powerful, platform-aware wrapper around `FFmpegFrameGrabber` designed for high-performance screen capture. It abstracts away the complexity of using different capture technologies for Windows (`gdigrab`), macOS (`avfoundation`), and Linux (`x11grab`).

-   **`initializeGrabber()`**: This method configures FFmpeg for low-latency capture. A key design decision is to always capture the entire screen and then perform cropping within OpenCV. This is more efficient than re-initializing the grabber every time the capture region changes. It also attempts to enable platform-specific hardware acceleration (e.g., DXVA2, VideoToolbox) to reduce CPU load.
-   **`captureScreen()`**: This is the main method called by the detection loop. It grabs a frame from FFmpeg, converts it to an OpenCV `Mat` object, and then crops it to the desired region of interest before returning it.

### `TemplateCache`
This class is responsible for loading and managing the image templates used for detection. It identifies the correct, OS-specific application data folder and loads all images from the appropriate subfolder (determined by `ScalingConverter`). It uses a `ConcurrentHashMap` to store the templates, allowing for thread-safe access from the detection thread. The nested `TemplateData` class holds an image's name and a clone of its `Mat` object, ensuring data integrity.

### `DetectionEngine`
This is the heart of the application's real-time functionality. It orchestrates the entire detection process in a continuous loop.

-   **`start()`**: This method creates and configures a `ScheduledExecutorService` to run the `processFrame` method at a fixed interval defined in the application's settings. Running the detection loop on a separate, dedicated thread is crucial for performance and prevents the application from becoming unresponsive.
-   **`processFrame()`**: This method executes on each tick of the scheduler. It calls `ScreenCapture` to get an image, asks `SequenceManager` for the list of abilities it currently needs to find, passes the image and list to `TemplateDetector`, and finally sends the results back to the `SequenceManager` to update the sequence state.

### `TemplateDetector`
This class contains the core computer vision logic. It uses OpenCV's `matchTemplate` function to find given ability icons within a screen capture.

-   **`findBestMatch(...)`**: This private method contains the most critical logic. It is specifically designed to handle templates with transparency (e.g., `.png` files). If a 4-channel image (BGRA) is passed, it intelligently extracts the alpha channel and uses it as a `mask` for the matching operation. This allows for pixel-perfect, non-rectangular matching, which is essential for accurately identifying game icons. It uses the `TM_SQDIFF_NORMED` algorithm, which is reliable for finding a single best match.

### `DetectionResult`
An immutable value object designed to hold the results of a single template detection operation. It uses static factory methods (`found`, `notFound`) to create instances, which is a best practice that makes the code more readable and ensures that the objects are always in a valid state. This class neatly bundles all relevant information (name, location, confidence, etc.), preventing "primitive obsession" where many loose variables would otherwise be passed around.

---

## Sequence Management (`sequence` package)

This package as a whole implements an Abstract Syntax Tree (AST) that represents the grammatical structure of an ability sequence.

### `SequenceManager`
This class serves as a high-level, thread-safe facade for all sequence-related operations. It holds a map of all named `SequenceDefinition`s (the blueprints) and manages the single `activeSequence`. By synchronizing all its public methods, it ensures that the `DetectionEngine` thread and any other potential threads (like a GUI thread) can interact with the sequence state without causing race conditions. It delegates all the complex state transition logic to the `ActiveSequence` instance.

### `ActiveSequence`
This class represents a live, running instance of an ability sequence. It holds a reference to an immutable `SequenceDefinition` and maintains the current state (i.e., `currentStepIndex`).

-   **`processDetections(...)`**: This is the key method where the sequence state is advanced. It takes the list of detected abilities from the `DetectionEngine` and uses the `StepTimer` to check if the time-based conditions for the current step have been met. If they have, it advances the `currentStepIndex` to move to the next step in the sequence.
-   **`getRequiredTemplates()`**: This method is crucial for optimization. It traverses the AST for the current and next steps to determine exactly which ability icons the `DetectionEngine` needs to search for, preventing wasted effort looking for irrelevant templates.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
These are simple, immutable classes that form the nodes of the AST, directly corresponding to the ability expression grammar.
-   **`SequenceDefinition`**: The root of the tree, representing a full sequence (`A -> B -> C`). It holds a list of `Step`s.
-   **`Step`**: Represents one part of the sequence between `->` arrows. It holds a list of `Term`s.
-   **`Term`**: Represents an "AND" condition (`A + B`). It holds a list of `Alternative`s.
-   **`Alternative`**: Represents an "OR" condition (`A / B`) or a single token. It can hold either a final token (an ability name string) or a nested `SequenceDefinition` for parenthesized groups.

### `SequenceParser`
This class implements a standard recursive descent parser to translate a human-readable expression string from `RotationConfig` into the `SequenceDefinition` Abstract Syntax Tree (AST) used by the `SequenceManager`. It is instantiated for a single parse operation and is therefore not thread-safe by design.
-   **`parse()`**: The static entry point that kicks off the parsing process.
-   **`parseExpression()`, `parseStep()`, `parseTerm()`, `parseAlternative()`**: These private methods are the core of the parser. Each method is responsible for parsing one level of the grammar's hierarchy (e.g., `parseExpression` handles the `->` operator). This recursive structure allows it to naturally handle nested, parenthesized sub-expressions, correctly maintaining operator precedence.
-   **Helper methods (`peek`, `consume`, etc.)**: A set of utility methods for advancing through the input string, checking for upcoming characters, and consuming expected tokens, which keeps the main parsing logic clean and readable.

### `StepTimer`
This class manages the time-based component of a sequence. It is not just a simple delay. When a step begins, `calculateStepDuration` intelligently inspects all abilities within that step, finds the one with the longest cooldown (or applies a default Global Cooldown), and sets a timer for that duration. The `isStepSatisfied` method then returns true only after this amount of time has passed, ensuring the sequence respects in-game ability timings.
