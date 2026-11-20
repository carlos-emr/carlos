# Wiki API coverage plan

This document is the plan of record for the developer-facing API documentation on the
CARLOS wiki (`github.com/carlos-emr/carlos/wiki`): what it covers, how each page is
produced and verified, how it is published, and what keeps it current as the APIs change.
It accompanies the OAuth access-mode work of PR #4461 and the first full edition of the
pages, written against release/2026.08 in October 2026.

## 1. Goal

Give an integrator or a CARLOS developer everything needed to call CARLOS programmatically
without reading the source: how to authenticate on each surface, what every endpoint and
operation accepts and returns, which calls a given access mode allows, what every refusal
means, and a tested example to start from. The pages must be methodical (complete
catalogues, not curated highlights), reproducible (generated from source where that is
possible) and verified (every example run against a packaged install).

Non-goals: a stability promise for any route (a route in source is not a public contract),
a per-field reference for every transfer object (the WSDL and the `to/model` classes are
that reference), and coverage of operational integrations (fax, billing, DrugRef), which
stay on the operations pages.

## 2. Page inventory

All pages live under *Developing CARLOS > APIs and interfaces* and carry the breadcrumb
`Home / Technical documentation / Developing CARLOS / APIs and interfaces`, an "On this page"
line, a "verified against" statement naming the release and date, and a "Related pages"
footer. Patient and provider data in examples is the fictional demonstration data set
(`FAKE-` names); keys, tokens and `securityTokenKey` values are redacted.

| Page | Purpose | Produced |
|---|---|---|
| `APIs-and-interfaces` | Section hub: pages in the section, every interface CARLOS publishes with where/who/authentication/docs, how to choose one for a task, safety rules. | Hand-written |
| `API-access-and-authentication` | The three surfaces; registering a REST client; OAuth 1.0a handshake table and signing rules (base string, nonce, timestamp skew, parameter length cap); access modes (restricted legacy default, scoped, full legacy) and the scope vocabulary table; endpoints closed in every mode; SOAP `login2` token and WS-Security header; the session API; front-door rate limits and audit rows; the complete status/reason table. | Hand-written from `OAuthScopes`, `OAuthScopeEnforcement`, `OAuthInterceptor`, `WsUtils`, `spring_ws.xml`, `debian/assets/nginx/carlos-emr.conf`, `carlos-emr.README.Debian` |
| `REST-API-reference` | All JAX-RS endpoints (223 on release/2026.08) grouped by service: method, path, scope, restricted-legacy allowed, always blocked, path/query/form/body parameters, response type and media types; the three response wrapper shapes. | **Generated** (section 3) |
| `SOAP-API-reference` | All JAX-WS services (14) and operations (79): arguments with wire names, return types, deprecation, per-service notes (inclusive vs exclusive date bounds, `useGMTTime`, lab parser mapping, loopback-only `SystemInfoService`), shared conventions. | **Generated** tables + hand-written notes |
| `API-tutorial-Python-and-curl` | One tested exchange per surface: a standard-library Python OAuth client (signing, handshake, `GET`/`PUT`), SOAP `login2` + `getDemographic` with curl, a session-cookie call, and what each refusal looks like. | Hand-written; every command executed (section 4) |

Navigation touched: `_Sidebar` (sub-list under APIs and interfaces), `Contents`,
`Developer-guide`, `Technical-reference`, `Integrations-and-services`.

## 3. Generation method

The two reference pages are produced by scripts rather than by hand so that a re-run after
a release reproduces them. The scripts are not part of the repository; they are small and
are described here precisely enough to be rewritten.

### 3.1 REST endpoint extraction

Input: `src/main/java/io/github/carlos_emr/carlos/webserv/rest/*Service.java` plus
`webserv/oauth/OAuthStatusService.java`.

