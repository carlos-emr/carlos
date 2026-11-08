# OAuth web callbacks

`oauth-web-callback` implements #5806 while retaining the existing out-of-band
and token-lifecycle checks. SQL registers one marker-owned client because the
administration UI does not disclose the signing secret. A real ephemeral HTTP
receiver binds only to loopback in the deployed browser's namespace. It records
the actual browser request in memory and returns fixed, credential-free HTML.

Both the exact registered callback and its allowed query extension complete
native provider consent, HTTP 303 browser redirect, exact request-token/verifier
capture, single-use exchange and signed provider/patient reads. Original query
data must survive; the redirect must carry no token secret or unexpected field.
Stored scopes/provider and access outside the requested scopes must agree with
`EXPECT_OAUTH_MODE` (`scoped`, `legacy-restricted`, or `legacy-full`). The earlier
tokens are tested again after all negative cases as independent live controls.

Native Deny has this release's explicit outcome: HTTP 403 `authorization_denied`,
visible in the browser, no callback, and no verifier/provider attribution. The
request remains pending; denial does not permanently revoke it. An unapproved
exchange must return `invalid_verifier` and issue nothing. Different hosts,
non-HTTP schemes and lookalike registered paths must be refused by the application
before any request/access token or redirect is issued. Only the precisely asserted
native denial response is acknowledged in strict browser diagnostics.

The encoded-query extension `context=owned%20callback%20%26%20value` currently
fails at initiation with HTTP 500 on both installed provinces (#5823). Its later
authorization/exchange stages are explicitly unreached; this remains a fatal
comparison. The independent exact/plain-query, Deny and unauthorized-callback
cases continue. The signing helper is pinned to published RFC 5849 vectors,
independently of the application verifier.

Cleanup claims a client namespace only after confirming it is empty, registers
intent before its INSERT, and recovers a lost acknowledgment by exact key/name.
Existing collisions cannot be adopted. The shared client finalizer verifies
ownership and removes only owned clients, tokens and consumed nonces. The browser
is drained/closed before fixture cleanup; receiver shutdown waits for pending
binding, stops acceptance before retiring connections, and preserves errors.
Real-socket controls cover response confidentiality, interrupted binding,
malformed incoming requests, resource retirement and namespace collision.
Diagnostics retain failure attribution while stripping URL queries and known
OAuth credentials. No secret, token or verifier is an artifact or receiver output.
