# Spaghetti Code & Architectural Analysis

This document outlines areas in the codebase that could be refactored to improve maintainability, reduce coupling, and adhere more closely to object-oriented design principles. These are not bugs, but rather opportunities to untangle the "spaghetti" and create a more robust and scalable architecture.

---

## 1. High-Coupling of `ConfigManager`

*   **Files Affected:** `Main`, `DetectionEngine`, `SequenceManager`, `ActiveSequence`, `Step`, `StepTimer`, `TemplateDetector`.
*   **Issue:** The `ConfigManager` instance is created in `Main` and then passed down through a long chain of constructors (`Main` → `DetectionEngine` → `SequenceManager` → `ActiveSequence` → `Step`). This creates a high degree of coupling. Components like `StepTimer` or `TemplateDetector` should not need to know about the entire `ConfigManager`; they only need specific values from it. This makes components difficult to test in isolation and harder to reuse.
*   **Proposed Solution:**
    1.  **Refactor to pass specific values, not the whole manager.** Instead of passing the `ConfigManager` instance, pass the specific configuration values needed by each component's constructor. For example, the `StepTimer` constructor could accept `long defaultGcdTicks` instead of the `ConfigManager`. The `TemplateDetector` could accept a `double defaultThreshold`.
    2.  **Introduce a "Configuration" interface or record.** Create a simple, immutable `DetectionConfig` record or interface that contains only the settings needed by the detection pipeline (e.g., `intervalMs`, `confidenceThreshold`). `Main` would be responsible for creating this object from `ConfigManager` and passing it down. This decouples the core logic from the file-based `ConfigManager`.
*   **Potential Issues in Moving:** This is a significant refactoring. It will touch many constructor signatures, which can be tedious to update. However, the risk is low as it's primarily changing how configuration is passed, not the logic itself. The benefit is a much cleaner, more decoupled design.

---

## 2. Inconsistent Logging and Excessive `System.out.println`

*   **Files Affected:** Almost all classes, especially `ConfigManager` and the `sequence` package.
*   **Issue:** The codebase is littered with `System.out.println` statements, which were likely used for temporary debugging. This is not a scalable logging solution, as it lacks levels (INFO, DEBUG, ERROR), cannot be easily configured or redirected, and adds noise to the console output. Additionally, `ConfigManager` uses `java.util.logging.Logger` while every other class uses SLF4J, leading to inconsistent configuration.
*   **Proposed Solution:**
    1.  **Standardize on SLF4J.** Change `ConfigManager` to use `org.slf4j.Logger` and `LoggerFactory` like the rest of the project.
    2.  **Replace `System.out.println` with logger calls.** Systematically go through the code and replace debugging printouts with appropriate SLF4J calls. For example, `System.out.println("Starting detection...")` should become `logger.info("Starting detection...")`, and `System.out.println("Found template: " + name)` should become `logger.debug("Found template: {}", name)`. This allows for fine-grained control over log output.
*   **Potential Issues in Moving:** This is a low-risk, high-reward change. The main effort is the manual work of finding and replacing all the print statements.

---

## 3. Potential for File Reorganization

*   **File Affected:** `TemplateCache.java`
*   **Issue:** `TemplateCache` is currently located in the root package (`com.lansoftprogramming.runeSequence`). Its purpose is very specific: loading and caching image assets for detection. Placing it in the root package makes the package structure less clear.
*   **Proposed Solution:**
    *   **Move `TemplateCache.java` to a more specific package.** There are two good options:
        1.  Move it to `...detection`, as it is exclusively used by `TemplateDetector`.
        2.  Create a new package, `...assets` or `...cache`, to hold it and potentially other caching mechanisms in the future.
    *   This clarifies the architecture by grouping related classes together.
*   **Potential Issues in Moving:** This is a very low-risk change. It only requires updating the `import` statement in `Main.java` and `TemplateDetector.java`. The benefit is improved code organization and clarity.

---

## 4. Hardcoded Values and Magic Strings

*   **File Affected:** `Main.java`
*   **Issue:** The name of the sequence to activate on startup, `"debug-limitless"`, is hardcoded directly in the `main` method. This makes it difficult to change the default sequence without modifying the source code.
*   **Proposed Solution:**
    *   **Move the default sequence name to the settings file.** Add a property like `"defaultSequence": "debug-limitless"` to `settings.json`. The `Main` class would then read this value from the `AppSettings` object and use it to activate the sequence. This makes the application more flexible and configurable.
*   **Potential Issues in Moving:** Low risk. This requires adding a field to the `AppSettings` POJO and a single line change in `Main`.

