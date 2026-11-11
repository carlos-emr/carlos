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
| #5972 | Back up durable, verified locked ID cohorts before deleting those same rows; report partial failures and retain recovery artifacts. | Real MariaDB dump/restore, final microsecond boundary, quoted credentials, concurrency, audit failure, timeout, missing rows, second-cohort failure and retry. |
| #5765 | Preserve saved/preseeded consent; announce retained true before stopping services; document old-default provenance. | Debconf/service-decision tests and documentation. |
| #5766 | Retire the unchanged distribution nginx default-site link before rendering, including IPv6 defaults. | Real helper function over stock/custom files and native package installation. |
| #5979 | Perform that cleanup independently of deferred provisioning; preserve custom targets and edited stock files. | Both provisioning-lock states; ambiguous metadata and custom files retained. |
| #5980 | Certificate regeneration preserves an inactive nginx; active reload/restart recovery remains. | Running/stopped/invalid-config command execution tests. |
| #5767 | Restore published beta6/beta7 carlos.xml bytes. | Published blob identity and real isolated dpkg upgrade retaining operator edits without a prompt. |
| #5974 | Restore published beta6/beta7 logging.properties bytes. | Same dpkg probe; intermediate snapshots explicitly retain normal conffile handling. |
| #6058 | Add distinct 2026.09.0~snapshot30 changelog/version. | dpkg version comparison and actual binary package build. |
| #5743 | Freeze the final stamp before qualification; reuse the candidate; compare final Debian contents and every asset hash. | Byte-preserving stamp, frozen-build reuse, final package payload and changed-asset rejection tests. |
| #5742 | Gate both publication paths on trusted exact-commit qualification, raw evidence and the latest required CI/job results. | Forged/stale/fork/failed/skipped/missing/duplicate evidence controls; workflow validation. |

The final focused Java run passed 341 cases with zero failures/skips, including
seven real MariaDB purge cases through the production tracking datasource and
compatibility driver. It covers SQL Date callers, write-only properties files,
and logged preparation failures without accessing the database. The clean JDK 25 build compiled all
JSP translation units under the repository's jspc validation profile and built
the WAR. The pinned DrugRef source also built successfully.

The complete Node 22.22.2 regression run passed all 7,913 cases across 560 files,
with zero skipped/cancelled/todo cases. Only then was its reviewed case inventory
regenerated. Packaging's Python 3.14 run passed all 258 cases with real restic;
the CI helper suite passed all 416 cases with real XML validation. The encoder,
BDD naming, SecurityException convention, SQL safety (116 cases), release-plan
validation and actionlint checks passed. The installed preflight also passed
inside the exact digest-pinned release JDK image with networking disabled.
An actual ON package installation passed the published CLI health checks and
first-login reset. Real systemd probes repaired a restored flag while holding
the CLI lock, refused a missing key and an unwritable home override, preserved
the original key, and kept nginx inactive through forced certificate renewal.
Health checks passed again after restoring the test configuration/certificates.

These are development and regression results, not a release qualification
receipt. Native package installation results are recorded in the PR. The wider
release matrix still contains external/incomplete acceptance work and remains
blocking. No global readiness claim or publication bypass is added. See
[the publication procedure](release-publication.md) and
[audit purge recovery](../audit-log-purge.md) for operational requirements.
