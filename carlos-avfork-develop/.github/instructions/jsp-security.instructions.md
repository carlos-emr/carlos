---
description: "OWASP encoding and security rules for JSP view files"
applyTo: "**/*.jsp"
---

# JSP Security Rules

Every JSP file MUST include OWASP Encoder output encoding for ALL user data.

## Required Taglib (add to every new JSP)

```jsp
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
```

**IMPORTANT**: Use URI `owasp.encoder.jakarta` (Jakarta EE), NOT the legacy URI. Wrong URI causes JSPC CI failures.

## Encoding Rules

- **HTML body**: `${e:forHtml(value)}` (NOT `<c:out>` or `fn:escapeXml()`)
- **HTML attribute**: `${e:forHtmlAttribute(value)}`
- **JavaScript string**: `${e:forJavaScript(value)}`
- **JS in HTML attr**: `${e:forJavaScriptAttribute(value)}`
- **CSS string**: `${e:forCssString(value)}`
- **URL path**: `${e:forUri(value)}`
- **URL parameter**: `${e:forUriComponent(value)}`

## CSRF Protection

CSRF tokens are auto-injected by CSRFGuard 4.5. Do NOT add manual CSRF hidden inputs.

## Legacy Patterns to Avoid in New Code

- `<c:out value="${...}" />` -- use `${e:forHtml(...)}` instead
- `fn:escapeXml()` -- use `${e:forHtml(...)}` instead
- Raw `${variable}` without encoding -- NEVER output user data unencoded
