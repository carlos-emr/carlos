## 2024-06-12 - Fix SQL Injection in DrugDaoImpl native query
**Vulnerability:** A native query in `DrugDaoImpl.findByParameter` constructed its SQL using raw string concatenation for both a dynamic column name (`parameter`) and the queried value (`value`), allowing SQL Injection.
**Learning:** In JPA/Hibernate native queries (`createNativeQuery`), you cannot parameterize dynamically injected column names. The value fields MUST be parameterized (e.g. `?1`).
**Prevention:** Always parameterize data values using placeholders. For dynamic column names where strict allowlisting is too restrictive, use a targeted blocklist regex (e.g. `.*[;'\"\\\\`].*`) to reject invalid SQL meta-characters and fail securely by throwing an `IllegalArgumentException` before concatenation.
