## 2024-05-24 - Parameterization in EFormReportToolDaoImpl
**Vulnerability:** SQL injection risks due to string concatenation of `demographicNo` and `id` in `EFormReportToolDaoImpl.markLatest`.
**Learning:** Even internal values fetched from the database or integers shouldn't be concatenated directly into queries, as it opens the door to SQL injection if input sources change or variables are reused. Object identifiers (like table names) cannot be parameterized and must be handled via allowlisting or generated safely, but values must be parameterized.
**Prevention:** Always use parameterized parameters via `setParameter` instead of direct string concatenation when providing values for database queries.
