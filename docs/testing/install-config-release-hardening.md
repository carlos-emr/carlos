# Installation, configuration and release hardening

This change addresses the following 19 issues in the CARLOS repository. Exports
and #6060 are excluded. The carlos-ctl pin, dependency floor, runtime code,
commands and arguments are unchanged; compatibility tests use published 1.3.2.

| Issue | Implemented correction | Verification |
| --- | --- | --- |
| #5761 | Reconcile the chart flag outside the provisioning lock, including deferred configure. | Executed postinst block; native preflight while the published CLI lock is held. |
| #5763 | Use Java Properties effective values, preserve already-true files, announce overrides. Upgrade status also parses effective values. | Duplicates, escapes, continuation, trailing layout; independent status comparison with JDK Properties. |
| #5764 | Prepare every packaged start; verify the effective home/managed files and existing key as the service user. | Native root/service-user permissions, read-only true, missing key, unwritable home, untrusted parent. |
| #5975 | Warn about a distinct home override and name the actual generated-key destination. | Override-plus-home startup tests, private key canaries. |
| #5976 | Refuse a failed explicit override with its path and read/lock diagnosis. | Missing/malformed selected sources and startup refusal tests. |
| #5982 | Keep public saveProperty create/append/error behavior, including write-only files; lock and flush before updating memory. | Creation, retained inode/bytes, write-only permissions, failures and concurrent writers. |
| #5983 | Remove the unreachable WEB-INF fallback, retain explicit-override then home precedence. | Startup tests and clean application compilation. |
| #5770 | Identify fixed invalid JDBC option names without reflecting URLs or credentials. | 65 JDBC compatibility cases, including credential canaries. |
| #5863 | Resolve audit-purge configuration at invocation; remove eager database-name slicing. | No-config bean construction and plain-name facade tests; native application startup. |
| #5972 | Back up durable, verified locked ID cohorts before deleting those same rows; report partial failures and retain recovery artifacts. Preserve primary failures through cleanup and report credential-removal failures. | Real MariaDB dump/restore, day boundary, credentials, concurrency, audit failure, timeout, missing rows, partial failure/retry, and rollback/invalidation, reaping and credential-removal faults. |
| #5765 | Preserve saved/preseeded consent; announce retained true before stopping services; document old-default provenance. | Debconf/service-decision tests and documentation. |
| #5766 | Retire the unchanged distribution nginx default-site link before rendering, including IPv6 defaults. | Real helper function over stock/custom files and native package installation. |
| #5979 | Perform that cleanup independently of deferred provisioning; preserve custom targets and edited stock files. | Both provisioning-lock states; ambiguous metadata and custom files retained. |
| #5980 | Certificate regeneration preserves an inactive nginx; active reload/restart recovery remains. | Running/stopped/invalid-config command execution tests. |
| #5767 | Restore published beta6/beta7 carlos.xml bytes. | Published blob identity, isolated dpkg probe, and all four beta6/beta7 × ON/BC native upgrades retain operator edits without a prompt. |
| #5974 | Restore published beta6/beta7 logging.properties bytes. | Same four native upgrades; intermediate snapshots explicitly retain normal conffile handling. |
| #6058 | Add distinct 2026.09.0~snapshot30 changelog/version. | dpkg version comparison and actual binary package build. |
| #5743 | Freeze the final stamp before qualification; reuse the candidate; compare final Debian contents and every asset hash. | Byte-preserving stamp, frozen-build reuse, final package payload and changed-asset rejection tests. |
| #5742 | Gate both publication paths on trusted exact-commit qualification, raw evidence and the latest required CI/job results. | Forged/stale/fork/failed/skipped/missing/duplicate evidence controls; workflow validation. |

The final complete H2 execution at `5a02e389bda` passed 36,211 actual cases with
zero failures/errors and 219 exact registered routed/prerequisite skips. Seven
real MariaDB purge cases and five unprivileged permission cases passed separately
without skips. All 6,311 production classes and 5,462 compiled test classes stayed
byte-identical across these final executions. The final focused run passed all
49 cases, including cleanup faults. Two CDS families abort before creating test
cases without their protected native selector; their 12- and 7-case requirements
remain in `java-cds-native` and are explicitly recorded in the coverage baseline.

The clean isolated JDK 25 build compiled the JSP translation units and dispatched
fragments with zero errors and built the WAR. The pinned DrugRef source also
built successfully. Final Debian packages built and passed the lintian error gate.
Their 9,283 application files match the frozen WAR plus exactly the two reviewed
packaging transforms. Packaging preserves the already-stamped WAR bytes, and all
6,311 application classes match the tested classes.

The complete Node 22.22.2 regression run passed all 7,913 cases across 560 files,
with zero skipped/cancelled/todo cases. Only then was its reviewed case inventory
regenerated. Packaging's Python 3.14 run passed all 258 cases with real restic;
the CI helper suite passed all 416 cases with real XML validation. All 30 coverage
tooling cases passed. The encoder, BDD naming (526 files), SecurityException
convention, SQL safety (116 cases), release-plan validation and actionlint checks
passed. Focused SpotBugs/Find Security Bugs analysis of the changed purge/action
classes completed with zero findings and zero analyzer errors. The installed preflight also passed
inside the exact digest-pinned release JDK image with networking disabled.
Fresh ON and BC package installations passed the published CLI health checks and
first-login reset. Real systemd probes repaired a restored flag while holding
the CLI lock, refused a missing key and an unwritable home override, preserved
the original key, and kept nginx inactive through forced certificate renewal.
Health checks passed again after restoring the test configuration/certificates.
An ON reinstall retained edited conffiles, all credential hashes and the original
encryption key without a prompt. Independent PRE/POST package preservation checks
passed beta6 and beta7 upgrades on both ON and BC, including migrations, clinical
row counts, stored documents, configuration, credentials, key and service health.
The current browser upgrade harness fails before upgrading because its fixture
cleanup expects `rx_submission_receipt`, absent in those predecessors; the full
browser upgrade matrix remains pending.

These are development and regression results, not a release qualification
receipt. The source-bound coverage baseline has 739 floors and retains every
higher historical threshold and required refusal line. All nine Java paths in
this PR meet their retained floors. The wider comparison still refuses 194
assertions, including 79 existing unreviewed zero floors, pending the required
native union and review. Native installation details are recorded in the PR.
The wider release matrix retains its external/incomplete acceptance work and
continues to block publication. No waiver or publication bypass is added. See
[the publication procedure](release-publication.md) and
[audit purge recovery](../audit-log-purge.md) for operational requirements.
