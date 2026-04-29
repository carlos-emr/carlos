## 2024-04-29 - Pre-compile Regex Patterns
**Learning:** The codebase contains frequent dynamic `java.util.regex.Pattern.compile()` calls inside methods and loops.
**Action:** Refactoring these to pre-compiled `private static final Pattern` fields is a proven, safe performance optimization.
