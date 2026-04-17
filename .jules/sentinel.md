## 2024-04-17 - SQL Injection in EFormReportToolDaoImpl
**Vulnerability:** SQL injection vulnerability in `EFormReportToolDaoImpl.populateReportTableItem` due to unparameterized string concatenation for `EFormValue` values and other fields in `INSERT INTO` dynamic queries.
**Learning:** Even though column names might need dynamic generation or are verified, data values being inserted MUST be parameterized to prevent SQL injection.
**Prevention:** Use JPA native queries with sequential parameters (`?1`, `?2`, etc.) and iterate over dynamic collections, matching them up with `setParameter()` calls.
