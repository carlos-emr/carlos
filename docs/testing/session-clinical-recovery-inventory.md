# Session loss with populated clinical editors

Target: `release/2026.08`; source request [#5802](https://github.com/carlos-emr/carlos/issues/5802).
Run `npm run test:session-clinical-recovery-playwright` with
`CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1` against the installed application.
The workflow creates two owned provider logins and retires their application
caches by restarting the disposable application after account/clinical cleanup.
This check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).

## Native workflow and contract

1. Native Save establishes an unsigned clinical note baseline and an owned eForm
   instance. The baseline login finishes before a fresh owned editor login opens.
   A distinguishable private note draft D1 must receive an actual autosave
   acknowledgement and be stored exactly once. The eForm is reopened from its
   actual saved-instance list. This representative eForm has no automatic draft
   store; its saved value and changed input are inspected separately.
2. Supervise every autosave from that owned editor before typing N2. Input is
   filled atomically, and every outgoing request is held before dispatch. A
   preceding request may still contain exactly D1: only that real request may be
   forwarded while the session is live, with its actual acknowledgement and exact
   D1 storage independently verified. No partially typed content is treated as
   pending if it already reached durable storage. The complete N2 request must
   then be held while the live editor visibly reports pending autosave state.
3. Change the saved eForm to E2 and click its actual toolbar Save. Capture the
   genuine `/eform/addEForm` request and bind it to the actual reopened instance,
   patient/template/provider action parameters and exact changed field. The
   viewer's server-added identity elements have IDs, rather than submitted names;
   the test does not invent body parameters that native Save does not send.
4. A separate browser context already holding the same old cookie clicks the
   application's Logout button. Its browser storage/broadcast is separate.
   An independent heartbeat read must establish that the original session is
   invalid before either held pending clinical request is released.
5. Forward each unchanged native request once, with redirects and retries disabled.
   Require the actual application-origin authentication refusal or exact local
   login destination, reject WAF blocks and server faults, and compare complete
   saved note/draft/eForm/value snapshots. No N2/E2 marker may occur in clinical
   storage under any patient/account. The note must show failed autosave with its
   pending text intact. The eForm must visibly show a login/refusal, never a save
   success. This release may render its generic `CARLOS Error: 403` page; the
   check records that actual contract instead of claiming a friendly login page.
6. Resume native autosave retries against the real expired session, then release
   the genuine browser heartbeat. Every captured heartbeat must report invalid.
   The old chart must close and its schedule must reach login. Exact known
   authentication responses are accounted for; unrelated console, HTTP and
   transport failures remain fatal. A browser abort is explainable only by its
   exact acknowledged request and its issuing document's proven departure.
7. Reauthenticate as the same owned user in the original editor browser context.
   Reopen the chart and eForm through native links: recover exactly D1 and the
   saved eForm baseline, retain one private draft and the exact saved clinical
   histories, and retain no unacknowledged N2/E2 or duplicate saved instance.
8. Sign out and sign in as the other owned user in that same browser context.
   Its usable chart, every remaining page, session storage, local storage and
   IndexedDB must contain no previous user's private D1/N2/E2. Its own draft set
   must stay empty, while the first user's exact acknowledged D1 remains intact.
   Shared saved baseline records remain available through their normal views.

The unrelated control patient's complete clinical state, including note and form
history, is independently preserved. No unauthenticated storage, new recovery
feature or retention policy is introduced. The existing heartbeat/refusal check
remains registered and unchanged.

## Request supervision and cleanup

`held-clinical-requests.js` holds real UI requests, not synthetic successful
responses. A private `0700` directory and `0600` journal record the owned marker,
request kind/state/method/path, body hash and known status; no credentials,
headers or clinical bodies are stored. A cancelled UI request is never forwarded
after release. An unacknowledged dispatched write retains the journal and all
browser-owned fixtures for reconciliation. Every upstream operation is drained
before ordinary workflow cleanup. After the proved expiry boundary drains,
supervision resumes without holds for the genuinely authenticated recovery phase. Known pre-dispatch cancellation can be retired
only after browser teardown; journal cleanup itself is checked.

Native provider-profile saves invalidate the owned account caches. Clinical rows,
drafts, locks, template/instances/values, owned provider archives/programs and
extra patients are removed by verified ownership after browser shutdown. Saved
eForm IDs avoid legacy dangling attachment references. The post-cleanup restart
retires deleted providers from application caches. Independent package/config,
SQL and unrelated-state audits remain required on success and failure.

## Qualification

Both final installed suites pass in strict-release mode, including all six
workflow stages and cleanup: ON 227.6 seconds; BC 220.8 seconds. No failure,
skip or missing structured PASS is accepted. Patient identity and all saved,
draft and pending values are excluded from raw/visible unauthenticated responses
and login/error pages. Both independent final audits preserve original package/
configuration hashes, health, zero owned clinical/account fixtures and all 184
preexisting email-log column digests. The exact source digests remain unchanged.

The complete Ubuntu Node run passes 7,645 cases in 543 files without failures,
skips, cancellation or TODO. Focused real-HTTP/ownership/journal/cancellation,
phase-handoff and authentication-feedback controls also pass in Ubuntu 26.04.
Earlier attempts found harness races and incorrect assumptions about native
routes, submitted identity fields, generic error feedback and autosaves during
typing; those attempts remain unqualified. Two conservative parent/journal
receipts retained by failed attempts were reconciled only after proving every
request was aborted before dispatch and every owned clinical child was already
absent. The exact parents and journals were then retired. No recovery journal
remains in either installed container. Final package identity and original full/
repeated qualification obligations remain in the consolidated acceptance register.
