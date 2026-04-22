## 2024-04-22 - [Refactor dynamic SQL from concatenation to parameters]
**Vulnerability:** A SQL injection vulnerability was found in FrmForm2Action where `demographicNo` was directly concatenated into the SQL query string `String sql = "SELECT * FROM form" + formName + " WHERE demographic_no='" + demographicNo + "' AND ID=0";`.
**Learning:** String concatenation of variables into a SQL string can lead to SQL injection attacks even when input variables are somewhat sanitized or assumed safe.
**Prevention:** Always use parameterized SQL when building dynamic queries, such as `String sql = "SELECT * FROM form" + formName + " WHERE demographic_no=? AND ID=0";`, and pass the variable via the appropriate parameterized function argument.
