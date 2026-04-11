## 2026-04-11 - Use secure hashing for audits
**Vulnerability:** The HashAudit class was using the broken MD5 algorithm for cryptographic hashes of note content.
**Learning:** Legacy systems often hardcode outdated cryptographic algorithms like MD5 or SHA-1 directly into entity classes.
**Prevention:** Avoid hardcoding specific hashing algorithms and regularly audit codebase for outdated cryptographic primitives like MD5 and SHA-1. Instead, use modern algorithms like SHA-256 or better.
