## 2024-05-24 - Fix SQL Injection in updateApptStatus
**Vulnerability:** The `OscarAppointmentDaoImpl.updateApptStatus` method concatenated unsanitized string input (IDs) directly into an HQL update query's IN clause, allowing potential SQL injection or query syntax errors.
**Learning:** Even internal framework queries like JPA/Hibernate HQL are vulnerable to injection and parser errors if user input is directly concatenated, especially in bulk operations like IN clauses.
**Prevention:** Always use parameterized queries for IN clauses. Hibernate allows passing a `Collection` directly to a named or positional parameter (e.g., `where id in (?2)` with `setParameter(2, collection)`). Never build queries using string concatenation with dynamic values.
