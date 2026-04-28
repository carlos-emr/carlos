## 2026-04-28 - Regex Compilation in Loops
**Learning:** Frequent `Pattern.compile()` calls within loops and methods are a major performance bottleneck in Java due to expensive state machine generation.
**Action:** Always refactor constant regex strings into pre-compiled `private static final Pattern` fields or arrays. `Pattern` objects are thread-safe and designed for static reuse, whereas `Matcher` objects should remain local to the method.
