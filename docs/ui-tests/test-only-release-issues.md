# Test-only release issue follow-up

This change is based on `release/2026.08` at `eba0178bdd2`. It repairs test
infrastructure and assertions; it does not change application source, database
migrations, or CI workflow definitions.

## Bounded repairs

| Issue | Change | Validation scope |
| --- | --- | --- |
| #5581 | Authenticated POST refusal probes obtain the live session's CSRF token; transport errors appear in the flowsheet assertion. | Real local HTTP transport and helper regressions; packaged patient-lock flow still needs a run. |
| #5580 | Fixture role privileges determine schedule access; the login helper verifies and consumes only the corresponding authenticated schedule refusal. | Fixture and login/probe regression tests; restricted-role packaged checks still need a run. |
| #5578 | Pin axe in the validation image; report missing axe, CDS registration/tooling, fax account, or heartbeat exclusivity gates as SKIP. | Prerequisite and cleanup-failure regression tests; wrapper already exports exclusivity and Schedule Setting already owns its provider. |
| #5577 | Track requests pending at document navigation/close; retain qualified cancellations in diagnostics and fail unexplained aborts. | Harness/navigation regression tests; three clean full packaged runs remain required. |
| #5576 | Assert the owner's tooltip decision; retire the contradictory partial-allergy-date workflow and remove its manifest/alias. | Policy tests; full-date allergy lifecycle checks remain registered. |
| #5575 | Assert the former probes' correct 400 responses; use test-only embedded Tomcat servlets for deterministic HTML and `/ws` 500 replacement, including disabled-filter controls. | 13 real HTTP/Tomcat tests plus browser assertion regressions; packaged front-door smoke still needs a run. |
| #5574 | Exclude only the exact global linking-lock sentinel in its owning table; keep rejecting other negative/malformed IDs. | Allocator regression tests including textual lookalikes; order-dependent packaged run remains required. |
| #5566 | Wait for `load` before the mocked rollback steps. The release base already used `click()` rather than `uncheck()`. | Harness/UI tests; 50 isolated packaged repetitions remain required. |
| #5166 | Support both message selectors and composer password contracts; decode visible MIME before checking the encrypted-message boundary. | Compose helper/MIME regression tests; current develop and release package runs remain required. Forward-merge this release fix to develop. |

SKIP is unavailable coverage, never a passing application assertion. Existing
annotation failures and accumulated CSP/page errors remain fatal when a later
fax prerequisite is missing; cleanup failures also override SKIP.

The browser error-sanitization smoke check now proves controlled client-input
refusals. Its pass alone does not prove 5xx replacement through nginx: the
deterministic Java integration tests establish replacement on real Tomcat.
There is no fault endpoint in the shipped WAR.

## Coverage plan #4492

Much of the plan already exists in the base, including upgrade canaries,
configuration profiles, dialog checks, UI quality checks and supervised cleanup.
See [the existing task/evidence map](harness-coverage-5456.md). This follow-up
uses the real Dashboard date picker for the successful save, exercises deletion
of an ordinary unused measurement stylesheet while preserving used mappings,
and pins the HDL-versus-total-cholesterol defect with explicit TODO regressions
against the actual calculator functions (finding 166 / #4553).

Do not close the plan on the strength of registrations or helper tests. Its
remaining acceptance includes current-build finding retakes, required clinical
profile prerequisites, the CAISI client-mode maintainer decision, and a complete
final-source SIGINT/SIGTERM residue inventory. Historical #5456 runtime results
are not new runtime results for this change.

## Branch-inapplicable race coverage #4102

The requested concurrency implementations are present on `develop` at
`313face61944e4c5c85743a5fc742d7df7b4fd13`, but absent from this release:

| Requested race | Develop implementation | Release state |
| --- | --- | --- |
| SMS queue claim (#4063) | `0f86decea45`, `sms/dao/SmsTransactionDaoImpl.java` | No SMS production package. |
| SMS settings save (#3927) | `406a37bb560`, `sms/service/SmsConfigService.java` | No SMS configuration classes. |
| Consent first save (#3914) | `0e4842186cd`, `ConsentDao.lockPatientForConsentChange` and `findLiveByDemographicAndConsentTypeIdForUpdate` | Both methods absent; the release manager uses the older read/persist sequence. |

The release's native MariaDB CI lane is not evidence for these specific races.
Keep #4102 open for tests against the actual develop implementations; invented
stand-ins or feature backports would not be a test-only release repair.

## Validation record

The focused Maven invocation was:

```sh
mvn -B -ntp test -Dtest=ResponseSanitizationFilterTomcatIntegrationTest -Dtest.forkCount=1
```

It passed dependency-lock/enforcer checks, Checkstyle, full main/test compilation,
and all 13 integration cases without failures, errors, or skips. The complete
JavaScript suite is run with `node --test scripts/*.test.js` after `npm ci`.
No full packaged CARLOS install was available in this execution environment;
the runtime acceptance listed above is deliberately unclaimed.
