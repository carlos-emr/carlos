# CSRF Security Evaluation - CARLOS EMR

**Date**: 2026-02-17
**Status**: Research / Recommendation
**Scope**: Evaluate CSRF protection strategy - Spring Security vs OWASP CsrfGuard upgrade vs alternatives

---

## Executive Summary

The current OWASP CsrfGuard 3.1.0 implementation is **effectively non-functional** due to a critical filter ordering bug and extremely low coverage. Only 51 of 1,304 JSPs include the token injection script, and the CSRFGuard filter executes *after* the Struts2 filter in web.xml — meaning CSRF tokens are **never validated** for any Struts-handled request.

**Recommendation**: Do NOT migrate to Spring Security. Instead, pursue a layered defense strategy:

1. **Immediate**: Add `SameSite=Lax` cookie attribute (broad baseline protection, zero code changes)
2. **Short-term**: Fix filter ordering, upgrade CsrfGuard to 4.3.x, universal script inclusion
3. **Medium-term**: Global AJAX token injection, convert critical GET state-changes to POST
4. **Ongoing**: Gradual JSP remediation as pages are touched

---

## Current State Assessment

### OWASP CsrfGuard 3.1.0 — What's Deployed

| Component | Status | Notes |
|-----------|--------|-------|
| CsrfGuard dependency | 3.1.0 | Outdated (current is 4.3.x) |
| Filter in web.xml | Configured | `OscarCsrfGuardFilter` mapped to `/*` |
| Properties file | Configured | POST/PUT/DELETE protected, session tokens, 32-char SHA1PRNG |
| JavaScriptServlet | Configured | Serves csrfguard.js at `/csrfguard` |
| Custom multipart handler | Present | `OscarCsrfGuardFilter` handles file uploads |
| Custom logger | Present | `CsrfGuardLogger` writes to `csrf.log` |
| Token REST endpoint | Present | `POST /ws/rs/csrf/getToken` |

### Why It's Broken — The Filter Chain Problem

The web.xml filter-mapping order is:

```
1.  monitoring          /*
2.  struts2             /*     ← Handles all .do requests, STOPS chain
3.  WebServiceSession   /ws/*
4.  ResponseDefaults    /*
5.  PrivacyStatement    *.jsp, *.do, *.html, *.htm
6.  UserActivity        /*
7.  DbConnection        /*
8.  LoggedInUser        /*
9.  PMMFilter           /PMmodule/*
10. ResponseOverride    *.do, *.jsp
11. LoginFilter         /*
12. CSRFGuard           /*     ← Never reached for Struts requests
13. ClickjackFilter     /*
14. XforwardHeader      /*
```

`StrutsPrepareAndExecuteFilter` (position 2) intercepts all requests matching Struts action mappings. For these requests, **it does NOT call `chain.doFilter()`** — it dispatches to the action and renders the response directly. The CSRFGuard filter at position 12 never executes.

**Result**: Every `*.do` endpoint — which includes all 321 Struts2 2Action classes — has **zero CSRF validation**.

### Coverage Gaps

| Anti-Pattern | Count | Severity |
|---|---|---|
| Forms without CSRF tokens | 698 / 701 (99.6%) | **CRITICAL** |
| JSPs without csrfguard.js | 1,253 / 1,304 (96%) | **CRITICAL** |
| GET links performing state changes | 1,050+ across 306 files | HIGH |
| AJAX calls without CSRF tokens | 191+ JSP files | HIGH |
| 2Actions with direct response writes | 102 / 321 (31.8%) | HIGH |
| JSPs using scriptlets | 1,246 / 1,304 (95.5%) | MEDIUM (complicates fix) |
| Struts redirects | 3 | LOW |

---

## Option Analysis

### Option 1: Migrate to Spring Security

Spring Security provides excellent CSRF protection via the Synchronizer Token Pattern with automatic token injection through its tag libraries and Thymeleaf integration.

#### Why It Won't Work Here

**No existing Spring Security infrastructure.** CARLOS has:
- Custom authentication: `LoginFilter` → `LoginCheckLogin` → `Login2Action` (session-based, no Spring Security `AuthenticationManager`)
- Custom authorization: `SecurityInfoManager.hasPrivilege()` called explicitly in every 2Action (not declarative)
- Custom session management: `LoggedInInfo` stored in `HttpSession`, not Spring's `SecurityContext`
- No `WebSecurityConfigurerAdapter` / `SecurityFilterChain`
- No `UserDetailsService` implementation
- No Spring Security filter chain (`DelegatingFilterProxy`, `FilterChainProxy`)
- Only dependency: `spring-security-crypto` (for BCrypt password hashing)

