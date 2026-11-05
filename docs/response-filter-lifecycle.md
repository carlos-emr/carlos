# Response-filter lifecycle and Tomcat forwards

Decision for [#5487](https://github.com/carlos-emr/carlos/issues/5487): retain
`suspendWrappedResponseAfterForward="false"` as an explicit compatibility
contract in both deployment descriptors. Keep the existing capture limits,
flush suppression and close shielding. JavaMelody 2.9.0 fixes its own forwarded
writer/JSP truncation, but cannot make CARLOS's outer REQUEST capture finish
inside a downstream forward.

## Ownership and dispatch boundaries

The order comes from `src/main/webapp/WEB-INF/web.xml`. The integration fixture
reads those mappings, including their dispatchers; it does not invent a new order.

| Layer | Capture and completion owner | Dispatch contract |
| --- | --- | --- |
| ResponseSanitizationFilter | Owns writer capture and streams opened with an error status; inspects status/body after the downstream chain returns; replays or replaces the body | REQUEST and ERROR; deliberately surrounds every downstream failure |
| JavaMelody MonitoringFilter | Adapts writer output to its monitoring stream; 2.9.0 drains its writer at forward completion | REQUEST and ASYNC; its successful stream can still sit behind CARLOS captures |
| CsrfGuardScriptInjectionFilter | Captures HTML writer output, injects one CSRF script, replays on unwind | FORWARD; already-injected pages are replayed unchanged |
| LogoutBroadcastFilter | Shields completion, defers entity length and adds heartbeat/logout script once for authenticated HTML writer responses | REQUEST and FORWARD; the request marker prevents nested injection |
| PrivacyStatementAppendingFilter | Shields writer flush/close, appends the print notice, then flushes its delegate | REQUEST; binary and AJAX bypass decoration |

A forward first calls `resetBuffer()`. Every capture owner must discard only
uncommitted output, preserving status and headers as the Servlet contract
requires, with one deliberate exception: a Content-Length the decorator itself
holds or published, which described the discarded body (see "Proven
corrections"). Nested forwards repeat that operation; discarded pre-forward content must never
be replayed later. `reset()` additionally resets response state. Do not discard a
capture before a delegated reset succeeds. A rejected reset must preserve the body.

With Tomcat's default, `ApplicationDispatcher` unwraps to `ResponseFacade` after
a forward returns and calls `finish()`. That suspends the underlying output
buffer; later writes are silently discarded. The minimal isolation is a
REQUEST-only sanitizer around a writer: its capture is still full when Tomcat
finishes the raw response, and its subsequent replay produces an empty 200.
JavaMelody 2.9.0 alone preserves the same complete UTF-8 payload. The combined
response-transforming stack still reproduces the empty 200 with the default.
This identifies a sufficient boundary, rather than claiming the sanitizer is the
only wrapper capable of retaining bytes past a forward.

With the explicit `false`, Tomcat's classic forward completion calls close on
the wrapper. CARLOS deliberately shields that close while captures/decorators
still own output. After the downstream chain and its decorators finish, the
sanitizer inspects the final body/status; the container owns final response
completion. Removing those no-op flush/close implementations or close shields
would change ownership and can commit an empty or incomplete response early.

`sendError`/redirect hand ownership to the container rather than replaying stale
capture. The ERROR mapping gives the sanitizer a fresh boundary around the error
resource. Error replacement retains the intended status and removes stale
entity headers/length. Safe binary/static streams keep their exact bytes.
Decorators defer or discard a supplied Content-Length when they alter the body;
a forwarded response must never replay the undecorated byte count.

Capture is bounded at 512 Ki characters/bytes. Above that limit, any captured
4xx/5xx response is replaced with the generic error page, even when its prefix
contains only safe prose. Successful responses switch to pass-through. A stream
opened while the status is still successful is also pass-through; if it later
fails, the sanitizer can replace only bytes that remain uncommitted (the `/ws`
buffer is enlarged for that reason). A writer decides once, when it crosses the
limit: if the status is still successful then, its output passes through and is
not re-checked. For a writer, a later error status replaces nothing, even where a
buffer raised above the limit (the `/ws` buffer property) left those bytes
uncommitted. At the default sizes, crossing the limit fills the buffer and
commits the writer's response first. Already-committed bytes cannot be
retracted. This is the existing bounded-capture guarantee, not whole-response
buffering.

## Proven corrections

The nested-forward matrix exposed a missing `resetBuffer()`/`reset()` override in
the CSRF capture wrapper: a prefix captured by the first FORWARD survived the next
forward's reset, then was replayed ahead of the real page. The wrapper now clears
its capture only after the delegated reset succeeds; buffer-only reset preserves
headers, while full reset clears deferred length and channel state. Every CARLOS
wrapper releases its cached writer/stream after a successful full reset: retaining
a logout writer pointing at a discarded CSRF capture would otherwise lose the new
body. The sanitizer releases its own channel selection too. The regression switches
writer/stream in both directions through the complete response-transforming stack.
Rejected resets retain the captured body and state. Flush/close behavior is unchanged.

The HTML-stream matrix exposed a stale Content-Length crossing the privacy wrapper
before its notice was appended. The wrapper now defers the length until it knows
whether it will decorate; binary/non-HTML streams retain their supplied length.
Known non-HTML lengths reach the logout wrapper before stream acquisition, so an
explicit flush cannot outrun header replay. The logout wrapper also publishes
known non-HTML writer lengths before a large body can commit. HEAD/no-channel
responses preserve metadata length; integer header setters share the same rules.
Content-Type header setters also publish known non-HTML lengths. If the type
changes to HTML before commitment, the decorators remove their published lengths
through every outer wrapper and defer them again until the append decision.
Decorated HTML omits the obsolete length so the outer sanitizer or container can
frame the completed response. On an accepted `resetBuffer()` every decorator
drops a pending length and removes one it published: both described the
discarded body, and a forward always replaces the body. A rejected reset leaves
all state unchanged; `reset()` clears everything. This is stricter than the bare
container, which keeps the header. A writer
initially captured before its Content-Type
was known still uses the existing CSRF writer replay contract: the container may
choose chunked framing instead of a supplied length. Exact byte integrity remains
required. Stream notices use the response charset, or UTF-8 when the declared name
is one the JRE cannot use (Tomcat 11 keeps a rejected name visible through
`getCharacterEncoding()`). This adds header coordination, not another body buffer.

## Why retain the setting

Moving only the sanitizer's replay into FORWARD would no longer inspect outer
REQUEST filters, their final status or exceptions after the forward. Flushing
all decorators there cannot drain the outer capture safely. Nested forwards
also have outer work left to do. Consolidating capture ownership could replace
the contract, but requires a coordinated redesign of all four CARLOS wrappers
and their streaming/error semantics. No isolated flush/close or dispatcher edit
establishes that replacement. Retaining a tested context capability is the
smaller, explicit design for this release.

## Regression coverage

Run on JDK 25 in an Ubuntu 26.04 container:

```sh
mvn test -Dtest=ResponseFilterLifecycleIntegrationTest,JavaMelodyResponseIntegrationTest,ResponseSanitizationFilterUnitTest,PrivacyStatementAppendingFilterUnitTest,CsrfGuardScriptInjectionFilterUnitTest,LogoutBroadcastFilterUnitTest -Dtest.forkCount=1
```

`ResponseFilterLifecycleIntegrationTest` uses real embedded Tomcat, compiled JSPs,
real CSRFGuard and JavaMelody, and all response-transforming filters in production
order. It reads each shipped descriptor's setting independently. The matrix covers:

- Direct, forwarded and nested-forward writer/JSP/HTML-stream responses with
  exact UTF-8 payload and multibyte tail checks at 8,191/8,192/8,193/55,324 bytes
  and immediately below/at/above the sanitizer capture limit.
- Successful and safe 404/500 responses, supplied Content-Length, discarded
  pre-forward prefixes, and exactly-once applicable CSRF/logout/privacy decoration.
- Tainted 403/500 writer and stream responses below/above the capture limit,
  container 503 ERROR dispatch, and binary/static byte integrity.
- A non-HTML length declared before a forward with no channel open never frames
  the forwarded body.
- A durable negative control with Tomcat's default, minimal sanitizer isolation,
  and a JavaMelody-only passing control. The control pins that Tomcat finished the
  raw response after the forward (the mechanism), not the bytes the unsupported
  default happens to emit; it does not make that default a supported deployment.

The fixture's fixed synthetic authenticated session bypasses login. It does not
load Spring, Struts, WAF, JDBC or the entire application. Validate those gates with
the built WAR and installed Debian packages, using synthetic data and the existing
`scripts/login-playwright-checks.js` and `scripts/deb-login-playwright-checks.js`.
The packaged first-login check must exercise the forced reset through nginx/TLS;
the comprehensive login suite also checks CSRF rejection and password persistence.

The WAR's `META-INF/context.xml` controls development deployment. Packaged installs
instead use `debian/assets/tomcat/Catalina/localhost/carlos.xml`, installed in the
Tomcat configuration directory. Keep both explicit settings; testing one path is
not evidence for the other. Do not experiment by restoring the default on a live
installation.
