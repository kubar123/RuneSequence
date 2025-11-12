# Technical Deep-Dive Documentation

This document provides a low-level, implementation-focused explanation of the RuneSequence project, tracing data flow and execution logic based on the current codebase.

---

## 1. Startup and Wiring

The application bootstraps in `Main` by loading configuration and templates, wiring the detection and sequence subsystems, and initializing a small Swing UI.

### 1.1 Configuration (`ConfigManager.initialize`)
- Creates the per-user config directory at an OS-appropriate location under `RuneSequence/`.
- Extracts default assets on first run:
  - Copies `defaults/Abilities` into `RuneSequence/Abilities/` if missing, then pre-generates size variants using OpenCV via `OpenCvImageProcessor` with a `mask.png` alpha mask. Output subfolders use sizes from `ScalingConverter.getAllSizes()` (e.g., 30, 34, 36, 42, 45, 52, 60).
- Loads or creates JSON configs using Jackson (`ObjectMapper` with JavaTime + pretty-print):
  - `settings.json` → `AppSettings` (region, detection interval, selected rotation, hotkeys).
  - `rotations.json` → `RotationConfig` (named presets with expressions).
  - `abilities.json` → `AbilityConfig` (per-ability metadata, thresholds). If an older schema is detected, backs up and regenerates from defaults.
  - `ability_categories.json` → `AbilityCategoryConfig`.

### 1.2 Template Cache (`TemplateCache`)
- `TemplateCache` loads ability icon templates from `RuneSequence/Abilities/<size>` where `<size>` comes from `ScalingConverter.getScaling(percent)`. The default constructor uses 100% → `30/`.
- Each file loads with `opencv_imgcodecs.imread(..., IMREAD_UNCHANGED)`. The `TemplateData` wrapper clones the `Mat` so the original can be closed immediately.

### 1.3 Core Components (`Main.main`)
- Initializes:
  - `ScreenCapture(appSettings)` with platform-tuned FFmpeg settings and optional region cropping.
  - `TemplateDetector(templateCache, abilityConfig)` which supports alpha-masked matching and per-ability thresholds.
  - `OverlayRenderer` which draws transparent, click-through borders over detections.
- Parses all rotation presets from `RotationConfig` using `SequenceParser.parse(expression)` into `SequenceDefinition` ASTs.
  - Grammar: Expression uses `→` to separate steps, `+` to require multiple terms in a step, `/` for alternatives, and parentheses for grouping. The tokenizer normalizes ASCII `->` to `→` and rewrites trailing `" spec"` to `+ spec`.
- Builds the runtime:
  - `SequenceManager` manages the active `ActiveSequence` and step timers.
  - `SequenceController` exposes a small state machine (READY → ARMED → RUNNING) and integrates hotkeys.
  - `HotkeyManager` loads user bindings from settings and notifies `SequenceController`.
- Selects a rotation to activate:
  - Prefers `settings.rotation.selectedId` as a preset id; if missing, attempts to match by preset name; else falls back to `debug-limitless`.
- Starts the `DetectionEngine` with configured interval; initializes a system tray UI with actions (Preset Manager, Select Region, Settings), and installs a shutdown hook to cleanly stop detection, overlay, and cache.

---

## 2. Real-Time Detection Loop (`DetectionEngine`)

The engine runs on a dedicated single-thread scheduler at `settings.detection.intervalMs`.

### 2.1 Frame Processing (`processFrame`)
- Syncs with `SequenceController` state; refreshes overlays defensively before and after each pass.
- Captures a `Mat` via `ScreenCapture`. If a region is configured, frames are either captured natively in-region (Windows/Linux) or cropped in software (macOS or platforms without native region capture).
- Pre-caches ability locations opportunistically using `TemplateDetector.cacheAbilityLocations(...)` when the active sequence changes or transitions to RUNNING.
- Builds per-occurrence detection requirements from `ActiveSequence` for the current and next step. Each requirement carries:
  - `instanceId` (e.g., `limitless#0`),
  - `abilityKey` (template name),
  - `isAlternative` (true if part of an OR group).
- For each requirement, uses any preloaded result or calls `detector.detectTemplate(...)`. Results are adapted back into global screen coordinates when region capture is active.
- Sends all results to `SequenceManager.processDetection`, then updates overlays.
- Measures time budget; logs a warning if a pass exceeds 100ms.

---

## 3. Template Matching (`TemplateDetector` + `TemplateCache`)

