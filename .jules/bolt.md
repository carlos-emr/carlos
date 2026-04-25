## 2024-05-20 - [Pre-compile Patterns]
**Learning:** `RxUtil.java` uses `Pattern.compile()` repeatedly inside methods, including frequently called parsing loops. We should optimize these repetitive instantiations.
**Action:** Move frequently used identical patterns like `Pattern.compile("\\d+")` to pre-compiled `private static final Pattern` fields for cleaner code and faster execution, as they are thread-safe to reuse.
