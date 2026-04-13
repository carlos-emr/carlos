
## 2026-04-13 - [CRITICAL] Fix SQL injection in DrugDaoImpl
**Vulnerability:** A critical SQL injection vulnerability was found in `DrugDaoImpl.java` within the `findByParameter` method. The `value` parameter was being concatenated directly into the `createNativeQuery` SQL string.
**Learning:** Even if a method is currently only called by trusted internal methods (like `RxUtil` passing `customName`, `regional_identifier`, or `BN`), any `createNativeQuery` or HQL query construction must always use parameterization to prevent SQL injection and ensure H2 compatibility. Legacy DAO methods using raw string concatenation are a frequent source of these vulnerabilities.
**Prevention:** Always use parameterized queries (e.g. `?1` or `:value` placeholders with `query.setParameter`) when dynamically building SQL or HQL statements with any parameters, even if those parameters are assumed to be trusted. Avoid using string concatenation (`+`) to insert variables into query strings.