---

## 5. Logic in `Tokenizer` Suggests Parser Weakness

*   **File Affected:** `Tokenizer.java`
*   **Issue:** The `Tokenizer` contains a `postProcess` method (currently unused) that attempts to merge specific patterns of tokens (e.g., `Ability`, `LeftParen`, `Ability`, `RightParen`) into a single, new `Ability` token. This kind of post-processing is often a "bandaid fix" indicating that the parser itself may not be robust enough to handle certain grammatical structures. Relying on this approach can make the parser brittle and hard to maintain.
*   **Proposed Solution:**
    *   **Enhance the parser grammar.** Instead of merging tokens after the fact, the parser's grammar should be extended to understand this pattern as a valid expression. For example, the `Alternative` rule in the `SequenceParser` could be modified to recognize `Ability '(' Ability ')'` as a single unit. This moves the logic from a fragile post-processing step into the core, more robust parsing logic.
*   **Potential Issues in Moving:** This is a moderate-risk change as it involves altering the core parsing logic. It would require careful implementation and testing to ensure it doesn't introduce regressions. However, the benefit is a more robust and formally correct parser.

---

## 6. Redundant Logic in `DetectionEngine`

*   **File Affected:** `DetectionEngine.java`
*   **Method Affected:** `processFrame()`
*   **Issue:** The `processFrame()` method calls `updateOverlays()` at the very beginning of the method and then again at the end, after `sequenceManager.processDetection()` has been called. The first call is completely redundant, as it updates the overlay with the state from the *previous* frame, only for it to be immediately overwritten by the second call with the state from the *current* frame. This is inefficient and can cause a brief visual flicker.
*   **Proposed Solution:**
    *   **Remove the first `updateOverlays()` call.** The method should only update the overlays once, at the end, after all detection and state processing for the current frame is complete. This ensures the UI reflects the most up-to-date information without unnecessary redraws.
*   **Potential Issues in Moving:** This is a very low-risk change with a direct performance and correctness benefit. There are no downsides.

---

## 7. Verbose Resource Management in `TemplateDetector`

