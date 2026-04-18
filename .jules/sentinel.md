## 2025-01-30 - Prevent SQL Injection in DemographicDaoImpl ORDER BY clause
**Vulnerability:** The `getOrderField` method in `DemographicDaoImpl` concatenated the `orderBy` string directly into the query when `nativeQuery` was true (`orderBy = "de." + orderBy;`), creating a SQL injection vulnerability if the `orderBy` parameter was user-controlled.
**Learning:** Order by clauses cannot be parameterized with placeholders in JPA/Hibernate. Dynamic order by columns must be strictly validated against an allowlist to prevent SQL injection.
**Prevention:** Always use an allowlist mapping approach for dynamic column names or order by directions, and provide a safe default fallback.
