# WAF Standards Alignment — CARLOS EMR

This document maps each WAF component in CARLOS EMR to the security standards and controls it addresses.

## WAF Architecture Overview

The WAF layer consists of servlet filters that run before all application logic. All components are **disabled by default** and enabled individually via `carlos.properties`.

```
Filter Chain Order (first to last):
1. RateLimitFilter      — per-IP rate limiting with path tiers
2. GeoIpFilter          — GeoIP country filtering (not yet implemented)
3. WafFilter            — request inspection: SQLi, XSS, traversal, bots (not yet implemented)
4. [Application filters: CSRF, LoginFilter, session management, etc.]
```

Rate limiting runs first because it is the cheapest check (counter increment) and provides the earliest rejection of abusive traffic before more expensive processing.

---

## NIST SP 800-53 Mapping

| Control | Name | CARLOS Implementation |
|---------|------|-----------------------|
| **SC-5** | Denial-of-Service Protection | `RateLimitFilter` — per-IP fixed-window rate limiting with configurable path tiers. Controlled by `WAF_RATE_LIMIT_ENABLED` in `carlos.properties`. |
| **SC-7** | Boundary Protection | `GeoIpFilter` — GeoIP country filtering. Controlled by `WAF_GEOIP_ENABLED`. *(Not yet implemented)* |
| **SC-8** | Transmission Confidentiality | HTTPS enforced via Tomcat connector configuration. |
| **SC-28** | Protection of Information at Rest | PHI encryption at the application layer (`EncryptionUtils`). |
| **SI-3** | Malicious Code Protection | `WafFilter` — request inspection for SQLi, XSS, path traversal, and bot signatures. Controlled by `WAF_ENABLED`. *(Not yet implemented)* |
| **SI-4** | System Monitoring | Detect mode in all WAF filters. Setting `WAF_RATE_LIMIT_MODE=detect` logs violations without blocking, enabling dry-run observation before enforcement. |
| **SI-10** | Information Input Validation | OWASP Encoder (`Encode.forHtml()`) on all JSP outputs; parameterized queries throughout DAO layer; `WafFilter` for request-level validation. |

---

## OWASP Top 10 (2021) Mapping

| Risk | Name | CARLOS Implementation |
|------|------|-----------------------|
| **A01** | Broken Access Control | `SecurityInfoManager.hasPrivilege()` required on every action; `PathValidationUtils` for file path operations. |
| **A02** | Cryptographic Failures | Bouncy Castle / Spring Security Crypto for password hashing; PHI encryption. |
| **A03** | Injection | Parameterized queries throughout; `WafFilter` SQLi rule group 942xxx. *(WafFilter not yet implemented)* |
| **A05** | Security Misconfiguration | All WAF components disabled by default; opt-in activation via `carlos.properties`. |
| **A07** | Identification and Authentication Failures | `RateLimitFilter` brute-force protection on `/login.do` (10 req/60s) and `/mfa/` (10 req/60s); MFA enabled by default. |

---

## OWASP CRS (Core Rule Set) Mapping

| Rule Group | Description | CARLOS Implementation |
|------------|-------------|-----------------------|
| **912xxx** | DoS Protection | `RateLimitFilter` — fixed-window counter per IP with configurable path tiers. |
| **941xxx** | XSS | `WafFilter` XSS rule group. *(Not yet implemented)* |
| **942xxx** | SQL Injection | `WafFilter` SQLi rule group. *(Not yet implemented)* |

---

## OWASP ASVS 4.0 Mapping

| Section | Requirement | CARLOS Implementation |
|---------|-------------|-----------------------|
| **§11.1.4** | Rate limiting on authentication endpoints | `RateLimitFilter` path tier `/login.do=10/60` and `/mfa/=10/60`. |

---

## RateLimitFilter Configuration Reference

All configuration via `carlos.properties`. See also `src/main/webapp/WEB-INF/waf-rules.properties`.

| Property | Default | Description |
|----------|---------|-------------|
| `WAF_RATE_LIMIT_ENABLED` | `false` | Master toggle. Set to `true`, `yes`, or `on` to enable. |
| `WAF_RATE_LIMIT_MODE` | `detect` | `enforce` = block with HTTP 429; `detect` = log only. |
| `WAF_RATE_LIMIT_DEFAULT_REQUESTS` | `100` | Global max requests per window for unmatched paths. |
| `WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS` | `60` | Global window duration in seconds. |
| `WAF_RATE_LIMIT_PATHS` | *(see below)* | Comma-separated path tiers: `path=requests/windowSeconds`. |
| `WAF_RATE_LIMIT_EXEMPT_IPS` | `127.0.0.1,::1,0:0:0:0:0:0:0:1` | IPs exempt from rate limiting. |
| `WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | `300` | Stale counter eviction interval. |

**Default path tiers (`WAF_RATE_LIMIT_PATHS`):**

```
/login.do=10/60            — brute force protection
/mfa/=10/60                — MFA brute force protection
/forcepasswordreset.jsp=5/60 — password reset abuse prevention
/lab/CMLlabUpload.do=30/60  — external lab integration
/lab/newLabUpload.do=30/60  — external lab integration
/ws/=200/60                — API traffic patterns
```

### Deployment Notes

- **Reverse proxy:** If CARLOS is behind a reverse proxy (nginx, Apache), ensure `XforwardHeaderFilter` is mapped before `RateLimitFilter` in `web.xml` so that rate limiting uses the real client IP, not the proxy's IP.
- **Shared hosting:** If multiple CARLOS instances share a reverse proxy, per-IP rate limiting is applied independently per instance (no shared state).
- **Monitoring probes:** Add monitoring probe IPs to `WAF_RATE_LIMIT_EXEMPT_IPS` to prevent false positives.
