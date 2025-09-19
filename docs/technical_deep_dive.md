# Technical Deep-Dive Documentation

This document provides a low-level, implementation-focused explanation of the RuneSequence project, tracing data flow and execution logic.

**Note on Inferred & Missing Components:** This analysis is based on the provided source files. The following critical components are referenced but missing, and their functionality has been inferred:
-   **`OverlayRenderer.java`**: Injected into `DetectionEngine`. It's responsible for drawing borders on screen, likely using a transparent `JWindow`.
-   **`Alternative.java`**: A node in the sequence AST used by `Term.java` and `Step.java`. It would handle `OR` logic (`/`), single tokens, and parenthesized groups.
-   **Sequence Expression Parser**: Logic to parse string expressions from `RotationConfig` (e.g., `"A -> B+C"`) into a `SequenceDefinition` object (the AST) is absent. This parser is the missing link between the config files and the sequence execution logic.

---

## 1. Execution Flow: Application Startup & Initialization

The startup sequence is orchestrated by static methods in `Main` and is responsible for preparing all configuration and data assets before the detection loop begins.

### 1.1. `Main.main()` -> `populateSettings()`
1.  **Entry Point**: `main()` first calls `populateSettings()`.
2.  **ConfigManager Instantiation**: A `new ConfigManager()` is created. Its constructor determines the OS-specific root config directory (`%APPDATA%`, `~/Library/Application Support`, or `~/.config`) and constructs `Path` objects for `settings.json`, `rotations.json`, and `abilities.json` within a `RuneSequence` subdirectory. It also creates a Jackson `ObjectMapper` instance configured to handle Java Time (`Instant`) and pretty-print JSON.
3.  **Initialization**: `configManager.initialize()` is called.
    -   It ensures the `RuneSequence` config directory exists.
    -   For each config file (`settings`, `rotations`, `abilities`), it runs a `loadOrCreate...()` method. This method checks if the file exists. If it does, it's deserialized using `objectMapper.readValue()`.
    -   **First Run Logic**: If a file does *not* exist, the `loadDefault...()` method is called. This uses `getClass().getClassLoader().getResourceAsStream()` to load a default version from the project's internal resources (e.g., `defaults/settings.json`), deserializes it, and then immediately saves it to the config directory.
4.  **State Population**: After `initialize()`, the `ConfigManager` instance holds three populated config objects: `AppSettings`, `RotationConfig`, and `AbilityConfig`. `Main` retrieves the `AppSettings` object and stores it in its own static `settings` field.

### 1.2. `Main.main()` -> `populateTemplateCache()`
1.  **Instantiation**: `main()` next calls `populateTemplateCache()`, which creates `new TemplateCache("RuneSequence")`.
2.  **Cache Initialization**: The `TemplateCache` constructor immediately calls its own `initialize()` method.
    -   It determines the path to the ability images, which is structured and specific: `[AppDataPath]/RuneSequence/Abilities/[scalingInt]`. The `scalingInt` is retrieved from the static `ScalingConverter.getScaling()` method, which maps the screen scaling percentage to an image size (e.g., 100 -> 30).
    -   `Files.list()` is used to stream the paths of all files in that directory.
    -   For each image file, `cacheTemplate()` is called.
        -   `opencv_imgcodecs.imread(path, IMREAD_UNCHANGED)` loads the image file into an OpenCV `Mat`, crucially preserving all 4 channels (BGRA) if they exist.
        -   A `new TemplateData(name, mat)` is created. The `TemplateData` constructor **clones** the `Mat` (`template.clone()`). This is a critical step to ensure the `Mat` in the cache has its own memory, preventing corruption or premature release.
        -   The original `mat` from `imread` is immediately closed (`mat.close()`) to release its memory.
        -   The `TemplateData` object is placed into the `cache`, which is a `ConcurrentHashMap`.
3.  **Final Sleep**: `Main` calls `Thread.sleep(1000)`. Given the synchronous nature of the cache loading, this is likely unnecessary and may be a remnant from a previous asynchronous implementation.

---

## 2. Execution Flow: The Real-Time Detection Loop

This flow begins when the (assumed) GUI or another controller instantiates and starts the `DetectionEngine`.

### 2.1. `DetectionEngine.start()`
1.  A `newSingleThreadScheduledExecutor` is created to run the detection logic on a dedicated background thread named "DetectionEngine".
2.  `scheduler.scheduleAtFixedRate(this::processFrame, ...)` is called. The rate is pulled from `configManager.getDetectionInterval()`. This initiates the main loop.

