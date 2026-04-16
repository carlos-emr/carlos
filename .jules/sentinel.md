## YYYY-MM-DD - Sentinel SQL Injection Findings
**Vulnerability:** SQL injection vulnerabilities exist in dynamic table name logic within EFormReportToolDaoImpl, and more crucially, parameter binding is not used in multiple native queries.
**Learning:** Legacy codebase patterns heavily rely on string concatenation for SQL queries using `entityManager.createNativeQuery`.
**Prevention:** Always parameterize user inputs in SQL and use an allowlist/regex validation for dynamic table and column names that cannot be parameterized.
