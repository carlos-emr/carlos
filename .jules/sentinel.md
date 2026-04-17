## 2024-04-17 - Dynamic SQL Parameterization Validation
**Vulnerability:** SQL Injection in dynamically constructed native queries inside DAOs, even when fields seem internal.
**Learning:** Native queries cannot parameterize column/table names safely with standard `?1` bindings. Furthermore, data values coming from user input must *always* be parameterized.
**Prevention:** If column names must be dynamically constructed, they must be strictly validated against an allowlist of known safe columns (e.g. `if (!"customName".equals(param)... throw exception`). Always bind user data values using placeholders (`?1`) and `query.setParameter(...)`.
