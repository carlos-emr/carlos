# CDS import recovery and diagnosis runbook

Use this with the [operator guide](cds-import-operator-guide.md), [ledger contract](cds-import-ledger.md) and [maintenance admission procedure](cds-maintenance-admission.md). This runbook describes supported recovery decisions. It does not authorize changing clinical rows, hashes, holds or ownership directly.

## Establish what happened

Preserve the upload ID, submission key, durable run ID, selected operator/provider and the exact source/options/artifact identities. Read the authorized status page or command result and obtain the protected event, diagnostic, unit and completion reports that are available.

Separate these observations:

| Observation | What it establishes |
| --- | --- |
| Uploaded bytes/chunks | Transfer progress, not acceptance |
| Accepted run ID | A durable job identity, not clinical completion |
| `resumeRequested` | Pending recovery intent, not exclusive ownership |
| Sealed expected inventory | The finite workset against which completeness is checked |
| Committed/prior unit counts | Progress; complete reconciliation also needs exact keys and receipts |
| Patient outcomes and holds | Which charts are complete, refused, partial or protected |
| `clinicalComplete` | Clinical execution outcome, independently of report delivery |
| `reportState` / report generation | Delivery status and authoritative report set |
| `cleanupState` | Ownership/reconciliation result, not permission to erase retained evidence |
| `maintenanceReleaseState` | Whether clinical admission was actually released |
| `cleanComplete` / `operationTerminal` | Composite operational results; pending recovery must be respected |

An unknown field, missing report or failed status request remains unknown. A stale last-confirmed value is useful evidence, but it cannot certify current completion.

## Choose the supported continuation

| Situation | Safe next step | Evidence to retain |
| --- | --- | --- |
| Transfer disconnected before acceptance | Select the owned upload and reselect the identical original file; resume its verified chunk prefix | Upload ID, original file/hash and chunk journal |
| Chunk conflict or changed local file | Resolve the source identity mismatch; use an explicitly new transfer when appropriate | Conflicting receipt and both original identities |
| Submit acknowledgment lost | Recover the same upload/submission identity and immutable options | Existing upload/submission IDs; do not create a replacement run merely because the response was lost |
| Known admission or authorization refusal before claim | Correct the authorized scope or leave the upload available for supported abandonment | Stable refusal code and unchanged upload receipt |
| `SUBMITTING` with genuinely uncertain acceptance | Use supported acceptance lookup/reconciliation | Original bytes, journal and uncertainty marker; retain until the exact outcome is proven |
| `UPLOAD_PREVIEW_SNAPSHOT_PENDING` | Reopen or resume the accepted preview and let its snapshot become durable | Preview run, actual/dry-run acceptance identities and original upload |
| Accepted run waiting for worker/ownership | Inspect the same run and its pending intent; allow durable dispatch or explicit supported Resume | Accepted handle, status and owner/control observations |
| Active owner is busy | Diagnose the actual owner; do not take over from a heartbeat timeout | Physical/process identity and safe control error |
| Known transaction-start failure | Resume the same compatible run after repairing the infrastructure | `UNIT_TRANSACTION_START_FAILED`, expected unit and earlier committed work |
| Lost commit acknowledgment | Reconcile durable checkpoint, row receipt and file references before continuing | Original run/unit keys, uncertainty code and protected evidence |
| Process, JVM or application stopped | Restore a compatible runtime/configuration and resume the same run | Accepted source, seals, checkpoints, original artifact and owner history |
| Source/native hash or target drift | Stop for explicit reconciliation; preserve subsequent clinical edits | Original and current digests, target IDs and conflict diagnostics |
| Blocking clinical meaning/constraint finding | Use versioned correction/decline review with a clinical reason | Original indexed field, original value, proposed correction and review version |
| Inactive correction draft | Complete the required corrections and whole-source revalidation | Draft lineage, diagnostics, reason and validation results |
| Source or rules changed after a proven zero-write failure | Use the documented new-submission and explicit unwritten-revision approval flow | Both accepted histories and the server's eligibility proof |
| Source/rules changed after clinical writes | Recover the original compatible run; obtain a reviewed reconciliation decision | Matching runtime/policy, original source, native receipts and holds |
| Clinical completion but reports failed | Retry supported report/release recovery without replaying clinical units | Completed clinical result and failed report-generation evidence |
| Cleanup failed or found unknown ownership | Repair and reconcile the named ownership problem; retain disputed bytes | Cleanup codes, ownership catalogue and publication/source references |
| Maintenance release pending/deferred | Use supported completed-run Resume to retry the release proof | Zero-hold proof, release state and authoritative completed run |
| Status or login expired | Sign in and reopen the exact bookmark; refresh read-only status | Last-confirmed handle and counts; no replacement submission |
| Failed worker/supervisor alert | Repair the runtime and follow its documented restart/recovery path | Safe worker code, original accepted work and supervision evidence |

