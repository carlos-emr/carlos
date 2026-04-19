## 2024-04-19 - Fix SQL Injection in DemographicDaoImpl Native Queries ORDER BY

**Vulnerability:**
The `DemographicDaoImpl.java` dynamically generated the `ORDER BY` clause in JPA native queries by concatenating raw `String orderBy` user input via the `getOrderField(String orderBy, boolean nativeQuery)` method (`orderBy = "de." + orderBy;`). This enables a critical SQL injection attack where an attacker can append malicious SQL directly into the query execution.

**Learning:**
In JPA/Hibernate, `ORDER BY` clauses cannot be parameterized with placeholders (e.g. `?1` or `:param`), unlike query values. When dynamic SQL generation takes column names or sort directions as input and concatenates them, it completely bypasses ORM security.

**Prevention:**
When dynamically constructing `ORDER BY` statements, never directly concatenate user input. Instead, strictly validate the dynamic order columns against an explicit allowlist or a map (e.g. mapping "dob" to "de.year_of_birth, de.month_of_birth, de.date_of_birth" and "last_name" to "de.last_name"), throwing an `IllegalArgumentException` if the input is not explicitly permitted.
