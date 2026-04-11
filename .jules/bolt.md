## 2024-05-14 - Pattern.compile performance issue
**Learning:** Found several places where `Pattern.compile` is called repeatedly inside loops or methods, rather than being pre-compiled as static final fields. This is inefficient as compiling regexes is an expensive operation in Java.
**Action:** Extract frequently used regexes to `private static final Pattern` constants.
