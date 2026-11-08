# Ontario remittance replay and adjustment coverage

Ticket: [#5719](https://github.com/carlos-emr/carlos/issues/5719).
Check: `billing-on-ra-replay`, Ontario core, 900-second limit.

Run through the installed HTTPS front door with `CARLOS_DISPOSABLE_HOST=true`
and `EXCLUSIVE=1`. The normal RA document directory and EDT inbox must be
available, using `RA_DOCUMENT_DIR`/`RA_EDT_INBOX` when the installation differs
from the packaged defaults. No external MOH service receives these files.

The check retains the original `billing-on-ra-import` workflow: native mixed
paid/rejected remittance upload, reconciliation/error/summary reports, rejected
GET settlement, claims-error import and reimport, native settlement and retired
route refusals. Its optional extension hooks do not change the existing check.

| Operation | Independent expected result |
| --- | --- |
| Initial mixed settlement | Only the paid claim changes from B to S. Its automatic audit timestamp falls within observed SQL time boundaries. Complete claim/item/extension/payment/allocation/transaction snapshots otherwise remain intact; rejected and control claims remain unchanged. |
| Reimport original bytes | One original header and the same details, statuses, amounts and upload bytes; no duplicate financial effect. The independently specified summary total is rebuilt correctly. |
| Replay settlement | Settled UI has no Settle action. Replay the actual captured settlement POST with fresh session/CSRF proof; native completion returns, with identical financial/header/detail snapshots. |
| Subsequent adjustment | A separate fixed-width MOH file identifies the original paid claim and provider, adds zero invoiced fee and explicitly recovers 5.00. Header/report/summary show -5.00. Combined original and adjustment amounts produce the independently calculated net payment and 5.00 outstanding. |
| Settle/reimport adjustment | One adjustment header and the same details, original paid status S and rejected status B; no repeated recovery or private-payment/allocation/transaction rows. |
| Isolation | Full unrelated control-claim snapshots and the global premium-row snapshot remain unchanged through every stage. |

`raheader.content` is a derived report XML cache and may be rebuilt on reimport;
it is checked for the independently expected summary total rather than included
in immutable clinical/financial snapshots. This does not waive differences in
source remittance details, claim amounts or payment state.

The adjustment builder's controls check raw fixed-width offsets, explicit signs,
zero new invoicing and identities, and reject invalid account/provider/code
inputs. Output parsing uses literal patterns and exact monetary strings; values
never become regular-expression source.

Fixture teardown verifies original/adjustment header ownership and every detail's
claim/provider association, retires only byte-matching owned files, and verifies
SQL/file removal before deleting the underlying claims and provider. An
unacknowledged mutating settlement request retains fixtures: session and CSRF
preflight failures are distinguished from a dispatched write without a response.
The shared browser owner drains tracked writes before cleanup.

This focused check does not prove live MOH interoperability or full release
qualification. Native results and exact package identities are recorded in the
[consolidated acceptance register](consolidated-test-ticket-acceptance.md).
