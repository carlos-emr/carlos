## 2024-04-13 - [CRITICAL] Fix SQL Injection in DrugDaoImpl
**Vulnerability:** A SQL Injection vulnerability existed in `DrugDaoImpl.findByParameter` where the `parameter` and `value` arguments were directly concatenated into the query string without sanitization or parameterization.
**Learning:** This occurred because dynamic column name querying was required. The `value` was concatenated instead of using a parameterized placeholder.
**Prevention:** Always use parameterized queries (`?1` and `query.setParameter(1, value)`) to bind input variables in `createNativeQuery`. When column names must be dynamic, validate them using an allowlist regex pattern (e.g. `^[a-zA-Z0-9_]+$`) before appending them to the query string.
