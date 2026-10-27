# OSCAR 19 OAuth compatibility and token-preserving migration

CARLOS preserves established OSCAR 19 OAuth 1.0a authorizations when a clinic
migrates. Integrations retain their consumer and access-token credentials and
do not repeat consent merely because the server changed. Lifetime configuration
is the O19 duration, not an input to a new minimum/maximum policy.

## Source baseline

Compared with `oscaremr/oscar` production `stable` at
`b425be066fa17f7dff0a4e0ee58e41bf89b83d81` (retrieved 2026-10-27):

- `src/main/java/oscar/login/OscarOAuthDataProvider.java` persists
  `ServiceClient.lifetime` and reconstructs access tokens from their stored
  issuance time and lifetime. Its immediate issuance object still says 3600;
  subsequent authorization uses the stored value. CARLOS uses one duration for
  both representations.
- `database/mysql/updates/update-2019-06-13.sql` adds configurable client
  lifetimes and initializes existing clients to -1.
- `src/main/webapp/admin/api/clients.jsp` has a blank text field for the lifetime
  in seconds, with Add Client/Cancel and client deletion. `clientManage.json`
  stores an integer, or zero when the input is blank/unparseable.
- The reproducible migration baseline, `a7900d569d3faf741993e5e1da8c14021bbefede`,
  predates that column and issues persisted access tokens with lifetime -1.
  The importer therefore supplies -1 only when the source **column is absent**.
  A present column, including a NULL or zero value, is copied unchanged.

Historical references: O19 configurable-lifetime change
`f7d5d8fae0b7e9a10a363eda2cf91bcd979ffae0`; the subsequent rewrite at
`c7c948761915ea91d32940eb115b2521c6d3457a` replaced the stored client setting
with 3600. This work restores configurability and existing-grant continuity.

## Lifetime contract

| Stored value | Behavior |
|---|---|
| Positive seconds | Exact duration, without a floor or automatic cap |
| -1 | Non-expiring; the token must still pass authentication and authorization |
| Zero | Not a default: a finite zero-duration grant, expired after its issuance second |
| NULL client setting | New issuance returns `invalid_token_lifetime`; no invented duration |
| Other negative values | Invalid; never interpreted as non-expiring |

The finite-token boundary follows the O19 CXF validator: expired when
`now > issued + lifetime`, using epoch seconds. Invalid issuance metadata and
arithmetic overflow fail closed. No wall-clock reset, seconds/milliseconds
conversion, or date/timezone normalization is applied during migration.

Client configuration affects newly issued tokens. It does not shorten, renew or
extend a previously issued token. Removing a token or its client still prevents
its use. Token signatures remain bound to the original consumer, and replay and
signature protections remain enabled. Unknown-token errors do not echo token
identifiers into logs.

## Administration and consent

The lifetime remains the existing blank text input measured in seconds. The
registration handler retains O19's integer/blank input behavior. No presets,
mandatory cap, new default, editing controls or new consent choices are added.
The existing Add Client/Cancel, deletion and consent workflow remain in place.

CARLOS's existing credential redaction is intentionally retained: list responses
and the UI do not expose client secrets, token identifiers or token secrets.
The database copy does not require displaying credentials. Existing admin
privilege checks, safe rendering and POST/CSRF protections are retained.

## Import implementation

