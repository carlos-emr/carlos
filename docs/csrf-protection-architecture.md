# CSRF Protection Architecture — CSRFGuard 4.5

> **Version**: OWASP CSRFGuard 4.5.0
> **Migrated from**: CSRFGuard 3.1.0 (February 2026)
> **Configuration**: `src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties`

## Overview

CARLOS EMR uses [OWASP CSRFGuard 4.5](https://github.com/OWASP/www-project-csrfguard) for
Cross-Site Request Forgery protection. The implementation uses the **Synchronizer Token Pattern**
with session-scoped tokens, validated server-side on every state-changing request.

CSRF tokens are **automatically injected** into forms and AJAX requests by client-side JavaScript.
Developers do **not** need to manually add CSRF tokens to JSPs or AJAX calls.

---

## Filter Chain Architecture

Four components work together (two custom filters and one servlet registered in `web.xml`, plus one request wrapper instantiated internally by the filter):

### 1. `CsrfGuardScriptInjectionFilter` (mapped to `/*`)

**Class**: `io.github.carlos_emr.carlos.app.CsrfGuardScriptInjectionFilter`

Auto-injects `<script src="contextPath/csrfguard"></script>` into every HTML response before
`</head>`. This eliminates the need to manually add the script tag to 1,200+ JSPs.

**How it works:**
- Wraps the `HttpServletResponse` with a `CaptureResponseWrapper` that captures `PrintWriter`
  output into a `CharArrayWriter`
- Binary responses (PDFs, images) using `getOutputStream()` pass through untouched
- After the filter chain completes, injects the script tag and writes the modified HTML

**Injection is skipped when:**
- Content-Type is not `text/html`
- The request is AJAX (`X-Requested-With: XMLHttpRequest`)
- CSRFGuard is disabled
- The response already contains a `/csrfguard` script reference (idempotency)
- The response used `getOutputStream()` instead of `getWriter()`
- The response was committed by `sendRedirect()` or `sendError()` during downstream processing

### 2. `CarlosCsrfGuardFilter` (mapped to `/*`)

**Class**: `io.github.carlos_emr.carlos.app.CarlosCsrfGuardFilter`

Validates CSRF tokens on incoming requests. Operates in **blocking mode** — requests that
fail validation are rejected, and configured actions (Log + Redirect) handle the response.

**Why a custom filter?** The stock `org.owasp.csrfguard.CsrfGuardFilter` does not wrap
`multipart/form-data` requests. When CSRFGuard extracts the CSRF token from a multipart
request body, it consumes the input stream, breaking downstream file upload processing.

**Multipart handling:**
- Detects multipart requests via `ServletFileUpload.isMultipartContent()`
- Wraps them with `MultiReadHttpServletRequest` so the body can be read twice — once by
  `CsrfValidator` for token extraction, and again by downstream servlets

### 3. `MultiReadHttpServletRequest`

**Class**: `io.github.carlos_emr.carlos.app.MultiReadHttpServletRequest`

`HttpServletRequestWrapper` that caches the request body in a `ByteArrayOutputStream` on first
read and returns a new `ByteArrayInputStream` on subsequent reads. Used exclusively by
`CarlosCsrfGuardFilter` for multipart request dual-read support.

### 4. CSRFGuard JavascriptServlet (mapped to `/csrfguard`)

**Class**: `org.owasp.csrfguard.servlet.JavaScriptServlet` (provided by CSRFGuard library)

Serves the dynamically generated `csrfguard.js` script that handles all client-side token
injection. Configured in `web.xml` as the `CsrfServlet` servlet.

---

## Client-Side Token Injection

The `csrfguard.js` script (served by the JavascriptServlet) automatically handles:

| Feature | Config Property | Current Value |
|---------|----------------|---------------|
| Hidden field injection into `<form>` elements | `injectIntoForms` | `true` |
| Token appended to form `action` URL | `injectFormAttributes` | `false` (redundant with hidden field; leaks token in URLs) |
| XHR/XMLHttpRequest header injection | controlled by `org.owasp.csrfguard.Ajax` | `true` |
| Dynamic DOM node token injection | `injectIntoDynamicNodes` | `true` (MutationObserver) |
| GET form injection | `injectGetForms` | `false` (GET is unprotected) |
| Link/attribute (`href`/`src`) injection | `injectIntoAttributes` | `false` (prevents token leakage via Referer) |
| Unprotected static file extensions | `UnprotectedExtensions` | `css,js,png,jpg,gif,svg,ico,woff,woff2,ttf,eot` |

**What this means for developers:**
- POST forms automatically get a hidden `CSRF-TOKEN` field injected by JavaScript
- `XMLHttpRequest` calls automatically get the token in a custom header
- Dynamically created DOM nodes (via AJAX page loads, JavaScript DOM manipulation) are
  automatically scanned and injected via `MutationObserver`
- **No manual token handling is needed in JSPs or JavaScript**

### Property Key Gotchas

CSRFGuard 4.5 has inconsistent property key naming. Several keys differ from what the
documentation or upstream examples suggest. Using the wrong key causes silent fallback to
defaults with no error logged. The correct keys (verified against the 4.5.0 JAR bytecode):

| Property | Correct Key | Common Wrong Key |
|----------|-------------|-----------------|
| Dynamic node injection | `...JavascriptServlet.injectIntoDynamicNodes` | `...injectIntoDynamicallyCreatedNodes` |
| Unprotected extensions | `...JavascriptServlet.UnprotectedExtensions` (capital U) | `...unprotectedExtensions` |
| Force synchronous AJAX | `org.owasp.csrfguard.forceSynchronousAjax` (no `JavascriptServlet.` prefix) | `...JavascriptServlet.forceSynchronousAjax` |
| XHR injection | `org.owasp.csrfguard.Ajax` | `...JavascriptServlet.injectIntoXhr` (does not exist) |

**To verify correct rendering**: Fetch the generated JavaScript from `/carlos/csrfguard` and
check that the substituted values match your configuration. Wrong property keys result in
default values being rendered silently.

---

## Token Configuration

| Property | Value | Purpose |
|----------|-------|---------|
| `TokenName` | `CSRF-TOKEN` | HTTP parameter name for the token |
| `SessionKey` | `CSRF-SESSION-KEY` | HttpSession attribute key for token storage |
| `TokenLength` | `32` | Characters in the generated token |
| `PRNG` | `SHA1PRNG` | Cryptographic PRNG algorithm |
| `TokenPerPage` | `false` | Single session-scoped token (not per-page) |
| `Rotate` | not set (default: `false`) | Token rotation disabled (see below) |

---

## Protected vs Unprotected

### Protected HTTP Methods

```properties
org.owasp.csrfguard.ProtectedMethods=POST,PUT,DELETE,PATCH
```

GET requests are unprotected by design — they should be idempotent/read-only per HTTP
specification. All state-changing operations must use POST/PUT/DELETE/PATCH.

### Unprotected Pages

```properties
org.owasp.csrfguard.unprotected.Soap=%servletContext%/ws/*
org.owasp.csrfguard.unprotected.Login=%servletContext%/index.jsp
org.owasp.csrfguard.unprotected.LoginDo=%servletContext%/login.do
org.owasp.csrfguard.unprotected.Logout=%servletContext%/logout.jsp
org.owasp.csrfguard.unprotected.Rest=%servletContext%/ws/rs/*
org.owasp.csrfguard.unprotected.IconFiles=*.ico
```

Login, logout, SOAP/REST web service endpoints, and static icon files are excluded from
CSRF validation. SOAP/REST endpoints use their own authentication (OAuth 1.0a).

---

## Violation Handling

When a CSRF violation is detected:

1. **Log action** records the violation:
   ```
   CSRF violation: (user:%user%, ip:%remote_ip%, method:%request_method%, uri:%request_uri%, error:%exception_message%)
   ```
2. **Redirect action** sends the user to `/errorpage.jsp`

The `CarlosCsrfGuardFilter` logs a WARN and returns (the configured actions have already
executed by the time `CsrfValidator.isValid()` returns `false`).

---

## Referer Header Validation

CSRFGuard's JavascriptServlet supports Referer header checking as a defense-in-depth measure
against JavaScript Hijacking attacks that attempt to steal CSRF tokens from the dynamically
generated JavaScript file.

### Current Configuration

```properties
# Domain-based matching (ENABLED) — validates that the Referer header's domain matches
# the request's domain before serving csrfguard.js. If no Referer header is present
# (e.g., proxy strips it), the request is allowed through (no failure).
org.owasp.csrfguard.JavascriptServlet.refererMatchDomain = true

# Regex-based matching — evaluated ONLY when refererMatchDomain is false.
# When refererMatchDomain is true (as configured above), this pattern is NOT evaluated.
org.owasp.csrfguard.JavascriptServlet.refererPattern = .*
```

### When to Change These Settings

**`refererMatchDomain = true`** (current, recommended):
- Automatically compares the Referer header's host against the request's `Host` header
- Works correctly across all deployment domains without configuration changes
- Allows requests with no Referer header (common with proxies, privacy settings)
- **This is the recommended setting for most deployments**

**When to switch to `refererPattern` instead:**
- Set `refererMatchDomain = false` and configure `refererPattern` **only if** you need
  to restrict the JavascriptServlet to specific URL patterns beyond domain matching
- Example: multi-tenant deployment where you want to restrict by subdomain or path
- The pattern is a Java regex matched against the full Referer URL

**`refererPattern` examples:**
```properties
# Allow only HTTPS from a specific domain:
org.owasp.csrfguard.JavascriptServlet.refererPattern = https://emr\.example\.com/.*

# Allow HTTP or HTTPS from a specific domain:
org.owasp.csrfguard.JavascriptServlet.refererPattern = https?://emr\.example\.com/.*

# Allow any subdomain of example.com:
org.owasp.csrfguard.JavascriptServlet.refererPattern = https?://[a-z]+\.example\.com/.*
```

**Important**: When `refererMatchDomain = true`, the `refererPattern` value is irrelevant —
it is not evaluated. Change `refererPattern` only if you also set `refererMatchDomain = false`.

---

## Token Rotation — Intentionally Disabled

Token rotation (`org.owasp.csrfguard.Rotate`) is **intentionally disabled** and must not be
enabled without significant engineering work.

### Why rotation is disabled

1. **Multi-tab breakage**: CSRFGuard's `TokenHolder` is per-session. When `Rotate=true`, a
   successful POST from Tab A rotates the token, instantly invalidating the token held by
   Tab B's JavaScript. EMR users routinely have 5-10 tabs open (patient charts, scheduling,
   billing). The next action in Tab B fails with a CSRF violation.

