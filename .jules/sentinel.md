## 2024-05-15 - Fix SQL Injection in EFormReportToolDaoImpl
**Vulnerability:** SQL Injection in `populateReportTableItem` method where values were directly appended to the `VALUES (...)` clause using `StringBuilder`.
**Learning:** Even when table columns are dynamic, user-provided values and basic metadata must be parameterized natively using `?` placeholders or named placeholders and `setParameter()` to prevent injection, particularly with dynamic values from forms. Furthermore, dates should be parameterized natively using `Date` objects instead of pre-formatting them into strings.
**Prevention:** Avoid manually appending values to queries. Append placeholders (`?`) in a loop for the values clause and bind them sequentially using `setParameter(index, value)`.