### 3.1 Alpha-aware Matching
- If a template has 4 channels (BGRA), its alpha channel is split and used as a mask; RGB channels are merged back to BGR before matching.
- If the screen is BGRA and the template is BGR, the screen is converted to BGR (`COLOR_BGRA2BGR`) to ensure consistent channel counts.

### 3.2 Matching Strategy
- Uses OpenCV `matchTemplate` with `TM_SQDIFF_NORMED` (smaller is better). Confidence is computed as `1 - minVal` and compared against a threshold.
- Per-ability thresholds come from `AbilityConfig` (`detection_threshold`); defaults to `0.99` when not specified:
  ```java
  private double getThresholdForTemplate(String templateName) {
      AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(templateName);
      if (abilityData != null && abilityData.getDetectionThreshold() != null) {
          return abilityData.getDetectionThreshold();
      }
      return 0.99; // Default high threshold
  }
  ```

### 3.3 ROI Reuse (Last Known Location)
- Maintains `lastKnownLocations` per ability. First attempts a fast search in a padded ROI around the previous hit (±10px each side). Falls back to full-frame search if not found. Successful matches update the cache.

---

## 4. Sequence Model, Parser, and Runtime

### 4.1 AST and Grammar
- AST nodes: `SequenceDefinition` (list of `Step`), `Step` (list of `Term`), `Term` (list of `Alternative`), `Alternative` (token or subgroup).
- Grammar enforced by `SequenceParser` and `Tokenizer`:
  - Expression := Step (`→` Step)*
  - Step := Term (`+` Term)*
  - Term := Alternative (`/` Alternative)*
  - Alternative := Ability | `(` Expression `)`
- Tokenizer details: normalizes `->` to `→`; treats `/`, `+`, `(`, `)` as separators; rewrites trailing `spec` to `+ spec`.

### 4.2 Ability Instances and Requirements (`ActiveSequence`)
- Each ability occurrence is indexed as `abilityKey#N` so identical abilities in the same or subsequent steps remain distinct.
- `getDetectionRequirements()` returns the union of requirements for the current and next step, preserving `isAlternative` for OR-terms.
- `processDetections(...)` stores the latest per-instance results and advances steps when timers permit.

### 4.3 Step Timing (`StepTimer`)
- Step duration is the max over abilities of `max(cast_duration, gcdTicks, cooldown)` where default GCD is 3 ticks.
- Ticks are converted to milliseconds at 600ms/tick. Timers pause/resume based on sequence RUNNING/READY state.

### 4.4 State Machine and Latch (`SequenceController` + `SequenceManager`)
- `SequenceController` states: READY → ARMED → RUNNING.
  - Start hotkey moves READY → ARMED. Restart hotkey resets sequence and returns to READY.
- `SequenceManager` contains `GcdLatchTracker` which arms on the first solid detections of GCD abilities, then:
  - Waits for those abilities to vanish (cooldown),
  - Then to return, at which point it calls `onLatchDetected()` to enter RUNNING and start timers without added delay.

---

## 5. Overlay Rendering (`OverlayRenderer`)

- Uses a full-screen transparent `JWindow` and custom `JPanel` to draw anti-aliased rectangles.
- Colors and thickness from `UiColorPalette`:
  - Current AND: bright green; Current OR: purple (driven by `DetectionResult.isAlternative`).
  - Next: red for single-next; dark purple when multiple next abilities are present.
- Borders expand slightly beyond the detected bounding box for visibility. Overlays refresh every frame; `clearOverlays()` hides the window when empty.

---

## 6. Screen Capture (`ScreenCapture`)

- Backend: `FFmpegFrameGrabber` with platform formats: Windows `gdigrab`, Linux `x11grab`, macOS `avfoundation`.
- Enables low-latency options (`framerate`, `probesize`, `fflags=nobuffer`, `flags=low_delay`) and attempts hardware acceleration (DXVA2/VAAPI/VideoToolbox).
- Region capture:
  - Windows/Linux: configures native ROI via FFmpeg options.
  - macOS/unknown: captures full screen and crops to the desired ROI in software.

---

## 7. Performance Notes and Future Work

- ROI reuse and per-ability thresholds reduce false positives and CPU.
- Pre-caching ability locations when a sequence activates improves first-frame responsiveness.
- Future: pipeline capture and detection using a bounded queue (size 1–2) to minimize end-to-end latency while ensuring frame freshness.

