# Release coverage work for issue #3774

The original ticket compares develop `9e27e0ce5f` with main `d182f254cd`. This work targets release/2026.08 at `3fa30806b3`. Test-name and route-string presence is useful inventory, but does not prove execution or a workflow assertion.

## Scope corrections

- SMS, the outbound-email archive, the consent-status converter, bounded email output stream, archive read-audit service, and V1.0.25/V1.0.26 are absent from this release. Their develop-specific test gaps remain on #3774; tests must not introduce those application features or migrations to this branch.
- `UserPropertyScheduleNavigationModeTest` and `ProviderPropertyActionScheduleNavigationModeTest` already inherit `unit`/`fast` from `CarlosUnitTestBase`. The former nevertheless missed the default Surefire filename patterns. Renaming it to `UserPropertyScheduleNavigationModeUnitTest` restores its four behavioral cases to default discovery and removes its unmatched-baseline exception. The provider test is already included and needs no redundant annotation.
- Credential logging and the simple framework checks now have the `unit` tag. The DAO example, Spring-backed action example and Spring-context checks have `integration`, matching their real Spring/database dependencies.
- DAO implementation filenames undercount indirect tests: the available full-suite JaCoCo report shows exercised methods in ConsultRequestDaoImpl, ProviderDataDaoImpl and SecurityDaoImpl. New tests must address behavior and missing branches, rather than duplicate an existing interface test just to match a class name.

## Measured Java baseline

The report comes from the completed combined promotion-validation build (`bc61743a06`, 12,503 tests, 51 skips, no failures/errors). Each production blob below was checked identical to the release tip; unmerged PDF/scratchpad fixes are not imported by this coverage branch. These counters describe Java tests, not live browser coverage or a guarantee that every behavior is correct.

| Class | Covered lines | Missed lines | Covered branches | Missed branches |
| --- | ---: | ---: | ---: | ---: |
| `SupServiceCodeAssoc2Action` | 0 | 51 | 0 | 24 |
| `ConsultRequestDaoImpl` | 74 | 12 | 36 | 18 |
| `EmailConfigDaoImpl` | 2 | 16 | 0 | 2 |
| `ProviderDataDaoImpl` | 74 | 51 | 21 | 15 |
| `SecurityDaoImpl` | 27 | 17 | 1 | 5 |
| `FrmBCAR2007Record` | 0 | 68 | 0 | 12 |
| `FrmBCAR2012Record` | 0 | 61 | 0 | 10 |
| `FrmFormRHPrevention2Action` | 0 | 62 | 0 | 14 |
| `ManageInboxhub2Action` | 0 | 74 | 0 | 16 |
| `MammogramReport` | 0 | 209 | 0 | 122 |
| `PapReport` | 0 | 199 | 0 | 110 |
| `RxDsMessageTo1` | 0 | 135 | 0 | 22 |
| `RHWorkFlow` | 0 | 53 | 0 | 2 |
| `WorkFlowState` | 0 | 73 | 0 | 16 |

## Reviewable work groups

1. Release audit, test discovery and tier classification (this PR).
2. DAO persistence and prevention-report behavior, including the release's actual EmailConfig schema.
3. Pregnancy forms, RH workflow transitions, clinical DTO behavior and inbox dispatch.
4. Admin/security and email browser tasks with owned fixtures and loopback mail capture.
5. Demographic tasks and labels, with a route-to-workflow/authorization matrix for remaining gates.
6. Billing/report and facility-selection coverage for applicable release routes.

The groups may be adjusted within the requested 2–8 PRs as source review and live validation establish which existing tests already cover a task. Completion needs meaningful assertions, negative controls where practical, owned-fixture cleanup, and CI/review convergence. Source presence alone does not close a gap.

## Additional evidence tiers

Record manual/agent-driven UI scenarios from `.claude/commands/ui-tests` and `docs/ui-tests` separately, with the tested commit, deployment and result. Record encoder null-safety lint, CodeQL, Semgrep and SpotBugs as static checks with their specific failure classes. Neither tier substitutes for Java assertions or a live end-user workflow. A missing route literal in a Playwright file is not proof of missing coverage when navigation helpers reach the route.

## Classification validation

JUnit discovery and execution with the `unit` tag selected 17 cases across the audited test classes plus the Surefire include contract; all passed. The `integration` tag selected 26 cases and all passed. The VM was stopped for compilation. The four renamed user-property cases execute under the unit tag and now match the default `*UnitTest` include. Tests that already inherit unit classification remain unchanged.

The incoming-PDF workflow belongs to the extended tier: it needs a local, writable incoming-document queue with service ownership and Poppler tools. Ordinary core runs now exclude it; explicit `--only incoming-pdf-extraction` and extended runs still select it. A runner selection regression covers all three paths (review follow-up from #3773).

The extended incoming-PDF workflow also rotates one page, rotates all pages, and deletes a selected page. Independent Poppler metadata/text checks verify exact rotations and retained page content, reopening and unchanged unrelated/extracted documents. Positive validation is performed against the combined DEB containing #3779 and #3780; the release baseline is expected to expose the documented PDF integrity/gating defects.
