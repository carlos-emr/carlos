# Migrate Apache HttpClient 4.5.14 to HttpClient 5.x

**Issue Type:** Maintenance
**Labels:** hibernate-6-prep
**Priority:** 3-Medium

## Summary

Migrate from Apache HttpClient 4.5.14 (`org.apache.httpcomponents:httpclient`) to HttpClient 5.x (`org.apache.httpcomponents.client5:httpclient5`) as part of Jakarta EE alignment. HttpClient 4.x is in maintenance mode; 5.x is required for long-term compatibility and is what CXF 4.2+ uses internally.

## Current State

**Dependencies (pom.xml lines 322-332):**
- `org.apache.httpcomponents:httpclient:4.5.14`
- `org.apache.httpcomponents:httpmime:4.5.14`

**14 files** with direct HttpClient usage across 10 functional areas, plus 4 files using only utility classes.

## Affected Files by Functional Area

### Fax Integration (3 files, Medium complexity)
- `src/main/java/.../fax/provider/MiddlewareFaxProviderClient.java` — REST with Basic Auth, RequestConfig (30s/60s timeouts)
- `src/main/java/.../fax/provider/SRFaxProviderClient.java` — Form-encoded POST to SRFax API
- `src/main/java/.../fax/admin/ManageFaxes2Action.java` — PUT with Basic Auth (uses deprecated `DefaultHttpClient`)

### BC Provincial Healthcare (2 files, HIGH complexity)
- `src/main/java/.../billings/ca/bc/Teleplan/TeleplanAPI.java` — **Highest risk.** Stateful sessions with persistent cookies, multipart file uploads, custom User-Agent. Talks to BC MSP billing broker.
- `src/main/java/.../lab/ca/bc/PathNet/Communication/HTTP.java` — Simple GET with query params for lab results

### Ontario Provincial Healthcare (1 file, Low complexity)
- `src/main/java/.../utility/OntarioMD.java` — SOAP POST to Ontario Health network with XXE protection

### Oscar-to-Oscar HL7 (2 files, Medium complexity)
- `src/main/java/.../commn/hl7/v2/oscar_to_oscar/SendingUtils.java` — Multipart POST with custom SSL/TLS. Uses deprecated `DefaultHttpClient`, `MultipartEntity`, `SSLSocketFactory`.
- `src/main/java/.../commn/hl7/v2/oscar_to_oscar/ByteArrayBody.java` — Custom multipart body extending `AbstractContentBody`

### Email (1 file, Medium complexity)
- `src/main/java/.../email/helpers/APISendGridEmailSender.java` — POST with custom SSLContext, bearer auth

### Dashboard Integration (1 file, Low complexity)
- `src/main/java/.../integration/dashboard/OutcomesDashboardUtils.java` — Form POST (uses deprecated `DefaultHttpClient`)

### DHIR Immunization (1 file, Medium complexity)
- `src/main/webapp/oscarPrevention/dhirSubmission.jsp` — SSL client in JSP scriptlet (should be extracted to Java class)

### Utility-only imports (4 files, Trivial)
- `InboxResultsDaoImpl.java` — `DateUtils.parseDate()` only
- `DmsInboxManage2Action.java` — `DateUtils` only
- `ProductDispensingService.java` — `DateUtils` only
- `MeasurementHL7Uploader2Action.java` — `HttpStatus` constants only

### Commented/unused (1 file)
- `CanadianVaccineCatalogueManager.java` — Commented-out HttpClient SSL code

### CXF transitive dependency
- CXF 4.1.5 uses HttpClient 4.x internally via `cxf-rt-transports-http`
- CXF 4.2+ switches to HttpClient 5 internally — alignment happens naturally after CXF upgrade

## Key API Changes (4.x → 5.x)