## Unknown commit outcomes

The owning transaction may have committed even when its caller received an error. The source checkpoint and native receipt are the recovery evidence. A successor validates the expected work, current target and file dependencies under new exclusive ownership before skipping a unit.

Do not infer rollback from a shell failure, a broken browser connection, a missing message or an exception after flush. Do not replay the whole patient to test whether a previous attempt succeeded. Subsequent completed patients and their publications remain independent durable outcomes.

## Corrections and revised source

Clinical corrections address the exact indexed logical field and require a reason and current review version. Original source bytes remain immutable. A draft that has not passed complete XML/native preflight remains inactive and blocks execution. Warning acknowledgment is not a clinical correction.

Unwritten-revision approval is narrowly conditional. It requires server-side proof that the relevant attempts have no clinical target, checkpoint, hold, advanced work or uncertain commit. It records old/new fingerprints and audited approval. Source namespace changes and database edits cannot substitute for this procedure.

## Interpreting reports and errors

The event report explains per-patient/category outcomes. The diagnostic report carries failures, warnings and unresolved review findings. The unit report accounts for every expected native and helper unit. The completion receipt identifies its selected generation and summarizes clinical, delivery, cleanup, release and recovery state.

Each immutable completion receipt projects delivery of its own selected report generation using the fresh completion-time ledger state. A pending Resume observed in that snapshot keeps `cleanComplete=false` and `operationTerminal=false`. Requests accepted after that snapshot belong to later live status; an immutable prior receipt is historical evidence and is not rewritten. Availability of an older report does not settle a newer pending recovery request. Compare the generation identity and its recorded state with the live protected summary.

The receipt uses the base ledger summary. Live operator status also applies the existing review-work overlay: ordinary unresolved review work keeps that operator view nonterminal. A stopped attempt's receipt is therefore evidence of that attempt, rather than proof that all subsequent review or recovery work is complete.

Technical evidence provides bounded exception types, SQLState/vendor codes and code locations. It excludes raw exception messages, SQL, credentials, patient values and physical paths. Share stable safe codes with support; keep complete clinical reports in the approved protected channel.

For CLI import/resume, exit `0` requires clean operational completion or a valid completed preview under its documented contract. Exit `4` can represent refusal, partial work, pending recovery or uncertainty. A successful status/report query describes an outcome; it does not turn that outcome into a completed import.

## Storage and backup recovery

Back up and restore the database, document store, durable source/staging/upload stores and retained report generations as a coherent set. Database rows can reference source spools and native publications outside the database. A database-only restore can leave evidence missing or refer to a later filesystem generation.

Before any cleanup, establish actual ownership, reference state and acceptance outcome. Accepted source, correction evidence, prior reports, uncertain publications and resumable native work remain retained. Supported housekeeping retires only the artifacts its proofs permit. A complete cleanup result includes intentionally retained evidence.

If storage is exhausted, preserve the original source and failed/partial owned state. Repair capacity, inspect the existing run and use supported recovery. Increasing heap does not repair a full disk. Raising a reserve or clearing a hold does not establish completeness.

A table-full diagnostic (`HY000` / vendor `1114`) can indicate exhausted database datadir or tablespace capacity, including a bounded memory-backed mount. Inspect database storage separately from source/staging free space and JVM heap. Preserve the protected diagnostic, repair capacity through normal database maintenance, then reconcile and resume the same accepted run.

## Reopening ordinary clinical access

Clinical maintenance and chart holds protect different boundaries. Only the documented release proof can reopen ordinary clinical work. A later report failure does not roll back a completed chart, and a successful report does not release an unresolved hold.

After database maintenance release, each web node must finish its deferred clinical startup. Pending startup returns HTTP `503` with the documented retry guidance. Diagnose startup separately from import outcome and preserve the import ledger/source. Use the normal deployment recovery procedure for the affected node.

## Escalation record

Record the exact software/package/runtime identities, source/profile, operator scope, upload/submission/run IDs, safe failure code, report-generation identity, last-confirmed clinical/hold/release state and the protected evidence locations. Mark each fact as observed, unknown or inferred. Preserve the failed attempt before running a later recovery command.

Rehearse these paths with actual database commits, process interruption, browser failure and constrained resources before relying on them; the CLI, browser-lifecycle and resource gate guides describe how. A source review or a mocked failure test does not establish installed recovery.
