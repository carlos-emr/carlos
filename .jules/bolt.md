
## 2026-04-13 - [Pre-compile Patterns in Loops/Frequent Methods]
**Learning:** `java.util.regex.Pattern.compile()` is unexpectedly expensive when placed inside frequently called static utility methods or loops.
**Action:** When working on performance, always search for dynamic regex compilation (`Pattern.compile()`) and refactor them to be pre-compiled as `private static final Pattern` fields.