| HttpClient 4.x | HttpClient 5.x |
|---|---|
| `org.apache.http.impl.client.CloseableHttpClient` | `org.apache.hc.client5.http.impl.classic.CloseableHttpClient` |
| `org.apache.http.impl.client.HttpClients` | `org.apache.hc.client5.http.impl.classic.HttpClients` |
| `org.apache.http.client.methods.HttpGet/Post/Put` | `org.apache.hc.client5.http.classic.methods.HttpGet/Post/Put` |
| `org.apache.http.client.config.RequestConfig` | `org.apache.hc.client5.http.config.RequestConfig` |
| `org.apache.http.util.EntityUtils` | `org.apache.hc.core5.http.io.entity.EntityUtils` |
| `org.apache.http.entity.mime.MultipartEntityBuilder` | `org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder` |
| `org.apache.http.impl.client.DefaultHttpClient` | **Removed** — use `HttpClients.createDefault()` |
| `org.apache.http.impl.cookie.DateUtils` | `org.apache.hc.client5.http.utils.DateUtils` |
| `httpclient` + `httpmime` (2 artifacts) | `httpclient5` (single artifact, multipart built-in) |

**Timeout method renames:** `setSocketTimeout()` → `setResponseTimeout()`, `setConnectTimeout()` stays but uses `Timeout` objects.

## Migration Plan

### Phase 1 — Fix deprecated 4.x patterns (prep PR, no dependency change)
- [ ] Replace `DefaultHttpClient` → `HttpClients.createDefault()` in SendingUtils, ManageFaxes2Action, OutcomesDashboardUtils
- [ ] Replace deprecated `MultipartEntity` → `MultipartEntityBuilder` in SendingUtils
- [ ] Extract DHIR HttpClient code from `dhirSubmission.jsp` into a proper Java class
- [ ] Remove commented-out HttpClient code in CanadianVaccineCatalogueManager

### Phase 2 — Upgrade to HttpClient 5 (single PR)
- [ ] Run OpenRewrite `UpgradeApacheHttpClient_5` recipe for mechanical import/class changes
- [ ] Swap pom.xml: remove `httpclient` + `httpmime`, add `httpclient5`
- [ ] Manually fix SSL/TLS config in SendingUtils, APISendGridEmailSender, dhirSubmission
- [ ] Manually fix cookie/session management in TeleplanAPI
- [ ] Manually fix timeout config (method renames, `Timeout` objects)
- [ ] Rewrite `ByteArrayBody` to extend HttpClient 5's `AbstractContentBody`
- [ ] Update `DateUtils` imports in 3 utility files
- [ ] Update `HttpStatus` import in MeasurementHL7Uploader2Action
- [ ] Build and run full test suite

### Phase 3 — CXF alignment (after CXF 4.2 upgrade)
- [ ] Verify CXF 4.2 transitive HttpClient 5 aligns with direct dependency
- [ ] Remove any HttpClient 4.x exclusions if present

## Risk Assessment

| Area | Risk | Reason |
|---|---|---|
| **TeleplanAPI** (BC billing) | **Critical** | Stateful sessions, cookies, multipart, province-specific. Cannot test without BC MSP broker access. |
| **SendingUtils** (HL7) | High | Custom SSL, fully deprecated API surface, inter-EMR communication |
| **APISendGridEmailSender** | Medium | Custom SSL context with client certs |
| **Fax providers** | Low-Medium | Already use modern 4.x patterns (builder, RequestConfig) |
| **PathNet, OntarioMD, Dashboard** | Low | Simple request-response |
| **Utility imports** | Trivial | Just import renames |

## Tooling

- **OpenRewrite** [`UpgradeApacheHttpClient_5`](https://docs.openrewrite.org/recipes/apache/httpclient5/upgradeapachehttpclient_5) handles ~70% of mechanical changes
- **HttpClient 5 "classic" API** is intentionally close to 4.x, reducing manual effort
- 14 files total — significantly smaller surface than other Jakarta migrations

## References

- [HttpClient 5.x Migration Guide](https://hc.apache.org/httpcomponents-client-5.5.x/migration-guide/index.html)
- [OpenRewrite HttpClient 5 Recipe](https://docs.openrewrite.org/recipes/apache/httpclient5/upgradeapachehttpclient_5)
- [Community Migration Guide](https://ok2c.github.io/httpclient-migration-guide/)
