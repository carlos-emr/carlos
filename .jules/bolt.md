## 2024-10-27 - [SAX Parser String Concatenation]
**Learning:** String concatenation (`+=`) inside SAX parser event handlers like `characters` causes $O(N^2)$ execution time and severe memory allocation overhead due to the large number of callbacks and immutable `String` behavior.
**Action:** Replace `String` variables accumulating parsed XML text with a `StringBuilder`. Use `StringBuilder.append()` for all concatenations, and specifically `StringBuilder.append(ch, start, length)` in `characters()` to avoid instantiating temporary `String` objects.