**Struts2 and Spring Security conflict.** Both frameworks want to own the request dispatch pipeline:
- Struts2 uses `StrutsPrepareAndExecuteFilter` to intercept and dispatch
- Spring Security uses `DelegatingFilterProxy` → `FilterChainProxy` with its own filter chain
- Making them cooperate requires careful ordering and custom integration that doesn't exist in either ecosystem

**Scale of rearchitecture required:**
- Implement `UserDetailsService` wrapping existing `ProviderDao` + credential logic
- Implement `AuthenticationProvider` wrapping `LoginCheckLogin`
- Create `SecurityFilterChain` configuration replacing `LoginFilter`
- Map 50+ security privilege objects from `SecurityInfoManager` to Spring Security authorities
- Replace `LoggedInInfo.getLoggedInInfoFromSession()` calls across 321 2Action classes
- Reconcile dual session management (Spring's `SecurityContextHolder` vs custom `LoggedInInfo`)
- Handle the `provider_no` / provider-context pattern that doesn't map to Spring Security's user model
- Risk: Healthcare system stability during transition

**Estimated effort**: 6-12 months of dedicated security architecture work, with high regression risk across the entire application.

**Verdict**: ❌ **Not recommended.** The ROI is terrible for CSRF alone. Spring Security is the right *long-term* direction if you ever do a full framework modernization (Spring Boot + Spring MVC replacing Struts2), but bolting it onto the current Struts2/custom-auth architecture for CSRF alone would be fighting the framework at every turn.

### Option 2: Upgrade OWASP CsrfGuard to 4.3.x

CsrfGuard 4.x brings significant improvements:
- Modular SPI architecture (extensible token management)
- Improved JavaScript injection
- Better SameSite cookie support
- Active maintenance (4.3.0 released 2024)
- Backward-compatible configuration

#### Why It's Necessary But Not Sufficient

**The filter ordering bug exists regardless of version.** Upgrading from 3.1.0 to 4.3.x doesn't fix the web.xml filter chain order. CsrfGuard 4.x still needs to execute *before* Struts2 to validate tokens.

**JavaScript injection is inherently fragile.** CsrfGuard relies on client-side JavaScript to inject tokens into forms and links after DOM load. This breaks when:
- Forms are submitted before DOM is fully loaded
- JavaScript is disabled or blocked
- Scriptlet-generated HTML contains dynamic form actions
- AJAX calls are made outside the overridden XMLHttpRequest path
- 2Actions write directly to response (102 cases), bypassing response wrappers

**Still requires broad remediation.** Even with CsrfGuard 4.x perfectly configured, you need:
- csrfguard.js included in every page
- Filter ordering fixed
- AJAX calls updated
- Direct response writers handled

**Verdict**: ✅ **Necessary upgrade**, but must be combined with other measures.

### Option 3: SameSite Cookies + Fixed CsrfGuard (Recommended)

A layered defense strategy that provides immediate broad coverage with `SameSite` cookies while properly fixing CsrfGuard for defense-in-depth.

---

## Recommended Strategy: Layered CSRF Defense

### Layer 1: SameSite Cookie (Immediate — hours, not days)

**What**: Add `SameSite=Lax` attribute to the JSESSIONID session cookie.

**How**: Add `cookie-config` to `web.xml` `<session-config>`:
```xml
<session-config>
    <session-timeout>120</session-timeout>
    <cookie-config>
        <http-only>true</http-only>
        <secure>false</secure> <!-- set true when HTTPS is enforced -->
        <attribute>
            <attribute-name>SameSite</attribute-name>
            <attribute-value>Lax</attribute-value>
        </attribute>
    </cookie-config>
</session-config>
```

Or via Tomcat's `context.xml`:
```xml
<CookieProcessor sameSiteCookies="lax" />
```

**Why this is powerful**:
- `SameSite=Lax` prevents the browser from sending the session cookie on cross-origin POST, PUT, DELETE requests
- Covers **every endpoint** regardless of filter ordering, JavaScript injection, or direct response writes
- Zero code changes to JSPs, Actions, or JavaScript
- Works for all 701 forms, all 321 2Actions, all AJAX calls
- Supported by all modern browsers (97%+ global coverage as of 2025)

**Limitations**:
- Doesn't protect against same-site attacks (subdomain takeover, XSS-to-CSRF chains)
- GET requests still carry cookies (which is why `Lax` not `Strict` — `Strict` breaks legitimate navigation)
- Older browsers ignore the attribute (IE11, very old mobile browsers)
- Not a substitute for proper token-based CSRF protection — it's a safety net

**This single change goes from 0% to ~95% CSRF coverage overnight.**

### Layer 2: Fix CsrfGuard Filter Ordering (Short-term — days)

**What**: Move the CSRFGuard filter-mapping BEFORE the struts2 filter-mapping in web.xml.

**New order**:
```xml
<!-- CSRF validation MUST happen before Struts2 dispatches -->
<filter-mapping>
    <filter-name>CSRFGuard</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>

<filter-mapping>
    <filter-name>monitoring</filter-name>
    <url-pattern>/*</url-pattern>
    ...
</filter-mapping>

<filter-mapping>
    <filter-name>struts2</filter-name>
    <url-pattern>/*</url-pattern>
    ...
</filter-mapping>
```

**Considerations**:
- CSRFGuard needs access to the `HttpSession`, which the servlet container provides regardless of filter order — it doesn't depend on `LoginFilter` having run first
- Unprotected paths (login, logout, REST APIs) are already configured in `Owasp.CsrfGuard.properties`
- The `OscarCsrfGuardFilter` custom multipart handling will still work since it wraps the request before Struts2 sees it
- **Must test thoroughly** — changing filter order in a healthcare system requires full regression testing

### Layer 3: Upgrade to CsrfGuard 4.3.x (Short-term — days)

**What**: Upgrade the Maven dependency from 3.1.0 to 4.3.0.

**Migration notes** (3.x → 4.x):
- Configuration property names remain backward-compatible
- `OscarCsrfGuardFilter` may need minor updates to match 4.x API changes
- The `CsrfGuardLogger` custom logger needs to implement the 4.x SPI interface
- The `csrfguard.js` file should be replaced with the 4.x version
- New features to enable:
  - Token-per-page (improves security, was disabled in 3.x config)
  - SameSite cookie attribute on CsrfGuard's own cookie (if it sets one)

### Layer 4: Universal Script Inclusion (Short-term — days)

**What**: Ensure `csrfguard.js` is loaded on every page via a common include.

**Current state**: Only 51 of 1,304 JSPs include it.

**Approach**: Identify the common JSP header/include that most pages already use and add the script tag there. In CARLOS, look for a shared header fragment (e.g., `header.jsp`, `taglibs.jsp`, or similar) that is `<%@ include>`'d or `<jsp:include>`'d across pages.

```jsp
<script src="<%=request.getContextPath()%>/csrfguard"></script>
```

If no single universal include exists, add it to the most common layout templates. Even covering the top 20 most-used templates would dramatically increase coverage.

### Layer 5: Global AJAX CSRF Token Injection (Short-term — 1 file)

**What**: Add a global jQuery `ajaxSetup` that includes the CSRF token on every AJAX request.

**How**: Create or update a common JavaScript include:
```javascript
// csrf-setup.js - include after jQuery and csrfguard.js
(function() {
    var token = document.querySelector('meta[name="csrf-token"]');
    if (token) {
        $.ajaxSetup({
            beforeSend: function(xhr) {
                xhr.setRequestHeader('CSRF-TOKEN', token.getAttribute('content'));
            }
        });
    }

    // Also handle fetch() if used
    var originalFetch = window.fetch;
    window.fetch = function(url, options) {
        options = options || {};
        options.headers = options.headers || {};
        if (token) {
            options.headers['CSRF-TOKEN'] = token.getAttribute('content');
        }
        return originalFetch.call(this, url, options);
    };
})();
```

Add a CSRF meta tag to the common header:
```jsp
<meta name="csrf-token" content="<csrf:tokenvalue/>"/>
```

This approach covers all 191+ JSP files with AJAX calls without modifying each one individually.

### Layer 6: Gradual Remediation (Ongoing)

As JSPs and Actions are touched for other work:

1. **Convert state-changing GET links to POST forms** — The 1,050+ GET parameter links that perform writes are a separate vulnerability (they're susceptible to simple link-based attacks even without CSRF). Convert the most critical ones first (admin operations, patient data modifications, billing actions).

2. **Add explicit CSRF tokens to forms** where JavaScript injection is insufficient — Some forms are dynamically generated, submitted immediately, or exist in iframes where the parent's csrfguard.js doesn't reach.

3. **Fix direct response writers** — The 102 2Actions that write directly to `response.getWriter()` should validate CSRF tokens explicitly at the start of their `execute()` method:
   ```java
   // In 2Actions that bypass Struts result chain
   CsrfGuard guard = CsrfGuard.getInstance();
   if (!guard.isValidRequest(request, response)) {
       response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF validation failed");
       return null;
   }
   ```

---

## Implementation Priority Matrix

| Phase | Action | Effort | Coverage Gain | Risk |
|-------|--------|--------|---------------|------|
| **1** | SameSite=Lax cookie | Hours | 0% → ~95% | Very Low |
| **2** | Fix filter ordering in web.xml | Hours | Enables Layer 3+ | Medium (needs testing) |
| **3** | Upgrade CsrfGuard 3.1.0 → 4.3.x | Days | Better token management | Medium |
| **4** | Universal csrfguard.js inclusion | Days | 51 → ~1000+ JSPs covered | Low |
| **5** | Global AJAX token injection | Hours | All jQuery AJAX covered | Low |
| **6** | Convert critical GET→POST | Weeks | Eliminates link-based attacks | Medium |
| **7** | Fix direct response writers | Weeks | 102 more endpoints covered | Medium |
| **8** | Per-page remediation | Ongoing | Edge cases | Low |

---

## Why NOT Spring Security (Detailed)

For readers who want the full reasoning:

### Architecture Mismatch

CARLOS's security model is **procedural and explicit**: every 2Action manually calls `securityInfoManager.hasPrivilege()`. Spring Security's model is **declarative and filter-based**: security rules are configured centrally and enforced by the filter chain before the controller ever executes.

These two models can coexist (Spring Security for CSRF + authentication, SecurityInfoManager for fine-grained authorization), but the integration surface is enormous:

- **321 2Action classes** use `LoggedInInfo.getLoggedInInfoFromSession(request)` — this would need to bridge to Spring Security's `SecurityContextHolder.getContext().getAuthentication()`
- **Session management** is tightly coupled: `LoggedInInfo` stores provider context, facility, locale, etc. that don't map to Spring Security's `UserDetails`
- **Multi-facility support** means the same user has different roles in different facilities — this requires a custom `AuthenticationProvider` that's context-aware

### The "Just Use It For CSRF" Trap

You might think: "Just add Spring Security for CSRF, keep everything else." But Spring Security's CSRF protection requires:

1. `SecurityFilterChain` bean configured
2. `CsrfFilter` in the filter chain
3. CSRF token available in the request via `CsrfTokenRepository`
4. JSPs to use `<sec:csrfInput/>` or `${_csrf.token}` in forms

Item 4 means touching every JSP form anyway — the same remediation effort as fixing CsrfGuard. But with CsrfGuard's JavaScript injection, many forms get token injection *automatically* without JSP changes. Spring Security has no equivalent automatic injection mechanism for plain JSP (it works with Thymeleaf, not JSP scriptlets).

So Spring Security would actually require MORE JSP changes than properly configured CsrfGuard.

### When Spring Security DOES Make Sense

Spring Security becomes the right choice when CARLOS migrates to:
- Spring Boot (replacing manual Spring XML configuration)
- Spring MVC (replacing Struts2)
- Thymeleaf or modern templates (replacing JSP scriptlets)

At that point, Spring Security CSRF "just works" because the entire stack is integrated. Bolting it onto Struts2 + JSP scriptlets + custom auth gives you the worst of both worlds.

---

## Risk Assessment

### Current Risk: CRITICAL

- CSRF protection is effectively **non-existent** due to the filter ordering bug
- A crafted page could force any authenticated provider to:
  - Modify patient records
  - Create/edit prescriptions
  - Change billing information
  - Modify system settings
  - Any state-changing operation the provider has access to

### Risk After Layer 1 (SameSite): LOW-MEDIUM

- Cross-origin CSRF attacks blocked by browser for POST/PUT/DELETE
- Remaining risks: same-site attacks, older browsers, GET-based state changes
- This is acceptable as a baseline while implementing deeper layers

### Risk After Layers 1-5: LOW

- Defense-in-depth with both SameSite cookies and CsrfGuard tokens
- AJAX calls covered via global token injection
- Residual risk from direct response writers and GET-based state changes
- Comparable to or better than most healthcare applications

---

## References

- [OWASP CsrfGuard 4.x Documentation](https://github.com/OWASP/www-project-csrfguard)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [SameSite Cookie Attribute (RFC 6265bis)](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis)
- [Struts2 Filter Execution Order](https://struts.apache.org/core-developers/web-xml.html)

---

*Generated with Claude Code — CARLOS EMR Security Evaluation*
