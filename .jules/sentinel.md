## 2026-04-26 - Parameterizing IN Clauses in JPQL
**Vulnerability:** String array elements were being directly concatenated into a JPQL `IN (...)` clause using a `StringBuilder` in `PatientLabRoutingDaoImpl.findLabNosByDemographic()`, leading to potential SQL injection.
**Learning:** In standard JPQL/Hibernate, collection-valued parameters must not be enclosed in parentheses (e.g., `IN ?2` is correct, while `IN (?2)` throws an `IllegalArgumentException` at runtime).
**Prevention:** Use `setParameter` with `java.util.Arrays.asList()` and omit parentheses around the parameter placeholder for `IN` clauses to safely and correctly bind collections.
