## 2026-04-27 - [Fix SQL Injection and functional regression in EFormReportToolDaoImpl]
**Vulnerability:** EFormReportToolDaoImpl `addNew` method was directly concatenating eForm variable names when creating dynamic report tables, introducing SQL injection. Additionally, `populateReportTableItem` used `VALID_IDENTIFIER_PATTERN` which caused functional regressions.
**Learning:** In JPA native queries, dynamic SQL columns must be protected with targeted blocklists (e.g. ``.*[`].*``) to allow eForms with hyphens/special chars, while data values must remain parameterized.
**Prevention:** Apply consistent targeted blocklisting across dynamic column concatenation and ensure all user inputs are correctly parameterized.
