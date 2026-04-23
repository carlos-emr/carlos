## 2026-04-23 - Pre-compile java.util.regex.Pattern in static fields
**Learning:** Calling `Pattern.compile()` repeatedly inside frequently used methods or loops, like parsing multiple decision support values (`DSValue.java`), creates a significant performance overhead because regex compilation is CPU-intensive.
**Action:** Always extract dynamically compiled, fixed string regex patterns into `private static final Pattern` fields at the class level so they are only compiled once during class loading.