### 2.2. A Single Tick: `DetectionEngine.processFrame()`
This method executes at every interval of the scheduler.
1.  **Screen Capture**: `screenCapture.captureScreen()` is called.
    -   **Lazy Init**: On the very first call, `initializeGrabber()` is invoked. It creates an `FFmpegFrameGrabber`, sets the format (`gdigrab`, `x11grab`, `avfoundation`) and input source (`desktop`, `:0.0`, `1`) based on the detected OS. It also attempts to enable hardware acceleration (`dxva2`, `vaapi`, `videotoolbox`).
    -   **Grab & Convert**: `grabber.grab()` captures a `Frame`, which is then converted by `OpenCVFrameConverter.ToMat` into a full-screen OpenCV `Mat`.
    -   **Cropping**: A `new Mat(fullScreenMat, roi)` is created, where `roi` is a `Rect` object representing the user-defined capture region. This creates a new `Mat` header pointing to a sub-region of the full-screen `Mat`'s data. The result is then **cloned** to create a new, independent `Mat` of just the cropped area, and the full-screen `Mat` is released.
2.  **Get Required Templates**: `detectAbilities()` calls `sequenceManager.getRequiredTemplates()`.
    -   This is delegated to `activeSequence.getRequiredTemplates()`.
    -   This method gets the current `Step` and the next `Step` from the `SequenceDefinition`. It calls `getDetectableTokens()` on both.
    -   `Step.getDetectableTokens()` recursively traverses its child `Term`s and (missing) `Alternative`s to build a `List<String>` of all unique ability names that need to be found in the current game state.
3.  **Template Detection**: The engine loops through the list of required template names.
    -   For each name, `detector.detectTemplate(screenMat, templateName)` is called.
    -   `imageCache.getTemplate()` retrieves the cached `Mat` for that ability.
    -   `findBestMatch()` is invoked. This is the core vision logic:
        -   **Alpha Masking**: It checks if the template `Mat` has 4 channels. If so, it calls `opencv_imgproc.split()`. The 4th channel (`Mat` at index 3) is stored as the `mask`. The first 3 channels (BGR) are `merge`d back into a new 3-channel `Mat` called `workingTemplate`.
        -   **Matching**: `opencv_imgproc.matchTemplate()` is called with the screen, `workingTemplate`, the `mask`, and the `TM_SQDIFF_NORMED` method. The mask ensures that transparent pixels in the template are ignored during matching.
        -   **Result Analysis**: `opencv_imgproc.minMaxLoc()` finds the point of lowest difference. Since `TM_SQDIFF_NORMED` returns values from 0 (perfect match) to 1 (worst match), the confidence is calculated as `1.0 - minVal.get()`.
        -   A `DetectionResult` object is returned.
4.  **Sequence State Update**: A list of *found* `DetectionResult`s is passed to `sequenceManager.processDetection()`.
    -   This is delegated to `activeSequence.processDetections()`.
    -   The results are stored in the `lastDetections` map.
    -   `stepTimer.isStepSatisfied()` is called. It simply checks if `System.currentTimeMillis() - stepStartTimeMs >= stepDurationMs`.
    -   If the timer condition is met, `activeSequence.advanceStep()` is called. This increments `currentStepIndex` and calls `stepTimer.startStep()` for the new step.
        -   `stepTimer.startStep()` calls `calculateStepDuration()`. This method iterates through all tokens in the new step, retrieves their cooldown data from `AbilityConfig`, and calculates the maximum required duration in milliseconds. This duration becomes the new `stepDurationMs`.
5.  **Resource Cleanup**: `screenMat.close()` is called to release the memory of the captured screen region, preventing a memory leak within the loop.
---

## 3. Key Algorithms & Data Structures

### `TemplateDetector`: Alpha-Masked Template Matching
The ability to match non-rectangular icons is the most critical vision algorithm. It is achieved by:
1.  Loading PNGs with `IMREAD_UNCHANGED` to preserve the 4th (alpha) channel.
2.  When a 4-channel `Mat` is detected, it is `split`.
3.  The alpha channel is used as a `mask` in `matchTemplate`. This tells OpenCV to only score the pixels where the mask is non-zero, effectively ignoring the transparent parts of the template.
4.  The other three (BGR) channels are `merge`d back into a temporary `Mat` to be used as the actual template image in the matching function.

### `Sequence...` classes: An Abstract Syntax Tree (AST)
The `sequence` package defines a tree structure that directly mirrors the ability expression grammar.
-   **`SequenceDefinition`**: Root node, holds a `List<Step>`.
-   **`Step`**: Represents `->` separation (sequence). Holds a `List<Term>`.
-   **`Term`**: Represents `+` separation (AND logic). Holds a `List<Alternative>`.
-   **`Alternative` (inferred)**: Represents `/` separation (OR logic) or a single token.
This AST allows the `DetectionEngine` and `ActiveSequence` to recursively query which abilities are relevant at any given time (`getDetectableTokens`) without complex string parsing in the main loop.

### `StepTimer`: Cooldown and Duration Management
This class is not a simple timer. It implements game-specific timing logic.
1.  When a step starts, `calculateStepDuration` is called.
2.  It iterates through every ability token in the `Step`'s AST.
3.  For each ability, it looks up its properties (cooldown, GCD status) in `AbilityConfig`.
4.  It calculates the duration in game ticks (0.6s) and finds the maximum duration required by any ability in that step.
5.  This calculated duration becomes the wait time, ensuring the sequence cannot advance faster than the in-game global cooldowns or ability cooldowns allow.