The matching CLI implementation is [carlos-ctl PR #16](https://github.com/carlos-emr/carlos-ctl/pull/16).
The application pins its commit for CI; include it in the coordinated 1.2.0
CLI release before shipping these manifests.

`ServiceAccessToken` is a copy-class table. Preserve its original ID, client ID,
token ID, secret, issuance time, lifetime, provider, scopes and creation date.
`ServiceClient` credentials and configured lifetime are preserved too. The
manifest includes the known optional lifetime column even though the old pinned
source tree lacks it; the CLI resolves it against the actual staged schema.

`SecurityToken` and unfinished `ServiceRequestToken` handshakes remain archived.
Expired access-token rows may be copied for parity, but remain expired; the
importer does not issue replacement tokens. Revoked grants absent from the final
source snapshot are not reconstructed from older archives.

Access tokens participate in the existing `carry-credentials` acknowledgement.
Reports contain counts, not credential values. Preflight refuses identifiers
ambiguous under the target collation and reports malformed grants. The manifest
version changes so a partially completed import cannot resume under different
classification rules. Generate all manifests and the standalone preflight from
one matched source set; do not hand-edit generated data.

## Integration and cutover checks

1. Inventory the actual clinic's integration URLs, HTTP methods, callbacks,
   scopes/permissions and read/write endpoints before the final migration.
2. Test each required endpoint against CARLOS. Its legacy access policy may
   restrict endpoints that O19 allowed. Select a reviewed, supported configuration
   for that clinic; migration does not silently switch every client to full
   access or bypass the always-blocked endpoints.
3. Preserve the public hostname/context where practical, and verify the actual
   proxy path. OAuth signatures include the URL; a redirect is not proof of
   signature compatibility.
4. Compare response fields, identifiers, contacts, date-only values, timestamps,
   nulls and collections using the integration's O19 fixtures. This lifetime fix
   does not change REST DTOs or date serializers and is not a claim that every
   endpoint in every vendor fork is already compatible.
5. Freeze source writes and authorization/revocation changes before the final
   consistent export. An earlier rehearsal dump could resurrect a grant revoked
   after that dump. Keep the target isolated until verification completes.
6. Complete row/content/relationship parity before auth checks that may remove
   expired token rows. Use original credentials with fresh timestamps/nonces;
   do not replay a stale recorded signed request or repeat the OAuth handshake.
7. Verify finite and non-expiring tokens, refusals, application restart and the
   actual required integration calls. Do not accept writes on both systems.

Before target clinical writes, rollback may return routing to the frozen source.
After target writes, reconcile clinical data, new grants and revocations before
rollback; restoring the old snapshot alone can lose data or revive revoked
access. Recovering tokens from an older completed migration is a separate,
reviewed reconciliation, not a blind re-run of the importer on a live database.

## Verification and review

- Java tests pin exact lifetime issuance, O19 expiry boundaries, original token
  metadata, consumer binding, forged signatures and nonce replay. Admin tests
  pin the existing input behavior and credential redaction.
- MariaDB tests run the actual manifest-driven table copier and resumable ledger,
  comparing original credentials, associations, scopes and expiry fields; older
  schemas and target-collation ambiguity are covered.
- Generator integrity tests cover both provincial profiles. Manifest generation
  is checked for drift. The CLI tests exercise preflight, mapping and resume
  behavior against the same manifests.
- Review 1: check source semantics, both schema generations and unchanged UI
  choices. Review 2: check secret handling, invalid grants, consumer ownership
  and replay protection. Review 3: check package matching, manifest/resume guards,
  cutover snapshots and rollback after writes.

A live clinic cutover still requires the per-integration rehearsal above. Unit
and disposable-database tests are not a substitute for that deployment check.

### Validation record (2026-10-27)

- JDK 25: 270 targeted OAuth, administration, consent, REST date and demographic
  endpoint tests passed, without failures, errors or skips.
- Generator/integrity suite: 253 passed; regeneration with `--check` passed.
- CLI suite with application manifests: 1,606 tests, no failures/errors, 37 skips
  for optional environment-dependent checks. Standalone snapshots: 1,606 tests,
  no failures/errors, 84 optional/checkout-dependent skips.
- Real MariaDB 11.8.8: 13 copy/resume/OAuth tests passed without skips.
- OAuth signer Appendix A regression and 11 packaging/pin checks passed.
- The revised lifetime tests first failed against the original PR (17 assertion
  failures). The missing-column and credential-acknowledgement importer cases
  also failed before the fixes.

Three review passes completed: source/UI compatibility; credential ownership and
failure handling; and import/package/cutover recovery. A full deployed clinic
migration and integration-specific live rehearsal were not performed.