2. **AJAX race conditions**: With `org.owasp.csrfguard.Ajax=true`, concurrent AJAX requests (auto-save,
   lab polling, messaging) race to use the pre-rotation token. Only the first succeeds.

3. **Clinical data loss**: A CSRF validation failure during prescription save or clinical
   note submission means lost work in a healthcare context.

4. **OWASP alignment**: The [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
   recommends the Synchronizer Token Pattern without requiring per-request rotation.
   Per-session tokens with HTTPS are considered sufficient. CSRFGuard ships with
   `Rotate=false` as the default.

### What enabling rotation would require

Beyond setting `Rotate=true`, you would need:
- A `BroadcastChannel` or `localStorage`-based mechanism to propagate new tokens to all
  open tabs after each rotation
- Retry logic for AJAX requests that fail during token propagation lag
- Extensive testing across all multi-tab clinical workflows
- This is high-effort, high-risk, low-value given that session tokens already prevent CSRF
  when combined with HTTPS and SameSite cookies

### Rotate property vs Rotate action

CSRFGuard has two independent "rotate" concepts:
- **`org.owasp.csrfguard.Rotate`** (boolean property) — rotates token after every *successful*
  validation. This is what's discussed above.
- **`org.owasp.csrfguard.action.Rotate`** (action class) — rotates token on *validation failure*
  as a defensive measure. This is not configured in CARLOS because it would compound the
  multi-tab problems described above.

---

## SLF4J Logging Bridge

CSRFGuard 4.x uses SLF4J 2.x natively. The `log4j-slf4j2-impl` bridge dependency routes
SLF4J calls to the application's Log4j2 backend:

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
    <version>${log4j2.version}</version>
</dependency>
```

Without this bridge, CSRFGuard logging silently falls back to SLF4J's NOP logger and all
CSRF-related log messages (including violation warnings) are lost.

**Logger name**: `org.owasp.csrfguard` — configure level in `log4j2.xml`.

---

## Troubleshooting

### CSRF validation failures after login

**Symptom**: First POST after login fails with CSRF violation, then works after redirect.

**Cause**: Session does not yet have tokens generated. The `CarlosCsrfGuardFilter` generates
tokens via `TokenService.generateTokensIfAbsent()` on each request, but if the first request
to a protected page is a POST, no token exists yet.

**Fix**: Ensure `index.jsp` and `login.do` are in the unprotected pages list (they are).
The login flow creates the session and generates tokens on the first GET to a protected page.

### Forms not submitting (403 or redirect to error page)

**Check**:
1. View page source — confirm `<script src="/carlos/csrfguard"></script>` is present in `<head>`
2. Browser dev tools (Network tab) — confirm the `csrfguard` script loaded successfully
3. Browser dev tools (Elements tab) — confirm forms have a hidden `CSRF-TOKEN` input
4. If the script tag is missing, check `CsrfGuardScriptInjectionFilter` is mapped in `web.xml`
5. If the hidden input is missing, check browser console for JavaScript errors

### AJAX requests failing with CSRF violation

**Check**:
1. Confirm `org.owasp.csrfguard.Ajax=true` in `Owasp.CsrfGuard.properties`
2. Confirm the AJAX request is using `XMLHttpRequest` (not `fetch` without interception)
3. CSRFGuard's `csrfguard.js` patches `XMLHttpRequest.prototype.open` and `send` — if a
   library replaces these after the script loads, injection breaks
4. For `fetch()` API calls: CSRFGuard 4.5 does **not** auto-intercept `fetch()`. Use
   `XMLHttpRequest` or manually include the token (see JSP Refactoring Guide, Step 5.3)

### No CSRF log messages appearing

**Check**:
1. Confirm `log4j-slf4j2-impl` is in the deployed WAR (`WEB-INF/lib/log4j-slf4j2-impl-*.jar`)
2. If missing, SLF4J falls back to NOP logger. Check Tomcat startup logs for:
   `SLF4J(W): No SLF4J providers were found`
3. Configure logger level in `log4j2.xml`: `<Logger name="org.owasp.csrfguard" level="debug"/>`

### Large pages (>1 MB) not getting CSRF script injected

The `CsrfGuardScriptInjectionFilter` increases the response buffer to 1 MB. Pages larger than
this may flush content to the client before script injection can occur. If this is an issue,
increase the buffer size in `CsrfGuardScriptInjectionFilter.CaptureResponseWrapper` constructor.

---

## Configuration Source of Truth

### Single Source of Truth: `Owasp.CsrfGuard.properties`

**All functional CSRFGuard configuration must live in `Owasp.CsrfGuard.properties`.** This is the
CSRFGuard 4.x design intent — the properties file is authoritative for every setting that controls
CSRF behaviour.

`web.xml` is intentionally minimal. It contains **only the bootstrap wiring** that the servlet
container requires:

| What | Why it must be in web.xml |
|------|--------------------------|
| `Owasp.CsrfGuard.Config` context-param | Tells the `CsrfGuardServletContextListener` where to find the properties file |
| `CsrfGuardServletContextListener` | Loads CSRFGuard at application startup |
| `CsrfGuardHttpSessionListener` | Cleans up tokens when sessions are destroyed |
| `CarlosCsrfGuardFilter` + mapping | Registers the custom validation filter |
| `CsrfServlet` + mapping (`/csrfguard`) | Registers the servlet that serves `csrfguard.js` |

Nothing else should be in `web.xml` for CSRFGuard. Adding functional configuration there creates
a dual-source-of-truth problem: a developer reading only the properties file would not see the
override, and removing the `web.xml` override (thinking the properties file is authoritative)
silently changes behavior.

### Environment-Specific Overrides: The Overlay File

For settings that legitimately differ between environments (development vs production), use the
**CSRFGuard overlay file** rather than `web.xml` context-params.

The overlay file is picked up automatically because `Owasp.CsrfGuard.properties` configures
`ConfigurationAutodetectProviderFactory` with this hierarchy:

```properties
org.owasp.csrfguard.configOverlay.hierarchy = \
    classpath:Owasp.CsrfGuard.properties, \
    classpath:Owasp.CsrfGuard.overlay.properties
```

Files on the right override files on the left. `Owasp.CsrfGuard.overlay.properties` is resolved
from the runtime classpath, which in Tomcat includes `WEB-INF/classes/`. If the file does not
exist, the factory silently falls back to the base properties with no error.

#### Creating a Development Overlay

Create `src/main/resources/Owasp.CsrfGuard.overlay.properties` (this ends up in
`WEB-INF/classes/` in the WAR):

```properties
# Owasp.CsrfGuard.overlay.properties — DEVELOPMENT OVERRIDES ONLY
# Do NOT commit this file to production deployments.
# This file is automatically applied on top of Owasp.CsrfGuard.properties
# by ConfigurationAutodetectProviderFactory.

# Enable config dump at startup (useful for debugging token/property issues)
org.owasp.csrfguard.Config.Print = true

# Drop to debug logging (also requires log4j2.xml change: set org.owasp.csrfguard to debug)
# org.owasp.csrfguard.Enabled = true
```

**Important**: Add `Owasp.CsrfGuard.overlay.properties` to `.gitignore` or production deployment
manifests. It should never be committed for production — its absence is the production state.

#### Why Not Use `web.xml` Context-Params for Overrides?

The `web.xml` context-param mechanism (`<context-param>Owasp.CsrfGuard.Config.Print</context-param>`)
is supported by CSRFGuard as a deployment-time escape hatch. It has two problems as a regular
configuration mechanism:

1. **Hidden override**: The properties file appears to be the authoritative source, but the
   `web.xml` param silently wins. Developers reading the properties file see misleading values.
2. **Not scalable**: Each environment-specific value requires a separate `web.xml` edit, which is
   part of the WAR and affects all environments. The overlay file approach is WAR-independent.

The overlay file approach matches how other CARLOS subsystems handle environment variation
(Spring's `over_ride_config.properties` pattern).

### CSRFGuard Configuration Quick Reference

| Setting | Where it lives | Notes |
|---------|---------------|-------|
| Token name, length, PRNG | `Owasp.CsrfGuard.properties` | Always in properties |
| Protected HTTP methods | `Owasp.CsrfGuard.properties` | Always in properties |
| Unprotected pages/URLs | `Owasp.CsrfGuard.properties` | Always in properties |
| JavaScript injection settings | `Owasp.CsrfGuard.properties` | Always in properties |
| Config.Print (production) | `Owasp.CsrfGuard.properties` → `false` | Must be `false` in properties |
| Config.Print (development) | `Owasp.CsrfGuard.overlay.properties` → `true` | Override in overlay only |
| Config file path pointer | `web.xml` | Must stay in web.xml |
| Listeners | `web.xml` | Must stay in web.xml |
| Filter + Servlet registration | `web.xml` | Must stay in web.xml |

---

## File Reference

| File | Purpose |
|------|---------|
| `src/main/java/.../app/CarlosCsrfGuardFilter.java` | CSRF token validation filter |
| `src/main/java/.../app/CsrfGuardScriptInjectionFilter.java` | Auto-injects csrfguard script tag |
| `src/main/java/.../app/MultiReadHttpServletRequest.java` | Multipart request dual-read wrapper |
| `src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties` | CSRFGuard configuration |
| `src/main/webapp/WEB-INF/web.xml` | Filter chain and servlet mappings |
| `pom.xml` | CSRFGuard 4.5.0 + SLF4J bridge dependencies |
| `docs/JSP-REFACTORING-GUIDE.md` (Step 3.5, 5.3) | JSP developer guidance for forms and AJAX |

---

## Migration History

- **February 2026**: Migrated from CSRFGuard 3.1.0 to 4.5.0
  - Replaced `OscarCsrfGuardFilter` (CSRFGuard 3.x API) with `CarlosCsrfGuardFilter`
  - Added `CsrfGuardScriptInjectionFilter` for automatic `<script>` tag injection (new — eliminates manual script tags in JSPs)
  - Removed manual `<script src="csrfguard">` tags from ~51 JSPs (now auto-injected)
  - Removed dead `${_csrf.parameterName}`/`${_csrf.token}` Spring Security EL expressions from ~12 JSPs
  - Added `log4j-slf4j2-impl` bridge for SLF4J 2.x logging
  - Added `PATCH` to protected methods
  - Enabled `refererMatchDomain` for JavaScript Hijacking defense
  - Switched from `DOMNodeInserted` event to `MutationObserver` for dynamic DOM injection
  - Disabled `injectFormAttributes` (redundant with hidden field injection; leaked tokens in URLs)
  - Fixed three silent property key mismatches (see Property Key Gotchas section above):
    - `injectIntoDynamicallyCreatedNodes` → `injectIntoDynamicNodes` (MutationObserver was silently disabled)
    - `unprotectedExtensions` → `UnprotectedExtensions` (static asset list was silently empty)
    - `JavascriptServlet.forceSynchronousAjax` → `forceSynchronousAjax` (wrong prefix, moot with TokenPerPage=false)
  - Added defensive 403 fallback in `CarlosCsrfGuardFilter` for uncommitted invalid responses
  - Added `RollingFile` appender for CSRF logging (10 MB size limit, 7-day retention) with dedicated `org.owasp.csrfguard` logger
