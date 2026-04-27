## 2026-04-27 - Parameterizing IN clauses to prevent SQL Injection
**Vulnerability:** SQL injection vulnerability in `updateApptStatus` in `OscarAppointmentDaoImpl` when constructing dynamic HQL queries.
**Learning:** `idClean.toString()` appended string values directly into an `IN` clause: `update Appointment set status=?1 where id in (" + idClean.toString() + ")"`.
**Prevention:** In JPA/Hibernate, `IN` clauses can directly take a parameter: `where id in :ids`. Before doing so, validating and parsing elements into a strongly-typed `List` (e.g., `List<Integer>`) prevents malicious string injection.
