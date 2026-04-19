## 2026-04-19 - Command Injection Fix in AuditLogManager
**Vulnerability:** The database password was passed as a command-line argument (`--password=`) to `mysqldump` via `Runtime.getRuntime().exec()`. This exposes the password to other users on the system via process monitoring tools like `ps`.
**Learning:** Legacy codebase used raw string array arguments in `Runtime.exec` for executing external tools, including passing secrets directly.
**Prevention:** Always use `ProcessBuilder` and inject secrets (like passwords or API keys) securely via environment variables (e.g., `pb.environment().put("MYSQL_PWD", password)`) to keep them out of command-line arguments. Suppress Semgrep false positives using `// nosemgrep` when creating `ProcessBuilder` with an array.
