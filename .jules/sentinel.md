## 2026-04-11 - Refactored String Concatenation in DAOs to prevent SQL Injection
**Vulnerability:** SQL injection vulnerability through string concatenation in native and JPA queries (`updateApptStatus` and `markLatest`).
**Learning:** Although input was manually scrubbed (e.g., `StringUtils.isNumeric(id)`), using direct string concatenation for dynamically built queries is unsafe and violates static analysis rules.
**Prevention:** Use parameterized queries (e.g., `?1` and `q.setParameter(...)`), even with Lists/Collections for `IN` clauses, which Hibernate supports out of the box.
