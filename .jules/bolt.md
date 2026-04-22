## 2024-06-12 - Pre-compiling Regex Patterns
**Learning:** The codebase contains frequent dynamic `java.util.regex.Pattern.compile()` calls inside methods and loops. Additionally, using dangerous wildcards (like `.+?`) and unanchored greedy quantifiers can trigger ReDoS alerts.
**Action:** Refactor these to pre-compiled `private static final Pattern` fields. Use safer negated character classes (e.g., `[^']+`) instead of `.+?` to avoid ReDoS warnings and improve performance. Remove redundant capturing groups when possible.
