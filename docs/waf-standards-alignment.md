# WAF Standards Alignment — CARLOS EMR

This document maps the CARLOS EMR Web Application Firewall implementation to
industry security standards. Use it as a compliance reference when configuring
the WAF for production deployment or during security audits.

**Filters covered**: `WafFilter`, `GeoIpFilter`, `RateLimitFilter` (Tomcat),
`LoginRateLimitFilter` (Tomcat), `StuckThreadDetectionValve` (Tomcat)

**Configuration**: `waf-rules.properties`, `carlos.properties`, `server.xml`

---

## Table of Contents

- [OWASP Core Rule Set (CRS) Alignment](#owasp-core-rule-set-crs-alignment)
  - [CRS Rule Group Mapping](#crs-rule-group-mapping)
  - [CRS Paranoia Levels](#crs-paranoia-levels)
  - [Healthcare Tuning](#healthcare-tuning-crs-exclusions)
- [OWASP Top 10 (2021) Coverage](#owasp-top-10-2021-coverage)
- [OWASP Application Security Verification Standard (ASVS)](#owasp-asvs-40-alignment)
- [NIST SP 800-53 Rev. 5 Control Mapping](#nist-sp-800-53-rev-5-control-mapping)
- [NIST SP 800-44 Rev. 2 Alignment](#nist-sp-800-44-rev-2-alignment)
- [PIPEDA and HIPAA Considerations](#pipeda-and-hipaa-considerations)
- [OWASP WAFEC Evaluation Criteria](#owasp-wafec-evaluation-criteria)
- [WAF Deployment Best Practices](#waf-deployment-best-practices)
- [Configuration Reference](#configuration-reference)
- [Related Issues and Future Work](#related-issues-and-future-work)

---

## OWASP Core Rule Set (CRS) Alignment

The OWASP Core Rule Set (CRS) is the industry-standard set of generic attack
detection rules for use with web application firewalls. Originally developed for
ModSecurity (as "OWASP ModSecurity CRS"), the project was renamed to "OWASP CRS"
and starting with CRS v4.0.0 (February 2024) is fully engine-agnostic —
supporting ModSecurity v2/v3, OWASP Coraza, and other compatible WAF engines.
The current release is CRS v4.8.0 (October 2024).

Our `WafFilter` implements a subset of CRS-equivalent patterns in a lightweight
Java servlet filter suitable for embedded deployment without an external WAF
engine. This approach avoids adding a ModSecurity/Coraza dependency to the Tomcat
stack while still providing CRS-aligned protection.

### CRS Rule Group Mapping

Each WafFilter module maps to an OWASP CRS rule group:

| CRS Rule Group | CRS ID Range | WafFilter Module | Property | Description |
|---|---|---|---|---|
| Method Enforcement | 911xxx | `protocol-enforcement` | `waf.module.protocol-enforcement.enabled` | Blocks TRACE/TRACK HTTP methods that can be used for cross-site tracing (XST) attacks |
| Scanner Detection | 913xxx | `scanner-detection` | `waf.module.scanner-detection.enabled` | Identifies known vulnerability scanners and attack tools via User-Agent signatures |
| Protocol Enforcement | 920xxx | `request-limits` | `waf.module.request-limits.enabled` | Enforces request size constraints: URI length, parameter count, parameter value length |
| HTTP Response Splitting | 921xxx | `header-injection` | `waf.module.header-injection.enabled` | Detects CRLF injection in header values (%0d, %0a, literal CR/LF bytes) |
| Local File Inclusion | 930xxx | `path-traversal` | `waf.module.path-traversal.enabled` | Blocks directory traversal sequences in raw, URL-encoded, and double-encoded forms |
| Remote Code Execution | 932xxx | `command-injection` | `waf.module.command-injection.enabled` | Detects shell metacharacters, command sequences, and JNDI/expression language injection |
| XSS | 941xxx | `xss` | `waf.module.xss.enabled` | Detects script injection, event handlers, dangerous URI schemes, and DOM manipulation |
| SQL Injection | 942xxx | `sqli` | `waf.module.sqli.enabled` | Detects UNION-based, tautology, stacked query, time-based blind, and data exfiltration patterns |

**CRS coverage gaps** (not implemented, by design):

| CRS Rule Group | CRS ID Range | Reason Not Implemented |
|---|---|---|
| IP Reputation | 910xxx | Handled separately by `GeoIpFilter` (Spamhaus DROP list) |
| Request Smuggling | 921xxx (subset) | Mitigated at Tomcat connector level (HTTP/1.1 strict parsing) |
| Multipart Attacks | 922xxx | Handled by Tomcat's multipart config and CSRFGuard multipart wrapper |
| Remote File Inclusion | 931xxx | Low risk — application does not dynamically include remote URLs |
| PHP Attacks | 933xxx | Not applicable — Java application |
| Generic App Attacks | 934xxx | Partial coverage via command injection (Node.js patterns not applicable) |
| Session Fixation | 943xxx | Handled at application layer (`LoginFilter`, session management) |
| Java/RCE | 944xxx | Partial coverage via command injection module (JNDI `${...}` patterns); full deserialization protection at application layer |
| Data Leakage (outbound) | 950xxx–953xxx | Planned: see [ResponseSanitizationFilter issue](#related-issues-and-future-work) |

### CRS Paranoia Levels

OWASP CRS defines four paranoia levels (PL) that control the aggressiveness of
detection rules. Higher levels catch more attacks but produce more false positives.

| Level | Description | False Positive Risk | CARLOS Default |
|---|---|---|---|
| **PL1** | Standard protection. Catches common, well-known attack patterns. Minimal tuning required. | Low | **Yes (default)** |
| **PL2** | Elevated protection. Additional patterns for less common attacks. Moderate tuning needed. | Moderate | No |
| **PL3** | High protection. Aggressive detection including partial pattern matches. Extensive tuning required. | High | No |
| **PL4** | Maximum protection. Catches advanced evasion techniques. Very high false positive rate. Not recommended for healthcare applications. | Very High | No |

**CARLOS operates at PL1 equivalent** because:
- Healthcare text (clinical notes, prescriptions) contains words that match
  injection patterns at higher paranoia levels (e.g., "SELECT-ive serotonin
  reuptake inhibitor", "patient OR family history", "drop in blood pressure")
- False positives in a healthcare EMR can block clinical workflows and
  directly impact patient care
- PL1 catches the vast majority of automated attacks and script kiddies
- Relaxed paths provide additional tuning for known high-false-positive endpoints

### Healthcare Tuning (CRS Exclusions)

Standard CRS deployments in healthcare environments require extensive rule
exclusions. Our approach uses **relaxed paths** instead:

```properties
# Paths where POST body injection checks are skipped
# Structural checks (limits, protocol, path traversal in URI) still apply
waf.relaxed.paths=/CaseManagementEntry.do,/CaseManagementView.do,/SaveNote.do,/oscarEncounter/,/eform/,/annotation/
```

**Why relaxed paths instead of per-rule exclusions:**
- Simpler configuration (no need to track individual CRS rule IDs)
- These endpoints already have application-layer protection (OWASP Encoder,
  parameterized queries, `SecurityInfoManager` privilege checks)
- Query string parameters are still checked even on relaxed paths
- URI-level path traversal checks always apply

---

## OWASP Top 10 (2021) Coverage

| # | Category | WAF Coverage | Component |
|---|---|---|---|
| **A01** | Broken Access Control | Partial | TRACE/TRACK blocking, path traversal detection. Primary enforcement at application layer (`SecurityInfoManager`, `HttpMethodGuardFilter`) |
| **A02** | Cryptographic Failures | N/A | Handled at application layer (BCrypt, Bouncy Castle, TLS termination) |
| **A03** | Injection | **Strong** | SQLi, XSS, command injection, CRLF detection patterns. Application layer adds OWASP Encoder + parameterized queries |
| **A04** | Insecure Design | Partial | Request limits, parameter validation. Primary enforcement at application architecture level |
| **A05** | Security Misconfiguration | Moderate | Scanner detection, method enforcement, security headers (`ResponseDefaultsFilter`). Planned: `ResponseSanitizationFilter` for error message leakage |
| **A06** | Vulnerable and Outdated Components | N/A | Handled by dependency management (Maven, Dependabot) |
| **A07** | Identification and Authentication Failures | Moderate | Rate limiting on login (10/min), global rate limiting (100/min). Application layer: `LoginFilter`, `LockOutRealm` |
| **A08** | Software and Data Integrity Failures | N/A | Handled at application layer (CSRF protection, CSRFGuard 4.5) |
| **A09** | Security Logging and Monitoring Failures | **Strong** | Detect mode logging, violation tracking with PHI-safe output, access logging (`AccessLogValve`) |
| **A10** | Server-Side Request Forgery (SSRF) | Partial | Path traversal and command injection detection. Limited SSRF protection — primary enforcement at application layer |

---

## OWASP ASVS 4.0 Alignment

The OWASP Application Security Verification Standard (ASVS) provides a framework
for testing web application security controls. Relevant WAF-related requirements:

| ASVS ID | Requirement | CARLOS Implementation |
|---|---|---|
| **5.1.3** | Verify that all input is validated using positive validation | WafFilter parameter inspection + application-layer OWASP Encoder |
| **5.1.5** | Verify that URL redirects and forwards only allow allowlisted destinations | Path traversal detection in WafFilter |
| **5.2.1** | Verify that all untrusted HTML input is properly sanitized | XSS detection module + OWASP Encoder at application layer |
| **5.2.2** | Verify that unstructured data is sanitized | Parameter value length limits + injection detection |
| **5.3.4** | Verify that data selection or database queries use parameterized queries | SQLi detection module + parameterized queries at application layer |
| **7.4.1** | Verify that a generic error message is shown for unexpected errors | `errorpage.jsp` + planned `ResponseSanitizationFilter` |
| **7.4.3** | Verify that security logging captures all auth decisions | WAF violation logging + `UserActivityFilter` audit trail |
| **11.1.4** | Verify that the application can detect and alert on abnormal requests | Detect mode logging, scanner detection, rate limiting |
| **12.4.1** | Verify that files obtained from untrusted sources are validated | Path traversal checks on all URIs + `PathValidationUtils` at application layer |
| **13.1.1** | Verify that all input is validated for type, length, range | Request limits module (URI length, parameter count, value length) |
| **14.4.1** | Verify that every HTTP response contains a Content-Type header | `ResponseDefaultsFilter` sets encoding and security headers |
| **14.4.3** | Verify that a Content Security Policy (CSP) is in place | Not currently implemented (planned for future PR) |

---

## NIST SP 800-53 Rev. 5 Control Mapping

NIST Special Publication 800-53 Revision 5 defines security and privacy controls
for information systems. The following controls are addressed by the WAF layer:

### Primary Controls

| Control ID | Control Name | Description | CARLOS Implementation |
|---|---|---|---|
| **SC-7** | Boundary Protection | Monitor and control communications at external/internal boundaries | `WafFilter` as application-layer boundary; inspects all inbound requests |
| **SC-7(5)** | Deny by Default / Allow by Exception | Deny network traffic by default; allow by exception | `GeoIpFilter` allows only configured countries (default: CA); all others denied |
| **SI-3** | Malicious Code Protection | Detect and eradicate malicious code | Injection pattern detection (SQLi, XSS, command injection, CRLF) |
| **SI-4** | System Monitoring | Monitor the system to detect attacks and unauthorized connections | Detect mode logging; scanner signature detection; violation tracking |
| **SI-10** | Information Input Validation | Check the validity, integrity, and accuracy of inputs | Request limits, parameter validation, encoding checks, healthcare-context-aware relaxed paths |
| **SI-11** | Error Handling | Generate error messages without revealing exploitable information | PHI-safe WAF logging; `errorpage.jsp`; planned `ResponseSanitizationFilter` |
| **AC-4** | Information Flow Enforcement | Enforce approved authorizations for controlling information flow | GeoIP country filtering; IP reputation blocking (Spamhaus DROP) |
| **AU-3** | Content of Audit Records | Ensure audit records contain sufficient information | Violation logs include: client IP, request URI, matched rule category. Never includes parameter values (PHI protection) |
| **AU-6** | Audit Record Review, Analysis, and Reporting | Review and analyze audit records for indications of inappropriate activity | Detect mode enables log-based tuning before enforcement; WAF logs to dedicated `waf.filter` / `waf.geoip` categories |
| **SC-5** | Denial-of-Service Protection | Protect against or limit effects of DoS attacks | Tomcat `RateLimitFilter` (100 req/min global, 10 req/min login); `StuckThreadDetectionValve` (5-min threshold) |
| **CM-7** | Least Functionality | Configure the system to provide only mission-essential capabilities | TRACE/TRACK method blocking; scanner tool rejection |

### Supporting Controls

| Control ID | Control Name | WAF Relevance |
|---|---|---|
| **AC-17** | Remote Access | GeoIP restricts remote access to Canadian IPs by default |
| **AU-12** | Audit Record Generation | WAF generates audit events for all violations |
| **CA-7** | Continuous Monitoring | Detect mode provides continuous monitoring capability |
| **IR-4** | Incident Handling | WAF logs provide incident response data (IP, URI, rule, timestamp) |
| **RA-5** | Vulnerability Monitoring and Scanning | Scanner detection module identifies active scanning attempts |

---

## NIST SP 800-44 Rev. 2 Alignment

NIST Special Publication 800-44 Revision 2, "Guidelines on Securing Public Web
Servers," provides specific guidance on web server security including WAF
deployment.

### Section 8.2 — Web Application Firewalls

NIST SP 800-44 Section 8.2 recommends:

| Recommendation | CARLOS Implementation |
|---|---|
| Deploy WAFs to provide an additional layer of input validation | `WafFilter` provides defense-in-depth alongside application-layer OWASP Encoder and parameterized queries |
| WAFs should inspect HTTP request and response traffic | Request inspection implemented; response inspection planned (`ResponseSanitizationFilter`) |
| WAFs should have configurable rule sets | `waf-rules.properties` provides per-module toggles, path exclusions, and request limits |
| WAFs should operate in detection mode before enforcement | Default mode is `detect`; documentation emphasizes 1-2 week tuning period |
| WAFs should log security events | Dedicated `waf.filter` and `waf.geoip` logging categories with PHI-safe output |

### Section 8.3 — Intrusion Detection

| Recommendation | CARLOS Implementation |
|---|---|
| Monitor for known attack signatures | Scanner User-Agent detection, injection pattern matching |
| Log detected intrusion attempts | All violations logged with client IP, URI, and rule category |
| Alert administrators of detected attacks | Logs routable to alerting systems via standard SLF4J/Logback configuration |
| Maintain up-to-date signature databases | Scanner signatures configurable in `waf-rules.properties`; Spamhaus DROP list auto-refreshed every 4 hours |

---

## PIPEDA and HIPAA Considerations

As a Canadian healthcare EMR, CARLOS must comply with PIPEDA (Personal Information
Protection and Electronic Documents Act) and align with HIPAA standards for
cross-border healthcare data protection.

### PHI-Safe Logging (PIPEDA Principle 7 / HIPAA §164.312)

The WAF is designed to **never log PHI** (Protected Health Information):

- Parameter **values** are never included in WAF logs
- Request **bodies** are never included in WAF logs
- Only structural metadata is logged: client IP, request URI, matched rule category
- This prevents clinical notes, patient names, health insurance numbers, and
  other PHI from appearing in security logs

```
# Example WAF log entry — note: no parameter values
WAF DETECT: ip=10.0.2.15 uri=/CaseManagementEntry.do rule=sqli
WAF BLOCK: ip=203.0.113.50 uri=/login.do rule=scanner
```

### Data Residency (PIPEDA Principle 8)

`GeoIpFilter` restricts access to Canadian IPs by default, supporting PIPEDA's
data residency expectations. This aligns with provincial health information
legislation (e.g., BC PIPA, Ontario PHIPA) that may require healthcare data
to be accessed from within Canadian jurisdiction.

### Access Controls (PIPEDA Principle 7 / HIPAA §164.312(a))

- Rate limiting prevents brute-force authentication attacks
- GeoIP filtering restricts access by geographic origin
- Scanner detection blocks automated reconnaissance
- All WAF controls layer on top of application-level access controls
  (`SecurityInfoManager.hasPrivilege()`)

### HIPAA Technical Safeguards (45 CFR §164.312)

The HIPAA Security Rule does not mandate specific products but requires
"reasonable and appropriate" technical safeguards for ePHI. WAFs are a widely
recognized control for mitigating web application threats to ePHI:

| HIPAA Requirement | Safeguard | CARLOS WAF Implementation |
|---|---|---|
| §164.312(a)(1) | Access Control | GeoIP filtering, rate limiting, scanner blocking |
| §164.312(b) | Audit Controls | WAF violation logging with PHI-safe output |
| §164.312(c)(1) | Integrity Controls | Injection detection prevents unauthorized data modification |
| §164.312(e)(1) | Transmission Security | WAF inspects HTTP layer; TLS at transport layer |

**Note on managed WAF services**: If deploying behind a cloud WAF (AWS WAF,
Cloudflare, Azure WAF), a Business Associate Agreement (BAA) must be executed
with the WAF provider, as the WAF infrastructure may process ePHI present in
HTTP headers or request payloads. CARLOS's embedded servlet filter approach
avoids this requirement since all WAF processing occurs within the application
server.

---

## OWASP WAFEC Evaluation Criteria

The OWASP Web Application Firewall Evaluation Criteria (WAFEC) project defines
a standardized framework for evaluating WAF capabilities. The following table
maps WAFEC v1.0 evaluation categories to the CARLOS implementation:

| WAFEC Category | Description | CARLOS Implementation |
|---|---|---|
| **Deployment Architecture** | Modes of operation, SSL/TLS, HA, inline operation | Embedded servlet filter (host-based); no separate WAF appliance needed. TLS termination at reverse proxy or Tomcat connector level |
| **HTTP Support** | HTTP versions, encoding, protocol validation, authentication | HTTP/1.1 via Tomcat 11; URL decoding and double-encoding detection in `WafFilter.decodeValue()` |
| **Detection Techniques** | Signature-based, anomaly-based, evasion detection | Signature-based (regex patterns aligned to CRS PL1); double-encoding detection; case-insensitive matching |
| **Protection Techniques** | Brute force, cookie tampering, session attacks | Rate limiting (`RateLimitFilter`), CSRF protection (`CSRFGuard 4.5`), session management (`LoginFilter`) |
| **Logging** | Transaction IDs, event logs, sensitive data handling | PHI-safe logging (never logs parameter values); dedicated `waf.filter` and `waf.geoip` log categories; SLF4J/Logback integration |
| **Reporting** | Event reports, formats, distribution | Standard log output routable to centralized logging (ELK, Splunk, CloudWatch); no built-in dashboard |
| **Management** | Policy enforcement, refinement, verification | `waf-rules.properties` for module toggles, path exclusions, limits; detect/enforce mode toggle; `carlos.properties` master switches |
| **Performance** | Latency, throughput under load | Lightweight regex matching in-process; no network hop to external WAF; allowlisted paths (static assets) skip all checks |
| **XML** | XML web services protection, schema validation | Not implemented — CXF web services have their own input validation |

---

## WAF Deployment Best Practices

Based on OWASP WAF Best Practices and NIST SP 800-44 guidance:

### Recommended Deployment Sequence

1. **Deploy in detect mode** (`waf.mode=detect`) with all modules enabled
2. **Monitor WAF logs** for 1-2 weeks under normal clinical workflow traffic
3. **Identify false positives** — add paths to `waf.relaxed.paths` as needed
4. **Switch to enforce mode** (`waf.mode=enforce`) once false positive rate is acceptable
5. **Enable GeoIP filtering** (`WAF_GEOIP_ENABLED=true`) if MaxMind database is available
6. **Enable IP reputation** (`WAF_DROP_LIST_ENABLED=true`) for Spamhaus DROP list blocking
7. **Review and tune** quarterly or after significant application changes

### Monitoring Checklist

- [ ] WAF logs (`waf.filter` category) are collected by centralized logging
- [ ] GeoIP logs (`waf.geoip` category) are collected
- [ ] Alert rules configured for high violation rates (potential active attack)
- [ ] Alert rules configured for scanner detection hits
- [ ] Periodic review of relaxed paths (ensure they're still needed)
- [ ] Spamhaus DROP list refresh verified (check logs for 4-hour refresh cycle)

### What NOT to Do

- **Do not skip detect mode** — false positives in healthcare can block clinical workflows
- **Do not set paranoia level above PL1** without extensive testing against real clinical data
- **Do not allowlist paths unnecessarily** — each allowlisted path bypasses ALL WAF checks
- **Do not log parameter values** — PHI protection is a legal requirement
- **Do not rely solely on the WAF** — it is defense-in-depth, not a replacement for
  secure coding (OWASP Encoder, parameterized queries, `SecurityInfoManager`)

---

## Configuration Reference

### carlos.properties

```properties
# Master WAF switch (default: false)
WAF_ENABLED=false

# GeoIP country filtering (default: false, requires MaxMind GeoLite2-Country.mmdb)
WAF_GEOIP_ENABLED=false

# Spamhaus DROP list IP reputation (default: false, requires WAF_GEOIP_ENABLED)
WAF_DROP_LIST_ENABLED=false
```

### waf-rules.properties

See `/WEB-INF/waf-rules.properties` for the full configuration file with
inline documentation and NIST/OWASP references for each setting.

### Tomcat (server.xml / web.xml)

| Component | Configuration | Always Active |
|---|---|---|
| `RateLimitFilter` | 100 req/min per IP, all paths | Yes |
| `LoginRateLimitFilter` | 10 req/min per IP, `/login.do` only | Yes |
| `StuckThreadDetectionValve` | 300s threshold, 600s interrupt | Yes |
| `RemoteCIDRValve` | RFC 1918 ranges (commented out, enable in production) | No |

---

## Related Issues and Future Work

- **[#1049](https://github.com/carlos-emr/carlos/issues/1049)** — Investigate
  Tomcat `maxPostSize` mismatch with upload servlet configs
- **[#1050](https://github.com/carlos-emr/carlos/issues/1050)** — Add
  `ResponseSanitizationFilter` to strip stack traces from error responses
  (NIST SI-11, OWASP ASVS 7.4.1, CRS 950xxx-959xxx data leakage equivalent)
- **Content Security Policy (CSP)** — Not currently implemented due to extensive
  inline scripts in JSP pages. Requires incremental migration to external scripts
  before CSP can be enforced.
- **HTTP Strict Transport Security (HSTS)** — Requires HTTPS configuration in
  production. Add via `ResponseDefaultsFilter` once TLS is deployed.
- **Cross-Origin-Opener-Policy (COOP)** — Cannot be adopted until the 40+
  `window.opener` popup patterns in JSP files are refactored.

---

## Normative References

| Document | Version | Date | URL |
|---|---|---|---|
| OWASP Core Rule Set | v4.8.0 | October 2024 | https://coreruleset.org/ |
| OWASP Top 10 | 2021 | 2021 | https://owasp.org/Top10/ |
| OWASP ASVS | 4.0.3 | 2021 | https://owasp.org/www-project-application-security-verification-standard/ |
| OWASP WAFEC | 1.0 | 2006 | https://owasp.org/www-project-wafec/ |
| OWASP Best Practices: Use of WAFs | 1.05 | 2008 | https://owasp.org/www-community/Web_Application_Firewall |
| NIST SP 800-53 | Rev. 5 Update 1 | January 2024 | https://csrc.nist.gov/pubs/sp/800/53/r5/upd1/final |
| NIST SP 800-44 | Version 2 | September 2007 | https://csrc.nist.gov/pubs/sp/800/44/ver2/final |
| HIPAA Security Rule | 45 CFR §164.312 | 2013 (Omnibus) | https://www.hhs.gov/hipaa/for-professionals/security/ |
| PIPEDA | S.C. 2000, c. 5 | 2000 (amended) | https://laws-lois.justice.gc.ca/eng/acts/P-8.6/ |

---

*Generated with Claude Code — last updated 2026-04-04*
