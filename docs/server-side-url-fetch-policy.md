# Server-Side URL Fetch Policy

CARLOS server-side code must not fetch arbitrary URLs. Any code path that opens a
`URL`, `URI`, `URLConnection`, or `InputStream` from a URL must validate the target
before opening the stream.

## Allowed Patterns

### Same-Application JSP Rendering

`Doc2PDF.parseJSP2PDF` contains a legacy server-side fetch used to render an
application JSP into PDF. This path is allowed only for same-application URLs:

- scheme must be `http` or `https`
- host must be loopback or match the servlet request's local connector name/address
- effective port must match the servlet request's local connector port
- path must stay inside `HttpServletRequest.getContextPath()`
- user-info and fragments are rejected

Callers must use the request-aware overload:

```java
Doc2PDF.GetInputFromURI(request, jsessionid, uri);
```

The legacy two-argument overload has no request context and fails closed.

### Drools DRL Resources

`DroolsHelper.loadFromUrl` is for local DRL resources only. It accepts:

- `file:` URLs
- `jar:file:` URLs for classpath resources packaged in local jars or WARs

Network-backed DRL loading is not allowed. The helper rejects `http:`, `https:`,
`ftp:`, `jar:http:`, and other non-local schemes before opening the stream.

## New URL Fetch Code

New server-side fetch code must define its own allowlist before the sink call. Use
the narrowest target set possible: a fixed external service host for integrations,
or same-application validation for internal requests. Do not add generic helpers
that accept arbitrary URL strings.
