# CDS import final review and refactor decisions

Target: `release/2026.08`. Scope: the complete import system, its existing clinical integrations and its coordinated CLI/container entry points. Export remains outside this delivery. See the [documentation index](cds-import.md), [architecture](cds-import-architecture.md) and [release verification contract](cds-import-release-verification.md).

## Review basis

Independent reviews compared the full implementation with the Ontario billing architecture, project layer naming, actual CARLOS DAO/model behavior, the pinned OntarioMD contracts and the import's transaction/recovery requirements. Review traces covered HTTP actions, assemblers, view records, JSP/JavaScript, application services, category preparation/writers, underlying clinical readers, physical JDBC controls and the installed command boundary.

Failure traces followed a large attachment, partial-date prevention, historical prescription and interrupted unit through preparation, publication, native commit, receipt/checkpoint reconciliation, reporting and recovery. Separate DAO reviews examined actual callbacks, generated identifiers, caches, transaction propagation and external side effects before choosing composition or a narrow participating extension.

The conclusion is that a wholesale importer or DAO rewrite is not justified. The shared engine, bounded preparation, fenced unit ownership and existing-model composition should remain. Source review identifies residual changes; executed final-candidate gates remain the release acceptance authority.

## Required residual changes

| ID | Finding | Focused correction | Required regression |
| --- | --- | --- | --- |
| R1 | The submission action claims a completed upload before the application boundary checks admission. A known refusal can leave false `SUBMITTING` uncertainty, retaining bytes/quota without a job. | Move typed upload submission coordination into an application service. Resolve the owned source without mutation; leave durable claim and acknowledgment solely inside the locked job-service acceptance path. | Disabled admission and known authorization/lock refusals preserve an abandonable completed upload; success, concurrent/conflicting options, lost acknowledgment, preview-to-import and accepted retired retries preserve exact identity. |
| R2 | Report generation can overwrite terminality while the fresh summary already contains a pending Resume. | Project selected report delivery using the fresh base-ledger pending-intent and final-state predicates. Preserve normal completed and valid-preview behavior. | Deterministically accept Resume before the completion snapshot; require that receipt and live ledger summary to remain nonterminal for the observed pending recovery. Check ordinary completion and report/cleanup/release failures. |
| R3 | The import view assembler accepts an unused servlet request. | Remove the parameter/import and update direct callers/fixtures. | Existing render/authorization/route tests retain the same view data. |

These corrections are narrow application/presentation/reporting changes. They do not justify generic resetting of uncertain uploads, replaying clinical units, weakening strict SQL or removing ownership checks.

All three corrections are implemented and independently reviewed against their exact source hashes. R1/R3 passed 70 focused tests across five existing/new submission, upload, action, contract and job-service families; all ten owned Java files passed Checkstyle. R2 passed 89 focused tests across four receipt/control families, including deterministic Resume interleaving, fourteen outcome combinations and fresh preview-state coverage; both owned Java files passed Checkstyle. These are focused verification results, not final-candidate database, installed or resource acceptance.

R2 is a point-in-time receipt correction. Controls accepted after the completion snapshot belong to later live status; prior immutable receipts are not rewritten. The existing operator facade's additional pending-review overlay remains authoritative for current review action.

## Design decisions retained

- Prototype actions with native HTTP/version/security gates; internal JSPs render encoded immutable models.
- Service-owned acceptance and clinical transactions, with a durable database work authority and a bounded in-process worker queue.
- Bounded source parsing/spooling/preparation outside clinical write transactions.
- Native data, precision metadata, references, receipt, strict event and checkpoint in the same fenced unit.
- Recovery from exact sealed expected work and current physical receipts; unknown acknowledgment is not treated as rollback.
- Existing participating CARLOS DAOs and narrowly extended APIs where ordinary managers would change historical meaning or emit unsuitable side effects.
- Fresh bounded identity/program projections where generic APIs introduce cached authorization, eager graphs or incomplete identity predicates.
- Narrow physical JDBC ownership, metadata pinning and bounded native-row hashing.
- Retention of accepted source, uncertain publications, correction history, prior reports and resumable state.

## Verification and documentation sequence

1. Implement R1 and R2 with deterministic regressions; apply R3 with its caller/test updates.
2. Independently review the delta for single acceptance ownership, pending recovery truth, DAO/model semantics, dependency direction and minimal CLI wiring.
3. Keep the fixed modern operational timestamp in the projection fixture; retain the earlier strict-MariaDB failure record. Production timestamp handling and strict constraints remain unchanged.
4. Publish bounded complete WAR-entry evidence in resource CI so installed/candidate parity is based on actual bytes, rather than source IDs or counts alone. Keep the manifest tests permanently in CI.
5. Freeze the signed source and the explicit required suite manifest, retaining every previously required suite and all new affected suites.
6. Run fresh compilation, affected regressions, all required native families and current-head CI. Native verification includes strict session behavior, DAO wiring/projections and every-target metadata-lock protection.
7. Build and bind the candidate WAR, source/class/dependency/migration/resource evidence, runtime identity and service UIDs.
8. Execute the installed browser/CLI, retained recovery, large ingress, session/proxy/tab interruption, synthetic-course, historical compatibility and constrained-resource gates against the bound candidate. Keep all five large-resource CI profiles and their unchanged budgets.
9. Resolve valid PR findings and record completed, failed and pending gates with exact identities in the [executed verification record](https://github.com/carlos-emr/carlos/pull/5347#issuecomment-6451727760).

This document records the implemented review corrections and the required completion sequence. It does not turn an unexecuted gate into a pass or guarantee the absence of defects. Retain candidate-specific execution results and any later finding dispositions in the verification record.
