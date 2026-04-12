## 2025-04-12 - Prevent Command Injection via ProcessBuilder Environment Variables
**Vulnerability:** Passing a database password as a command-line argument to an external process (`mysqldump`) via `Runtime.getRuntime().exec()`. This exposes the password to process monitoring tools like `ps`.
**Learning:** Command-line arguments to external processes are often visible globally on the system. Secrets must never be passed this way.
**Prevention:** Use `ProcessBuilder` and inject secrets securely into the process via environment variables (e.g., `pb.environment().put("MYSQL_PWD", password)`). Also, pass arguments directly to the `ProcessBuilder` varargs constructor rather than building and passing an array of concatenated strings to prevent command injection.
