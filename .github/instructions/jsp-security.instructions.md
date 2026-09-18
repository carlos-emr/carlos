---
description: "OWASP encoding and security rules for JSP view files"
applyTo: "**/*.jsp"
---

# JSP Security Rules

Every JSP file MUST encode ALL user data with the CARLOS null-safe encoder wrappers.
CI (`scripts/lint/check-encoder-null-safety.sh`) rejects the raw OWASP `e:` forms in new
code because `Encode.forXxx(null)` renders the literal string `null` for nullable fields.

## Required Taglib (add to every new JSP that encodes output)

```jsp
<%@ taglib uri="carlos" prefix="carlos" %>
```

## Encoding Rules (CARLOS null-safe wrappers)

- **HTML body**: `<carlos:encode value="${v}"/>` or `${carlos:forHtmlContent(v)}` (NOT `<c:out>` or `fn:escapeXml()`)
- **HTML attribute**: `${carlos:forHtmlAttribute(v)}` or `<carlos:encode value="${v}" context="htmlAttribute"/>`
- **JavaScript string**: `${carlos:forJavaScript(v)}`
- **JS in HTML attr**: `${carlos:forJavaScriptAttribute(v)}`
- **CSS string**: `${carlos:forCssString(v)}`
- **URL path**: `${carlos:forUri(v)}`
- **URL parameter**: `${carlos:forUriComponent(v)}`
- **Java scriptlets**: `SafeEncode.forHtmlContent(v)` (drop-in null-safe replacement for `Encode.forHtmlContent(v)`)

Prefer the `<carlos:encode>` tag for standalone output; use the `${carlos:forXxx(...)}` EL
functions inline inside attribute strings, URLs, or JSON.

## CSRF Protection

CSRF tokens are auto-injected by CSRFGuard 4.5. Do NOT add manual CSRF hidden inputs.

## Legacy Patterns to Avoid in New Code (CI-enforced)

- `<e:forXxx>` tags and `${e:forXxx(...)}` EL functions -- render null as literal `"null"`; rejected by the encoder null-safety lint
- `<%= Encode.forXxx(...) %>` scriptlets -- use `SafeEncode.forXxx(...)` instead
- `<c:out value="${...}" />` -- use `${carlos:forHtmlContent(...)}` instead
- `fn:escapeXml()` -- use `${carlos:forHtmlContent(...)}` instead
- Raw `${variable}` without encoding -- NEVER output user data unencoded
