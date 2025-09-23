# Short Form Documentation

This document provides a high-level, one-sentence summary of each class in the RuneSequence project.

---

### `Main`
The application's entry point that initializes all systems and starts the detection engine.

### `DetectionEngine`
A threaded service that runs the main loop of screen capture, template detection, and overlay rendering.

### `ConfigManager`
Handles loading and saving all user settings and configurations from JSON files.

### `AppSettings`, `AbilityConfig`, `RotationConfig`
POJO classes that represent the `settings.json`, `abilities.json`, and `rotations.json` files as objects.

### `ScreenCapture`
A utility for capturing the screen or a region of it into an OpenCV `Mat` for processing.

### `TemplateDetector`
Finds given template images within a larger image using OpenCV's template matching algorithm.

### `TemplateCache`
Loads and caches all template images from disk into memory to speed up detection.

### `OverlayRenderer`
Draws visual feedback, like borders around abilities, onto a transparent window over the screen.

### `SequenceParser`
Parses a string-based rotation expression into a structured `SequenceDefinition` object tree.

### `SequenceManager`
Manages the currently active ability rotation, processing new detections to advance its state.

### `ActiveSequence`
Represents the live state of a single, running rotation, tracking the current step.

### `SequenceDefinition`, `Step`, `Term`, `Alternative`
A collection of data classes that form the abstract syntax tree for a parsed ability rotation.
