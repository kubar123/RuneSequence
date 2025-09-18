# Potential Issues & Analysis

This document outlines potential issues found during a static analysis of the codebase. It includes known compilation errors (which are likely due to work-in-progress components) as well as suggestions for improving logic and code quality.

---

## 1. Known Compilation Errors

These issues currently prevent the project from compiling. They appear to be related to ongoing development.

### 1.1. `ImageCache` vs. `TemplateCache` Typo
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/detection/TemplateDetector.java`
-   **Issue:** The class declares a field `private final ImageCache imageCache;` and expects an `ImageCache` in its constructor. However, no such class exists in the codebase.
-   **Analysis:** The project contains a `TemplateCache.java` class with the exact `getTemplate()` method that `TemplateDetector` needs. This is a simple but critical typo; the type should be `TemplateCache`.

### 1.2. Incorrect Method Call in `Step.java`
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/sequence/Step.java`
-   **Issue:** The `collectDetectable` and `collectDetections` methods call `alt.getGroup()` on an `Alternative` object.
-   **Analysis:** The `Alternative` class does not have a `getGroup()` method. The correct method, which returns the nested sequence, is named `getSubgroup()`.

### 1.3. Missing Methods in `ConfigManager`
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/config/ConfigManager.java`
-   **Issue:** `DetectionEngine` and `TemplateDetector` call `getDetectionInterval()` and `getConfidenceThreshold()` on the `ConfigManager` instance, but these methods are not defined.
-   **Analysis:** These methods should be added to `ConfigManager`. They would act as simple passthroughs, retrieving the values from the `AppSettings` object that `ConfigManager` already holds (e.g., `return settings.getDetection().getConfidenceThreshold();`).

### 1.4. Missing `OverlayRenderer` Class
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/detection/DetectionEngine.java`
-   **Issue:** `DetectionEngine` depends on the `OverlayRenderer` class, which is not present in the codebase.
-   **Analysis:** This appears to be a known work-in-progress, as noted in the original documentation.

---

## 2. Logic and Code Quality Issues

### 2.1. Inconsistent Logging Framework
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/config/ConfigManager.java`
-   **Issue:** This class uses `java.util.logging.Logger`, while all other classes in the project use the SLF4J logging framework (`org.slf4j.Logger`).
-   **Suggestion:** For consistent logging configuration, output, and performance, `ConfigManager` should be updated to use SLF4J like the rest of the application.

### 2.2. Limited Parser Error Handling
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/sequence/SequenceParser.java`
-   **Issue:** The parser's `consume()` method throws a generic `IllegalStateException` when it encounters a syntax error.
-   **Suggestion:** This could be improved by creating a custom `ParseException` that includes more context, such as the position of the error in the input string and what token was expected. This would make it much easier for users to debug their sequence expressions in `rotations.json`.

### 2.3. Hardcoded `Thread.sleep()` at Startup
-   **File:** `src/main/java/com/lansoftprogramming/runeSequence/Main.java`
-   **Issue:** The `populateTemplateCache()` method contains a `Thread.sleep(1000)`.
-   **Analysis:** The comment in the long description notes this might be a remnant of a previous asynchronous design. As the current `TemplateCache` loads synchronously, this sleep call likely just adds a one-second delay to the application's startup time for no reason. It should be investigated and likely removed.