*   **File Affected:** `TemplateDetector.java`
*   **Method Affected:** `findBestMatch()`
*   **Issue:** The method manually declares and initializes over a dozen native OpenCV resource variables (`Mat`, `Point`, `DoublePointer`, etc.) to `null` at the top of the method, and then has a very long, deeply nested `finally` block to manually close each one. This pattern is verbose, error-prone (it's easy to forget to close a resource), and makes the code harder to read.
*   **Proposed Solution:**
    *   **Declare variables at the point of use.** Instead of pre-declaring all variables, declare them inside the `try` block where they are actually assigned (e.g., `Mat mask = channels.get(3).clone();`).
    *   **Create a resource management helper.** A static helper method like `closeQuietly(AutoCloseable... resources)` could be created to iterate through the resources and close them in a null-safe way. This would dramatically simplify the `finally` block to a single line: `closeQuietly(mask, workingTemplate, result, ...);`.
    *   **Investigate try-with-resources wrappers.** If the JavaCPP presets provide wrappers that implement `java.lang.AutoCloseable`, the code could be significantly simplified by using try-with-resources blocks, which handle cleanup automatically.
*   **Potential Issues in Moving:** Low risk. This is a pure refactoring of the method's internal implementation that doesn't change its behavior. The benefit is much cleaner, safer, and more maintainable code.

---

## 8. Tangled Responsibilities in AST Classes

*   **Files Affected:** `Step.java`, `ActiveSequence.java`
*   **Issue:** The AST node classes (like `Step`) contain logic for interpreting the tree. For example, `Step.getDetectableTokens()` takes a `ConfigManager` to check if an ability exists. `ActiveSequence` traverses the AST to get required templates. This tangles the data structure (the AST) with the logic that operates on it. A pure AST should only represent the structure, not how it's used. This violates the Single Responsibility Principle and makes the AST classes harder to reuse for other purposes (e.g., a "pretty-print" validator).
*   **Proposed Solution:**
    *   **Implement the Visitor pattern.** Create a new class, `SequenceInterpreter` or `AstVisitor`, that contains the logic for traversing the AST. Methods like `getRequiredTemplates()` or `getCurrentAbilities()` would be moved to this new class. The interpreter would be passed the root of the AST (`SequenceDefinition`) and the necessary context (`lastDetections`, `ConfigManager`), and it would return the result.
    *   This separates the concerns of data structure and interpretation, leading to a much cleaner and more extensible design.
*   **Potential Issues in Moving:** This is a moderate architectural refactoring. It would require creating a new class and moving significant logic out of `ActiveSequence` and the AST nodes. The risk is that the logic could be implemented incorrectly, but the benefit is a textbook example of good object-oriented design that will be much easier to maintain and extend in the long run.

---

## 9. Misplaced Method-Level Logic

*   **Issue:** Several classes contain methods whose logic is more closely related to the responsibilities of another class, or a utility class that doesn't exist yet. This reduces class cohesion (how focused a class is on a single purpose) and can make code harder to find, test, and reuse.
*   **Proposed Solutions:**
    *   **Move AST interpretation logic out of AST nodes.**
        *   **Methods:** `Step.getDetectableTokens()`, `Step.flattenDetections()`.
        *   **Location:** `src/main/java/com/lansoftprogramming/runeSequence/sequence/Step.java`
        *   **Suggestion:** As mentioned in the "Tangled Responsibilities" point, these methods are for *interpreting* the AST. This logic should be moved to a dedicated `SequenceInterpreter` or `AstVisitor` class. The `Step` class should be a simple data container, and the interpreter would be responsible for traversing the `Step` objects and extracting the necessary information. This is a classic application of the Visitor design pattern.
    *   **Extract platform-specific utility methods into a dedicated utility class.**
        *   **Method:** `ConfigManager.getAppDataPath()`
        *   **Location:** `src/main/java/com/lansoftprogramming/runeSequence/config/ConfigManager.java`
        *   **Suggestion:** This is a `public static` helper method for finding a system-specific directory. It has nothing to do with managing configuration *state*. It should be moved to a new utility class, for example, `com.lansoftprogramming.runeSequence.util.PlatformUtils`.
        *   **Justification:** While creating a new class for a single method might seem like overkill, it follows the Single Responsibility Principle (SRP). The `ConfigManager`'s responsibility is managing configs, not knowing the details of different operating systems' file structures. A `PlatformUtils` class would have a clear, single purpose: to provide platform-specific information. This makes the code more organized and creates a logical home for any future platform-dependent code (e.g., methods for checking OS version, display properties, etc.), preventing `ConfigManager` from becoming bloated with unrelated static methods.
    *   **Isolate startup orchestration from the main entry point.**
        *   **Methods:** `Main.populateSettings()`, `Main.populateTemplateCache()`
        *   **Location:** `src/main/java/com/lansoftprogramming/runeSequence/Main.java`
        *   **Suggestion:** The `main` method currently handles the entire application initialization sequence. This logic could be extracted into a dedicated `ApplicationInitializer` class. The `main` method would then become extremely simple: `public static void main(String[] args) { new ApplicationInitializer().start(); }`. This improves separation of concerns, making the startup process easier to manage and test independently of the main application entry point.

---

## 10. Unused and Unreachable Code

*   **Issue:** The codebase contains several methods that are `public` but are never called by any other part of the application. It also contains logic paths that can likely never be executed. This "dead code" adds clutter, can be confusing to new developers, and may hide obsolete logic.
*   **Proposed Solutions:**
    *   **Identify and Remove Unused Public Methods:**
        *   **Methods:**
            *   `Tokenizer.postProcess(List<Token> tokens)`
            *   `Step.debugStep(AbilityConfig abilityCfg)`
            *   `ScreenCapture.isRunning()`
            *   `SequenceManager.hasActiveSequence()`
            *   `TemplateCache.getCacheSize()`
        *   **Suggestion:** A static analysis of the call hierarchy shows these `public` methods are not used within the project's main logic. They should be reviewed. If they are leftover from previous development or debugging, they should be removed to clean up the code. If `debugStep` is intended for future use, it should be clearly documented as such or only included in test builds.
    *   **Identify and Remove Unreachable Code Paths:**
        *   **Location 1:** `OverlayRenderer.calculateBorderBounds()`
        *   **Code:** The `else` block that handles a `null` `boundingBox`.
        *   **Analysis:** The `addBorder` method, which calls `calculateBorderBounds`, already contains a null check for `boundingBox`. Therefore, the `else` block within `calculateBorderBounds` is unreachable and can be removed. This simplifies the method and removes dead code.
        *   **Location 2:** `Alternative.toString()`
        *   **Code:** The final `return "?";` statement.
        *   **Analysis:** An `Alternative` object's constructor ensures it must be either a `token` or a `subgroup`. The `if (isToken())` and `if (isGroup())` cover all possible valid states, making the final `return` statement unreachable. It can be safely removed.