1. For each class, read the class-level `@Path` (the service root).
2. For each method annotated `@GET/@POST/@PUT/@DELETE`, read the method-level `@Path`
   (may be absent), `@Produces`, `@Consumes`, `@Deprecated`, the return type, and the
   parameter list. Parse the parameter list with **balanced parentheses**, not up to the
   first `)`: annotations such as `@QueryParam("includes[]")` contain parentheses, and a
   naive split drops every parameter after the first annotation. Classify each parameter
   by its annotation (`@PathParam`, `@QueryParam`, `@FormParam`, `@HeaderParam`), treat
   an un-annotated non-`@Context` parameter as the JSON body, and record the body's type
   without its variable name.
3. Emit one record per endpoint: service, root, verb, full path, method name, return type,
   parameter lists, produces, consumes, deprecated. Release/2026.08 yields 223 records.

### 3.2 Access-mode classification

Do not re-implement the rules in the generator. Compile a small Java program against
`target/classes` and call the production code for each (verb, path):
`OAuthScopes.scopeFor(...)` for the required scope, `OAuthScopes.isAlwaysBlocked(...)` and
`OAuthScopes.isLegacyRestrictedAllowed(...)` for the two flags. Emit a TSV of
`verb, path, scope, blocked, legacyAllowed`. This guarantees the tables say what the
interceptor does, including the `#` numeric-segment wildcard and the `.json`/`.xml`
suffix handling. A blocked endpoint is rendered with `—` in the scope column.

### 3.3 SOAP operation extraction

Input: `src/main/java/io/github/carlos_emr/carlos/webserv/*Ws.java` and the
`<jaxws:endpoint implementor="#bean" address="/Service">` lines of
`src/main/resources/spring_ws.xml` (the address is the public service name; only classes
with an endpoint are published).

1. Strip `//` and `/* */` comments first; a commented-out method otherwise appears as an
   operation (this happened with `BookingWs.getScheduleTemplateCodes`).
2. Match `public <type> <name>(` and read the argument list with balanced parentheses.
3. For each argument, keep the Java type and use the `@WebParam(name=...)` value as the
   wire name when present (`lastUpdate`, `fields`, `file_name`, `oscar_provider_no`),
   otherwise the Java name; strip all other annotations.
4. Record `@Deprecated` from the annotations immediately above the method.

### 3.4 Rendering

One table per service with a fixed column set (REST: Method | Path | Scope | Legacy |
Blocked | Parameters | Response; SOAP: Operation | Arguments | Returns), a services summary
table with endpoint counts, and the hand-written prose (conventions, per-service notes,
transfer-object section, related pages) kept in the generator so prose and tables are
re-emitted together. Media types are shown only when they differ from JSON.

## 4. Verification

Every example on the tutorial and access pages must be executed before publication, on a
packaged install (the `deb-install-validation` container, Ubuntu 26.04) loaded with the
demonstration data, in the server's default access mode. The run for the first edition
covered, and any re-run must cover:

- OAuth: `initiate` (request token, `oob` callback), consent approval as the demo
  provider with the restricted-legacy notice visible, `exchange` (access token),
  `GET /ws/services/demographics/{no}` (200), `PUT /ws/services/demographics` (200),
  `GET /ws/services/oauth/info` (200), a restricted-mode refusal (`403 restricted_endpoint`
  on a schedule read), an always-blocked refusal (`403 blocked_endpoint` on `/jobs/all`),
  an unsigned call (`401 authentication_required`), and a second `exchange`
  (`401 invalid_verifier`).
- SOAP: `login2` (200 with `securityId`/`securityTokenKey`), `getDemographic` with the
  WS-Security header (200), the same without the header (400 `SecurityError` fault), with
  a wrong token (401 `SecurityError` fault), a wrong password at `login2` (generic 500
  page), `?wsdl` (200), `SystemInfoService` through the front door (404).
- Session API: `GET /ws/rs/demographics/{no}` with a `JSESSIONID` (200, epoch-millisecond
  dates) and without (401 `<error>Not authorized</error>`).
- Link check: every `[text](Page#anchor)` on the new and edited pages resolves to an
  existing page and heading.
- Forbidden-content scan: no vendor names, no third-party documentation site names, no
  session links, no real credentials, no non-`FAKE-` patient data.

