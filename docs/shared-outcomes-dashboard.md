# Shared Outcomes Dashboard (launch and metric refresh)

Schedule ▸ Dashboard ▸ Common Dashboard opens the external Shared Outcomes dashboard in a popup
and lets it ask CARLOS to publish a provider's indicator results. This page is the contract of
that integration. It records the disposition of issue #5335 (a launcher URL that did not match its
Struts action) and the defects that repairing it exposed.

## Flow

1. `GET /web/dashboard/display/sharedOutcomesDashboard` (`ViewSharedOutcomesDashboard2Action`,
   `_dashboardDisplay` read, GET/HEAD only) renders the launcher page. It opens the dashboard
   (`DashboardManager.getSharedOutcomesDashboardLaunchURL`: an encrypted provider/clinic payload)
   in a popup and sends `CONNECT` to it every 500 ms until the dashboard answers `CONNECTED`.
2. The dashboard asks for a refresh with `window.opener.postMessage({outcomes: {interaction:
   "submitQuery", request: {username, queryList: [...]}, onSuccess}}, ...)`.
3. The launcher posts `data=<base64 of {"username","queryList"}>` to
   **`POST /web/dashboard/OutcomesDashboard`** (`OutcomesDashboard2Action`).
4. The action resolves each shared metric-set name to an indicator template, computes the provider's
   indicator panel and publishes one metric set per match to `shared_outcomes_dashboard_send_url`
   (`OutcomesDashboardUtils.sendProviderIndicatorData`, form fields `version=1.1` and `data`).
5. On success the launcher relays the dashboard's `onSuccess` message back to it; on failure it does
   not, and logs the HTTP status to the browser console.

## Configuration (`carlos.properties`)

| Property | Use |
|---|---|
| `shared_outcomes_dashboard_url`, `_host` | Popup address; `_host` is also the only origin the launcher accepts messages from and posts to |
| `shared_outcomes_dashboard_key`, `_salt` | Encryption of the launch payload (`_salt`: 32 hex characters) |
| `shared_outcomes_dashboard_clinic_id` | Clinic identity published with each metric set (at most 42 characters; the clinic name when blank) |
| `shared_outcomes_dashboard_send_url` | Destination of the metric sets. Unset: nothing is published and the refresh answers 502 |

## The refresh route

* Requires `_dashboardDisplay` read. Reading the indicator library additionally needs
  `_dashboardManager` write and computing the panel `_dashboardDrilldown` read (both enforced by
  `DashboardManager`); a user without them cannot refresh.
* **POST only.** Each metric set is an outbound HTTP POST, so any other method answers 405 (`Allow:
  POST`) before any work. The `method` query parameter is ignored: there is no method dispatch.
* CSRF: the launcher posts with jQuery, so CSRFGuard's XHR hook adds the token header.
* Responses are JSON and the action returns `NONE`:

| Status | Body | Meaning |
|---|---|---|
| 200 | `{"published": n}` | Every matched metric set was accepted; a requested name no template matches is logged and skipped (`published` can be 0) |
| 400 | `{"error":"invalid_request"}` | `data` missing, not Base64 JSON, or not `{username: text, queryList: array}` |
| 403 | `{"error":"forbidden"}` | The manager withheld the indicator library (`_dashboardManager` write missing) |
| 502 | `{"error":"publish_failed"}` | A matched set was not delivered: destination unset or unreachable, non-2xx answer, unknown provider, or no panel for the caller |
| 403/405 page | security error / `Allow: POST` | Missing `_dashboardDisplay`; wrong method |

Published metric data comes from `IndicatorBean.originalJsonPlots`, which `IndicatorQueryHandler.createOriginalGraphPlots`
serializes as strict JSON (`{"results":[{"Active":2},{"Not active":1}]}`, one object per result column, valid for
any number of rows). It was hand-built, single-quoted and comma-less between rows, so the first publication ever
made through the repaired route would have failed to parse. A destination that redirects is treated as a
failure (the HTTP client does not follow redirects, so a login page answering 200 is never mistaken for
acceptance). The launcher sends `data` as UTF-8 JSON in Base64, form-encoded by jQuery (a `+` in Base64 and
non-ASCII metric names both survive). The action requires a single `data` parameter with one JSON object, unique keys and text metric names. The publisher uses UTF-8 form encoding, makes one delivery attempt per match and logs the HTTP status.

## Cross-Origin-Opener-Policy exception

Every CARLOS response carries `Cross-Origin-Opener-Policy: same-origin`
(`CrossOriginOpenerPolicyFilter`, issue #4513). Under that policy a browser severs a **cross-origin**
popup from its opener (`window.opener` is `null` in the popup and the launcher's reference is
`closed`), so `CONNECT` and every refresh message are undeliverable regardless of the route. The
launcher page alone is therefore served with `same-origin-allow-popups`, which keeps the relationship
with the popups it opens while still isolating the page from any cross-origin window that opens it.
The exception is the exact path `/web/dashboard/display/sharedOutcomesDashboard` (taken from the
user's request, so the action-to-JSP forward and error pages keep it); any other spelling falls
back to `same-origin`. Launching the popup from the schedule therefore starts a new browsing context
group (the schedule keeps no reference to the launcher; nothing used one).

The launcher accepts `message` events from its dashboard popup at the configured origin and replies to that origin.

## Verification

* Unit: `OutcomesDashboard2ActionUnitTest`, `OutcomesDashboardUtilsUnitTest` (real loopback HTTP
  destination), `CrossOriginOpenerPolicyFilterUnitTest`, `MutatorActionGetRejectionContractUnitTest`.
* Browser (manual, extended tier, disposable install with the synthetic properties documented in the
  script): `node scripts/shared-outcomes-launch-playwright-checks.js` (`npm run
  test:shared-outcomes-launch-playwright`). It stubs the dashboard origin, listens on
  `127.0.0.1:${SHARED_OUTCOMES_SEND_PORT:-18335}` as the send URL, and checks the opener channel, the
  published payload, a rejected delivery (502, not reported as done), a foreign-origin message, GET and
  token-less POST refusal and the absence of the former `/OutcomesDashboard` URL.
* `bash scripts/validate_struts_actions.sh` after any mapping change.

## Disposition of #5335

`/web/dashboard/OutcomesDashboard`: **kept; the caller was repaired** (`/OutcomesDashboard` was never
mapped and stays absent, with no compatibility alias). Retained dependencies:
`OutcomesDashboard2Action`, `OutcomesDashboardUtils`, `integration/dashboard/model/*`,
`sharedOutcomesDashboard.jsp` and `ViewSharedOutcomesDashboard2Action`. Not changed: which provider's
indicators a request may name (the request's `username` is taken as sent, as before) and which
templates are searched (the whole library, not only those flagged shared); both are noted for
follow-up rather than altered inside a routing repair.
