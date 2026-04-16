## 2026-04-16 - Pre-compiled Regex over Matcher Append

**Learning:** Recompiling a regular expression (like `Pattern.compile("\\D")`) inside a heavily-used utility function and manually replacing matches using a `Matcher` loop (`matcher.find()` + `appendReplacement()`) introduces unnecessary overhead. This overhead adds up when called in loops or extensively in utility methods like `cleanNumber`.
**Action:** Always prefer using a statically initialized, pre-compiled `private static final Pattern` in conjunction with `replaceAll` when stripping or modifying strings based on fixed regular expressions.
