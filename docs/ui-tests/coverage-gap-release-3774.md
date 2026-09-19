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

Eight PRs target `release/2026.08`:

| PR | Scope | Validation evidence |
| --- | --- | --- |
| #3781 | Discovery/tier corrections, audit, incoming PDF rotate/delete coverage | 17 unit + 26 integration cases; eight PDF workflow steps on validation13 |
| #3782 | Email configuration persistence/schema, secure DAO sorts, screening reports | 48 integration cases; migration and future-upgrade-order regressions; migrated MariaDB check |
| #3783 | BCAR forms, RH workflow/action, clinical DTO, inbox dispatch | 51 focused cases; transition mutation detected |
| #3787 | BC supplementary billing association CRUD | 54 focused/contract cases; three installed-DEB BC workflow steps |
| #3788 | Demographic gate authentication, labels and printing | 25 anonymous route checks; six label/print workflow steps |
| #3789 | ON payment-type CRUD, CSRF readiness and visible errors | 18 executable script cases; four installed-DEB workflow steps |
| #3790 | Security administration, eForm email/status, agreement upload | Security/email/agreement VM workflows, bounded SMTP-sink checks, 19 agreement Java cases |
| #3793 | Facility selection/revocation and patient health-care team | Four facility and three team workflow steps on installed DEBs |

Live runs use the Ubuntu 26.04 VM with locally built matched DEBs and owned fixtures. Each group's document identifies configuration prerequisites, cleanup and limitations. Negative controls exposed application defects rather than merely exercising successful paths: BC association writes, payment CSRF, the security stylesheet, agreement file location/request validation and the email schema mismatch. Issues #3784, #3785, #3786, #3792, #3794 and #3795 track them in their corresponding group PRs.

Existing contact-lifecycle tests already cover personal/internal/professional contact CRUD and flags; the demographic group adds authentication-policy checks rather than claiming new CRUD coverage for every gate. The facility group adds the previously missing team workflow. Existing DAO coverage is retained where the audit's class-name matching understated it. Develop-only SMS/archive/encryption-specific behavior remains outside this release branch and remains open on #3774. These PRs do not claim complete line/branch coverage or close that broader develop audit by route-name presence alone.

Completion still requires final changed-head VM checks and CI/review convergence. Rate-limited review requests are not counted as completed reviews.

## Additional evidence tiers

Record manual/agent-driven UI scenarios from `.claude/commands/ui-tests` and `docs/ui-tests` separately, with the tested commit, deployment and result. Record encoder null-safety lint, CodeQL, Semgrep and SpotBugs as static checks with their specific failure classes. Neither tier substitutes for Java assertions or a live end-user workflow. A missing route literal in a Playwright file is not proof of missing coverage when navigation helpers reach the route.

## Classification validation

JUnit discovery and execution with the `unit` tag selected 17 cases across the audited test classes plus the Surefire include contract; all passed. The `integration` tag selected 26 cases and all passed. The VM was stopped for compilation. The four renamed user-property cases execute under the unit tag and now match the default `*UnitTest` include. Tests that already inherit unit classification remain unchanged.

The incoming-PDF workflow belongs to the extended tier: it needs a local, writable incoming-document queue with service ownership and Poppler tools. Ordinary core runs now exclude it; explicit `--only incoming-pdf-extraction` and extended runs still select it. A runner selection regression covers all three paths (review follow-up from #3773).

The extended incoming-PDF workflow also rotates one page, rotates all pages, and deletes a selected page. Independent Poppler metadata/text checks verify exact rotations and retained page content, reopening and unchanged unrelated/extracted documents. Positive validation is performed against the combined DEB containing #3779 and #3780; the release baseline is expected to expose the documented PDF integrity/gating defects.
