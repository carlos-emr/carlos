## 2026-04-12 - Fix SQL Injection vulnerability in OscarAppointmentDaoImpl
**Vulnerability:** A raw string `idClean.toString()` was being concatenated directly into a JPQL query`IN` clause in `OscarAppointmentDaoImpl.java`s `updateApptStatus` method. Although protected by `StringUtils.isNumeric(id)`, string concatenation should never be used to construct SQL/JPQL queries as it is considered a significant security smell and can cause compatibility issues (e.g., with H2).
**Learning:** Hibernate and JPA natively support passing `Collection`s (like `List`) to parameterized `IN` clauses (e.g., `where id in (:ids)`).
**Prevention:** Always use parameterized queries for collections, passing lists directly to query parameters instead of building comma-separated strings manually.
