# Session cookies

CARLOS uses `Secure` and `HttpOnly` session cookies, cookie-only session tracking,
and `SameSite=Lax`. A session ID in a URL does not authenticate a request.
Lax keeps authenticated top-level navigation from external links working while
excluding cookies from cross-site POSTs. CSRFGuard remains responsible for CSRF
protection; SameSite is an additional browser protection.

Use HTTPS for every browser login. In the devcontainer, open
`https://localhost:8443/carlos` and accept the development certificate locally.
The login and application-health Playwright scripts default to
`https://127.0.0.1:8443/carlos`; they reject HTTP and accept a self-signed
certificate only on exact loopback hosts. Other test hosts need a trusted
certificate. `BASE_URL` selects a different HTTPS deployment.

On packaged installs, use the HTTPS front door provided by the package. Keep the
Tomcat connector private and retain its trusted proxy configuration. Do not
expose plain HTTP as an alternative login endpoint or weaken the cookie flags to
work around a proxy problem. After proxy changes, verify that a login survives a
second page load and that the browser reports `Secure`, `HttpOnly`, and
`SameSite=Lax` for `JSESSIONID`.

The WAR's `META-INF/context.xml` provides the devcontainer SameSite policy.
Packaged installs use `debian/assets/tomcat/context.xml`; their external context
descriptor takes precedence over the WAR's embedded context. Keep the policies
consistent. Cross-site POST-based identity-provider callbacks need their own
reviewed integration; Lax does not make those flows work automatically.

Server-side PDF rendering sends the session cookie only to a validated local
application connector. It does not place credentials in the URL, follow
redirects, or treat an HTTP error response as successful document content.
