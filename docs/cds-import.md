# CDS import documentation

This is the entry point for the CDS import system targeting `release/2026.08`. Browser imports and the paired `carlos-ctl` command use the same CARLOS engine, clinical models and durable recovery contracts. Import admission is disabled by default. Export is outside this delivery.

These documents explain the implementation and operating contracts. Release acceptance additionally requires the executed evidence for the final candidate, recorded on the release pull request.

## Start by role

| Reader | Read first | Then use |
| --- | --- | --- |
| Import operator | [Operator guide](cds-import-operator-guide.md) | [Web workflow](cds-import-web.md), [recovery runbook](cds-import-recovery-runbook.md) |
| Clinical reconciler | [Clinical meaning and native writes](cds-clinical-import-contract.md) | [Prevention dates](cds-prevention-date-contract.md), [legacy reader safeguards](cds-legacy-projection-contract.md), protected per-run reports |
| Administrator | [Maintenance admission](cds-maintenance-admission.md) | Operator guide, recovery runbook, [installed compatibility](cds-import-installed-compatibility.md) |
| Developer or reviewer | [Architecture and class responsibilities](cds-import-architecture.md) | [Ledger and transactions](cds-import-ledger.md), clinical contract |
| Release verifier | [Resource verification](cds-import-performance.md) | [CLI gate](cds-import-cli-verification.md), [browser lifecycle gate](cds-import-lifecycle-verification.md), [WAF policy and gate](cds-import-waf-verification.md), [installed compatibility](cds-import-installed-compatibility.md) |

## Documentation map

| Subject | Authoritative document |
| --- | --- |
| Architecture, MVC, dependency direction, class map, lifecycle and persistence choices | [Architecture](cds-import-architecture.md) |
| Uploading, progress, bookmarkable jobs, source views, review and protected downloads | [Web workflow](cds-import-web.md) |
| Deployment preparation, rehearsal, reconciliation and operator exit meanings | [Operator guide](cds-import-operator-guide.md) |
| Server defaults, source registration, delegation and capacities | [Configuration reference](cds-import-configuration.md) |
| Failure diagnosis, safe continuation, unknown outcomes and retained evidence | [Recovery runbook](cds-import-recovery-runbook.md) |
| Ownership, epochs, seals, checkpoints, review publication and finalization | [Ledger contract](cds-import-ledger.md) |
| Writer exclusion, physical connection guard, startup and maintenance release | [Maintenance admission](cds-maintenance-admission.md) |
| Source normalization, existing DAO composition and historical clinical semantics | [Clinical contract](cds-clinical-import-contract.md) |
| Unknown and partial prevention dates, storage anchors and downstream meaning | [Prevention date contract](cds-prevention-date-contract.md) |
| REST/SOAP, legacy readers and refusal of unsafe projections | [Legacy projection contract](cds-legacy-projection-contract.md) |
| Field, choice and compositor coverage | [Specification crosswalk](cds-spec-crosswalk.md), [machine-readable crosswalk](cds-spec-crosswalk.json), [review pins](cds-spec-review.json) |
| Physical JDBC timeout and cancellation behavior | [JDBC timeout verification](cds-import-jdbc-timeouts.md) |
| Actual host package, delegation, interruption and command compatibility | [Installed CLI verification](cds-import-cli-verification.md) |
| Container replacement and resource isolation | [Container recovery driver](cds-container-recovery-driver.md) |
| Genuine old/new browser, API, runtime and schema compatibility | [Installed compatibility](cds-import-installed-compatibility.md) |
| Large inputs, heap/RSS, SQL counts, disk reserve and actual ENOSPC | [Resource verification](cds-import-performance.md) |
| Session expiry, proxy failure, tab closure and same-run recovery | [Browser lifecycle verification](cds-import-lifecycle-verification.md) |
| Actual Nginx/ModSecurity body and route policy | [WAF verification](cds-import-waf-verification.md) |

## What completion means

Transfer, durable acceptance, clinical completion, report delivery, cleanup and maintenance release are separate facts. A successful HTTP request or a displayed upload percentage does not establish import completion. A complete clinical count does not prove reports were delivered. A status command can succeed while the import it describes remains incomplete.

Use the durable run ID and all four protected report kinds. Reconcile source patients, destination copies, clinical occurrences, helper units, committed units, verified prior work and unfinished work. Preserve unknown totals until discovery and sealing prove them. The operator guide and ledger contract define these terms and the supported controls.

Source values that require review remain attributable and visible. Original XML, precision, restrictions and provenance are not replaced by a convenient native default. Acknowledging a message does not resolve a blocking clinical interpretation.

## Deployment companions

The CARLOS application owns the shared engine, web workflow, migrations and clinical behavior. [carlos-ctl](https://github.com/carlos-emr/carlos-ctl/pull/15) supplies the verified host/container command and delegation boundary. [carlos-podman](https://github.com/carlos-emr/carlos-podman/pull/22) supplies the coordinated container integration. Compatible packages, runtime classes, configuration and implementation fingerprints must be verified together. Publishing a companion release asset follows the normal maintainer release process.

## Keeping these documents accurate

Update the relevant contract when changing a public route, command, configuration property, DTO field, state transition, DAO operation, native representation, resource bound or recovery decision. Update this index when adding a document. Check relative links and examples against the implementation.

Keep executed candidate-specific results on the release pull request with their exact source/artifact identities. Preserve failures, skips, interrupted attempts and pending gates explicitly. A previous candidate's result or a source review does not certify a successor or establish OntarioMD certification.