---

## 4. Detection Engine Improvements

The detection engine has been enhanced with several new features to improve accuracy and performance, addressing the need for both high precision and real-time responsiveness.

### 4.1. Per-Ability Detection Thresholds

**Motivation:**
A global detection threshold is insufficient for a varied set of templates. Some abilities, especially those with very small visual footprints (e.g., under 40x40 pixels), can be easily confused with other screen elements. These require an extremely high similarity threshold (e.g., 99%) to prevent false positives. Conversely, larger, more distinct icons might be reliably detected with a slightly more lenient threshold. Per-ability thresholds provide the granular control needed to tune detection for each specific template.

**Implementation:**
The `detection_threshold` is now a configurable, optional property within each ability's entry in `abilities.json`.

*   **Configuration:**
    ```json
    "Limitless": {
      "cooldown": 0,
      "triggers_gcd": false,
      "detection_threshold": 0.99
    },
    "big_ability": {
      "triggers_gcd": true,
      "cast_duration": 4.0,
      "cooldown": 30
      // No threshold defined, will use default
    }
    ```

*   **Logic:** The `TemplateDetector` class retrieves the appropriate threshold using the `getThresholdForTemplate` helper method before performing a match.
    ```java
    private double getThresholdForTemplate(String templateName) {
        // Access the loaded config
        AbilityConfig.AbilityData abilityData = configManager.getAbilities().getAbility(templateName);

        // Check if the ability and its threshold are defined
        if (abilityData != null && abilityData.getDetectionThreshold() != null) {
            return abilityData.getDetectionThreshold();
        }

        // Fallback to a high default for reliability
        return 0.99;
    }
    ```
    This logic ensures that if a threshold is explicitly set, it is respected. If it is omitted, the system defaults to a safe, high-confidence value of `0.99`.

### 4.2. Last Known Location Optimization

**Motivation:**
The detection loop runs on a fixed interval (e.g., every 400ms). In many cases, UI elements like ability icons do not move between frames. Performing a full-screen search in every cycle is computationally expensive and redundant. This optimization drastically reduces the search area for templates that have already been found, freeing up CPU resources and reducing latency.

**Implementation:**
The `TemplateDetector` now maintains an internal cache of the last successfully found location for each template.

*   **Cache:** A `Map<String, Rectangle> lastKnownLocations` is used to store the bounding box of the last match for each `templateName`.

*   **Optimized Search Logic:** The `detectTemplate` method's logic was modified:
    1.  **Check Cache:** Before a full-screen search, it checks if `lastKnownLocations` contains the `templateName`.
    2.  **Region-Based Search:** If a location is cached, it creates a new, slightly larger `Rectangle` around the cached one. A padding of 10 pixels is added to each side to create a 20x20 pixel larger search area. This accounts for minor UI shifts or rendering variations.
    3.  `detectTemplateInRegion()` is called to perform a highly-localized (and therefore very fast) search.
    4.  **Cache Update & Return:** If the template is found in this padded region, its new location is updated in the cache, and the result is returned immediately, skipping the full-screen search.
    5.  **Fallback:** If the template is not found in the cached region (or if no cache entry exists), the detector proceeds with the original full-screen search using `findBestMatch()`. If this search is successful, its location is added to the cache.

### 4.3. Future Improvements - Pipelined Screen Capture

**Motivation:**
The current detection cycle is sequential: `Capture -> Detect -> Draw`. While the "Last Known Location" optimization reduces the *duration* of the "Detect" step, the process remains synchronous. The application is idle during screen capture, and the capture hardware is idle during detection. Pipelining would allow these operations to overlap, maximizing hardware utilization and improving the application's temporal resolution (its ability to detect changes closer to when they happen).

**Proposed Implementation:**
A multi-threaded producer-consumer model would be an effective solution.

*   **Producer Thread (Capture):** A dedicated thread would be responsible solely for screen capture. Its loop would consist of:
    1.  Capture a frame.
    2.  Place the frame into a shared, thread-safe queue (e.g., a `java.util.concurrent.ArrayBlockingQueue` of size 1 or 2). A small queue size is crucial to ensure freshness.

*   **Consumer Thread (Detection):** The main `DetectionEngine` thread would become the consumer. Its loop would be:
    1.  Take a frame from the queue, waiting if none is available.
    2.  Perform detection on that frame.

*   **Benefits:** This decouples the capture rate from the detection rate. The capture thread can run at the maximum speed of the hardware (e.g., 60 FPS), always providing the most recent frame to the detection thread. This significantly reduces the effective latency from screen event to detection.

*   **Potential Challenges:**
    *   **Thread Safety:** The queue implementation must be thread-safe.
    *   **Frame Freshness:** If the detection process is slower than the capture rate, the queue could fill with stale frames. A strategy to handle this would be to use a queue of size 1 and have the producer always discard the old frame and overwrite it with the new one (`queue.clear(); queue.put(frame)`). This ensures the detector always works on the most recent possible frame.
