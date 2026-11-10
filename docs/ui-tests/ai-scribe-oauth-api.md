# AI-Scribe OAuth API live check

`scripts/ai-scribe-oauth-api-live-check.js` exercises the deployed OAuth 1.0a
workflow and database-backed API calls that AI-Scribe needs. It complements
unit and local-transport tests; those do not prove that OAuth consent, signed
requests, deployed routing, scope enforcement, and persisted writes work
together.

## What it covers

- Out-of-band OAuth request-token, browser consent, access-token exchange, and a
  signed token-identity request.
- Scoped OAuth demographic read, update, and database-backed read-after-write.
- Scoped measurement read using an owned synthetic BP measurement.
- eForm template listing, create, load, update, and persisted read-after-write.
- `insufficient_scope` refusals for demographic and eForm operations when the
  token lacks the corresponding scope.
- The current REST measurement-write gap: a suitably scoped `PUT` is expected
  to return HTTP 405.

The eForm endpoints manage templates, not completed patient eForm instances.
Measurement REST currently supports reads only; adding measurements is a
separate SOAP/WS-Security API. Both limited-scope and fully scoped REST
measurement `PUT` probes return HTTP 405 because the route does not exist; the
limited-scope probe therefore does not demonstrate `measurement.write` scope
enforcement. The check makes these boundaries explicit rather than treating
them as implemented OAuth workflows.

## Prerequisites and execution

Run only against a **separate, disposable Ontario CARLOS EMR installation**
with synthetic data. Enable `oauth.scope.enforcement.enabled=true` for that
installation, restart it, and do not change the mode of a shared installation.
The OAuth mode setting is server-wide.

Set `BASE_URL` if the installation is not at
`http://127.0.0.1:8080/carlos`. The login defaults to the devcontainer test
account (`carlosdoc` / `carlos2026`, PIN `2026`); override with `TEST_USER`,
`TEST_PASSWORD`, and `TEST_PIN` as appropriate. Set `MYSQL_PASSWORD`; optional
database connection overrides are `MYSQL_HOST`, `MYSQL_USER`, and
`MYSQL_DATABASE`. Database writes are restricted to a loopback host by default.
Do not set `ALLOW_NON_LOCAL_MYSQL_HOST=true` except for a deliberately
isolated, disposable database.

From the repository root, run:

```sh
EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
```

`EXPECT_OAUTH_MODE` is an assertion, not a server configuration mechanism.
The script refuses a non-scoped expectation, and the OAuth consent page must
not show a legacy-mode warning. A successful run prints an endpoint-by-endpoint
table with the HTTP status, persisted read-after-write checks, scope refusals,
and explicit `GAP` rows for the unsupported measurement writes.

The check creates one uniquely marked synthetic demographic and BP
measurement, one eForm template, and one temporary OAuth client with issued
tokens/nonces. It removes those owned fixtures in cleanup even when an
assertion fails. Run it serially and never against a shared or production
database.

## API capability follow-up

If AI-Scribe requires writing measurements through OAuth REST, CARLOS needs a
REST operation and matching `measurement.write` scope/privilege implementation;
the live check currently proves that the route is not available. If AI-Scribe
needs completed eForm instances rather than reusable templates, that workflow
also needs a distinct API contract and test coverage.