Fixture hygiene: the REST client created for the run ("Wiki tutorial client") and its
request tokens, access tokens and nonces are deleted from `ServiceClient`,
`ServiceRequestToken`, `ServiceAccessToken` and `ServiceOAuthNonce` afterwards, and the
state file holding the tokens is removed.

## 5. Publishing

The wiki is its own git repository (`carlos-emr/carlos.wiki.git`). Commits are signed
off (`git commit -s`) by the author, use a conventional `docs:` subject, carry no session
links, and are pushed to `master`. A cloud session's git proxy does not hold a credential
for the wiki repository, so a change prepared there is handed over as a bundle
(`git bundle create <file> origin/master..HEAD`) or a `format-patch` file and pushed from a
workstation:

```bash
git clone https://github.com/carlos-emr/carlos.wiki.git && cd carlos.wiki
git pull /path/to/carlos-wiki-api-docs.bundle master   # or: git am /path/to/0001-*.patch
git push origin master
```

## 6. Keeping it current

The pages state the release they were verified against. They go stale on the following
events; each names the regeneration or edit it needs.

| Change in the repository | Action |
|---|---|
| A JAX-RS service or method is added, removed or re-pathed | Re-run 3.1–3.2 and 3.4; update the endpoint count in the hub and reference intro |
| A JAX-WS operation or `spring_ws.xml` endpoint changes | Re-run 3.3–3.4; revisit the per-service note if semantics changed |
| `OAuthScopes` rules change (scope vocabulary, blocked list, restricted allow-list) | Re-run 3.2; update the scope table, the blocked-endpoints section and the restricted-mode list on the access page; re-run the refusal examples |
| `OAuthScopeEnforcement` defaults or property names change | Update the access-modes section, the `carlos.properties` and README references, and the tutorial's mode remarks |
| OAuth parameter limits, timestamp skew, nonce or token lifetimes change | Update the signing and handshake sections and the status table |
| `debian/assets/nginx/carlos-emr.conf` rate-limit zones change | Update "Rate limits and audit trail" |
| Audit event names change (`OAUTH_LOGIN_*`, `WS_LOGIN_*`, `REST WS: NOT AUTHORIZED`) | Update the audit paragraph and the REST conventions |
| A new release line is cut | Re-run section 4 on a packaged install of that line; update every "verified against" statement and the `release/...` links |
| A transfer object used in the tutorial changes shape | Re-run the tutorial commands and replace the shown output |

Lightweight guard: a future CI job can diff the generated endpoint and operation lists
against the committed `docs/api-request-response-matrix.xlsx` / Postman collections and
flag drift, so a reviewer knows the wiki needs regeneration. Until that exists, the PR
template question "Does this change a `/ws` route, scope rule or SOAP operation?" is the
trigger.

## 7. Known gaps and follow-ups

- **Per-endpoint request/response examples.** The reference pages give types, not sample
  bodies. The Postman collections and the request/response matrix carry samples for the
  commonly used endpoints; extending the generator to pull one sample per endpoint from
  the matrix is the natural next step.
- **Transfer-object field tables.** `DemographicTo1`, `AppointmentTo1`, `DocumentTo1` and
  the SOAP `*Transfer` types are the ones integrations touch most; field-level tables for
  those few would remove most trips to the source.
- **Scoped-mode walkthrough.** The tutorial runs in the default restricted legacy mode.
  A second run with `oauth.scope.enforcement.enabled=true` and a scoped token (showing
  `insufficient_scope` and the scope-bearing consent page) should be added once a test
  server in that mode is part of the validation run.
- **Screenshots.** The REST Clients administration page and the consent page in each
  mode are referenced in words only; they belong in the screenshot capture queue.
- **Session API caveats.** `/ws/rs` is documented as the browser's API with the date
  format difference called out; it is not inventoried separately because its paths are the
  same as `/ws/services`. If it ever diverges, split the table.
- **FHIR.** The hub states that no FHIR server endpoint is published; if one is added,
  it needs its own page and a row in the interfaces table.
- **Commit history hygiene.** Older wiki or code commits may name third-party vendors in
  their messages; the pages themselves do not, and new commits must not.
