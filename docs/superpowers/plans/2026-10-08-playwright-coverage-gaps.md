# Playwright Coverage Gaps (2026.08) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status (2026-10-09).** PR #4416 delivered Phase 0, Phase 1 and the part of Phase 3 listed in its description; Tasks 35–48 and the upgrade-path check (Task 17) are not done and would be follow-up PRs. The numbers in this plan are as of 2026-10-08 and must be re-read before starting a task: **the findings log now ends at 266, so the next free number is 267** (not 182); the manifest has 589 entries (not 496); `release/2026.08` has fixed findings 140, 150, 152, 173, 176 and 214, so a task that says to pin one of them (Tasks 15 and 39 name 150, 152, 173 and 176) should assert the fixed behaviour with no `expectedFailure`; and the test baseline is 3,503. `xss-poison` payload ranges and the other "next free" values need the same re-read.

**Goal:** Close the workflows that the Playwright suite (`scripts/playwright-suite.json`, 496 checks) does not exercise, or exercises only shallowly. Each gap gets a check that asserts correct behaviour. Defects that stand are recorded rather than encoded into the checks.

**Architecture:** Each gap becomes a `*-playwright-checks.js` script built on the shared harness (`runWorkflow`, owned FAKE- fixtures, marker cleanup). It also gets a manifest entry and an npm alias. Each check is validated live on the packaged Ubuntu 26.04 install that `scripts/deb-docker-validation.sh` brings up (Ontario or BC). Harness gaps come first, because later tasks rely on them: known-failure bookkeeping, residue audit and configuration profiles. No application code changes. A check that exposes a defect fails, and the defect goes into `docs/ui-tests/app-findings-log.md`.

**Approved scope (2026-10-08):** Phases 0, 1, 3, 4 and 5. Phase 2 (British Columbia, Tasks 18–27) and every BC step elsewhere are excluded; they are tracked in #4439. Validation runs on the Ontario container only.

**Tech Stack:** Node 22, Playwright (Chromium at `/opt/pw-browsers`), `node:test` meta-tests, Bash, MariaDB 11.8, the `carlos-emr` / `carlos-emr-drugref` / `carlos-ctl` Debian packages.

**Spec:** The gap inventory in §1 below. It was built on 2026-10-08 from five read-only mapping reviews: cross-cutting; chart and clinical; documents, labs, Rx, scheduling and forms; billing and reports; admin, integrations, provider and login. Each review measured the 1,079 Struts routes and the 414 untouched ones against the manifest. The five reports, which give each gap's path, routes and fixtures, are in the appendix `docs/superpowers/plans/2026-10-08-playwright-coverage-gaps-reviews.md`. Background: `docs/ui-tests/playwright-coverage-plan-2026.08.md` (§5 sequencing, §6 deliberate exclusions) and `docs/ui-tests/release-2026.08-workflow-coverage-expansion.md`.

## Global Constraints

- **Report, don't encode.** A check asserts correct behaviour and fails while a defect stands. Each defect is a row in `docs/ui-tests/app-findings-log.md`. Rows are numbered consecutively; the next free number is **182**. Status is `open`, `issue-filed` or `fixed`, and every evidence cell is longer than 20 characters. Issues are filed only when the user asks.
- **Manifest and alias.** Every new check gets an entry in `scripts/playwright-suite.json`. Its fields are `name`, `script`, `tiers`, `assertsDatabase`, `provinces`, `timeoutSec`, `env` (the env var names it reads), `fixtures` and `notes`. It also gets a `test:<name>` alias in `package.json`.
- **Meta-tests stay green.** `node --test scripts/*.test.js` reports 0 failures after every task; the baseline is 2,974 tests. A static test that pins an open finding uses `node:test`'s `{ todo: 'finding N' }`, so CI stays green until the fix lands and then reports the flip.
- **Synthetic data only.** Fixtures are FAKE-/marker-named patients, synthetic HINs and PHNs, and owned rows. Cleanup removes them by key and asserts they are gone. No check writes message text, names or HINs to logs or results.
- **Clinic-wide state.** A check that changes clinic-wide state (properties, the clinic row, `fax_config`, the MCEDT password, Select Forms registrations, `billing_preferences`) snapshots it and restores it byte-exact. Its `fixtures` must say "must not run concurrently with other checks (live wrapper: EXCLUSIVE=1)".
- **Nothing reaches live external systems.** Never press Teleplan Send / Get Remittance / Check Eligibility, MCEDT Upload & Submit / Download, DHIR Submit, or OntarioMD lookups, and never use live SRFax.
- **xss-poison payload ranges are unique per check.** The next free range starts at 1100 (`xss-poison-note-history` owns 1000–1099).
- **`.github/**` cannot be written by Claude.** CI changes are delivered as a reviewed proposal for a maintainer to commit.
- **Commits.** Conventional Commits (`test:`, `docs:`, `chore:`), `git commit -s`, and the session's `Co-Authored-By` / `Claude-Session` trailers.
- **Branch.** The default is a new branch `claude/playwright-coverage-gaps-2026-08` from `release/2026.08`, with a PR to `release/2026.08`. The user may redirect this.

## Review Focus

1. **A check that passes without reaching the code under test.** Causes include a hidden menu item, a blocked popup, a 404 or WAF page read as "refused", or a skipped branch. Every check asserts a page-specific selector before asserting outcomes. A refusal assertion requires the app's 403/405 body or `securityError` page **and** an unchanged row count; a bare non-200 is not enough. (Owned by each check task. The shared assertion `assertRefused()` is in Task 2.)
2. **Clinic-wide residue from one check changing the next.** Finding 180 came from `fax-configure`. Task 3's `--residue-audit` runs after every live validation in this plan and must report nothing.
3. **An expected failure hiding a new failure at a different step.** Task 2's `expectedFailure: {finding, step}` turns a failure at any other step into `failed-elsewhere`, which fails the run.
4. **A check run on the wrong province or configuration.** It must skip with a recorded reason, never pass vacuously. Task 40's `shouldSkipWithReason_whenProfileInactive` pins this for profiles, and Task 27 corrects `provinces` from a real BC run.
5. **The WAF refusing the test's own fixture text, misread as an application refusal.** `isWafPage()` (in `scripts/lib/get-reject-probe.js`) classifies it. Front-door runs report a WAF refusal as its own outcome (Task 4).

---

## 1. Gap inventory (the spec)

Status: **NOT** means not covered, **SHALLOW** means rendered or partially asserted, and **BLOCKED** means it needs a profile, a stub or a fix first. Priorities are those the reviews assigned: P1 is safety, PHI, money or a core daily workflow. "Cand." marks a new defect candidate (§2).

### 1.1 Cross-cutting and harness

| Gap | Status | P | Task |
|---|---|---|---|
| Nothing runs the suite automatically (no PR smoke, no nightly; `script-regressions.yml` runs only `*.test.js` and 3 fixtures) | NOT | P1 | 6 |
| Upgrade path N-1 → N on a populated database (last runs a11→a12 and a13→a14; `deb-upgrade-verify.sh` defaults are stale) | SHALLOW | P1 | 17 |
| PHI in error bodies and in non-ERROR log levels (the audit reads ERROR/FATAL/SEVERE only) | SHALLOW | P1 | 13 |
| WAF false positives on clinical prose (corpus covers 2 of 62 exclusions) | SHALLOW | P1 | 14 |
| Write-side authorization by role, tokenless replay of mutations, menu visibility, `/ws/rs` text/plain | SHALLOW | P1 | 11 |
| Unpinned 2026.08 findings on safety, PHI or money (149, 150, 156, 163, 165, 168, 173, 176, 178) | NOT | P1 | 15 |
| Province matrix: about 400 `all` checks never run on BC | SHALLOW | P2 | 27 |
| Configuration modes never run: address lockout, multisite, CAISI, no-PIN, legacy contacts, legacy Teleplan | NOT | P2 | 40–44 |
| Known-failure bookkeeping (133 prose notes, manual triage of 94 failures) | SHALLOW | P2 | 2 |
| Seed and demo-data contract (`_newCasemgmt.episode` o vs x; orphan note links) | NOT | P2 | 5 |
| Fixture hygiene: no `mutates` field, no residue audit, `fax-configure` left polling, 57 checks without signal-safe cleanup | SHALLOW | P2 | 3, 48 |
| Browser and front-door fragility (beforeunload gesture, download naming, WAF 403 on msgId) | SHALLOW | P2 | 4 |
| Direct-response contract and servlets outside Struts (`BackupDownload`, `OscarDownload`, log viewer) | NOT | P1 | 12 |
| Cancelling a `confirm()` writes nothing (accept paths only today) | SHALLOW | P2 | 38 |
| Other cheap finding pins (136, 142, 143, 151, 152, 154, 157, 159, 161, 166, 167, 169, 170, 177; 172, 174, 175 static) | NOT | P2/P3 | 5, 39 |
| Responsive hit-test, accessibility, i18n walk (would have caught finding 146) | NOT | P3 | 47 |

### 1.2 Chart and clinical

| Gap | Status | P | Task |
|---|---|---|---|
| Two charts in one session: navbar "+", measurement history, SetupForm and plots take the patient from the session-wide `EctSessionBean` (cand. 182) | NOT | P1 | 7 |
| Unsaved note: exit confirm, autosave draft recovery after close or kill, no cross-provider leak | NOT | P1 | 8 |
| Verify & Sign; appointment status S/V; back-dated and future observation dates; encounter type | NOT | P2 | 28 |
| Issues: assign to a note, Issues / Resolved Issues lists, filter by issue (cand.: filter has no opener) | NOT | P2 | 29 |
| Encounter form catalogue: about 33 ON forms never saved or reopened; MH 14/42, Annual V2, MH chain | NOT | P2 | 30 |
| Rourke 2020 and growth charts | NOT | P2 | 31 |
| Lab result reaching the flowsheet (HbA1c → A1C row, out-of-range flag, lab link) | SHALLOW | P2 | 32 |
| Consultation config writers accept GET and need only read rights (findings 107/108, never live-checked) | NOT | P2 | 33 |
| Annual Review Planner risk/checklist Save; flowsheet reading delete; CPP copy and position; Forms list paging | SHALLOW | P3 | 45, 46 |
| Pregnancy module (route unmapped, cand. 183) | BLOCKED | P3 | 46 |

### 1.3 Documents, labs, Rx, scheduling, demographics

| Gap | Status | P | Task |
|---|---|---|---|
| Inbox File and manual Forward of HL7 labs (popup, preview card, document viewer) | NOT | P1 | 9 |
| Legacy lab types from the OSCAR 19 import (MDS, old CML, PathNet): display, acknowledge, forward (cand. 186) | SHALLOW | P1 | 10 |
| Lab Recall preferences and the Recall action | SHALLOW | P2 | 35 |
| Legacy Add Relation / delete relation (`NEW_CONTACTS_UI=false`, which migrated clinics get) | NOT | P2 | 43 |
| eForm "Approve and download" of an incomplete render | SHALLOW | P3 | 45 |
| Rx satellite address, Manage Sites | NOT | P3 | 42 |
| Chart informed-consent banner | NOT | P3 | 46 |

### 1.4 Billing and reports, Ontario

| Gap | Status | P | Task |
|---|---|---|---|
| MCEDT local half: claim file staged to the outbox, Add/Delete upload, Change Password (no transport) | SHALLOW | P1 | 16 |
| Billing report for non-doctor roles (Unsettled / OB / FLU, OB2 popup) | SHALLOW | P2 | 37 |
| Overnight Batch Eligibility (OBEC) file content | SHALLOW | P2 | 37 |
| Letter template delete; invoice letterhead logo | NOT | P3 | 45 |
| Clinic NBR codes (rma), View MOH Files (property-gated) | BLOCKED | P3 | 42 |

### 1.5 British Columbia

| Gap | Status | P | Task |
|---|---|---|---|
| Bill an appointment, view it, unbill it (cand.: bill form lost its `name`, so its handlers throw) | NOT | P1 | 18 |
| Teleplan claim file: simulate and generate (cand. 187: GET commits a batch) | NOT | P1 | 19 |
| BC billing mutators: GET refusal and role authorization | NOT | P1 | 20 |
| MSP Quick Billing (cand. 184: Save dereferences a null form bean) | NOT | P1 | 21 |
| WCB claim and WCB form (cand.: three broken entry links) | NOT | P1 | 22 |
| Private bill: receipt, bill-to, Receive Payment (finding 164 stands) | NOT | P1 | 23 |
| Remittance import, reconciliation, settlement (needs `NEW_BC_TELEPLAN=no`) | BLOCKED | P1 | 43 |
| Accounting Reports (PDF/CSV); report center Billed/OB/Flu modes | NOT | P2 | 24 |
| Code administration, service-code/dx associations, referral docs, provider billing preferences | SHALLOW | P2 | 25 |
| BC-AR 2020 (pages 1–5), BC-NewBorn 2008, BPMH (cand. 185: the Forms-menu link does not resolve) | NOT | P2 | 26 |

### 1.6 Administration, provider, login, integrations

| Gap | Status | P | Task |
|---|---|---|---|
| Hidden PHI-bearing admin pages still live: Database/Document Download, Server Logging | NOT | P1 | 12 |
| Login-time facility chooser for a provider in two facilities | SHALLOW | P2 | 34 |
| Clinic/Agency Address edit and the letterheads that print it | SHALLOW | P2 | 36 |
| CAISI admin authoring (Default Encounter Issue, Facility Messages editor) | BLOCKED | P3 | 44 |
| PMmodule client and provider pages (Tiles results that cannot render; Vaccine Provider login) | BLOCKED | P3 | 44 |

### 1.7 Not planned here (external or needs a decision first)

These are blocked:
- MCEDT transport needs a WS-Security EDT SOAP stub.
- DHIR submission needs an SSO test hook and a stub.
- OntarioMD lookups, Ocean eRefer and eConsult, and the shared outcomes dashboard are external.
- Teleplan live and ManageTeleplan need a TeleplanService stub.
- Group notes need facility flags; cover them first if a clinic enables them.

These need a fix-or-retire decision first:
- Upload Login Exam / AUA: the upload writes a file the login page never reads, and the page renders the AUA unencoded.

The live-external SRFax scripts in `scripts/e2e/fax/` are registered as `manual` entries in Task 6. Visual diffing and load testing stay out of scope (§6 of the coverage plan).

## 2. New defect candidates surfaced by the mapping

Task 1 confirms each one and logs it. The first six are confirmed in source at the branch head; the rest come from the reviews' code reading and are unverified.

| Cand. | Defect | Evidence so far |
|---|---|---|
| 182 | `FrmSetupForm2Action` replaces the request's `demographic_no` with the session-wide `EctSessionBean`'s whenever a bean exists. With two charts open, a form opened from A's chart loads B's data. The measurement history and navbar loaders also fall back to the bean. | Source: `if (demo == null \|\| bean != null) demo = bean.getDemographicNo();` (FrmSetupForm2Action:115-118) |
| 183 | The Pregnancy module's links all 404. `EctDisplayPregnancy2Action` builds `/Pregnancy?method=…`, but no Struts action named `Pregnancy` exists, so `Pregnancy2Action` is unreachable. | Source: struts-*.xml has only `encounter/displayPregnancies` |
| 184 | BC Quick Billing Save throws an NPE and saves nothing. `QuickBillingBCSave2Action` builds `new QuickBillingBCHandler()` with no form bean, and `saveBills()` reads `quickBillingBCFormBean.getBillingData()`. | Source: QuickBillingBCSave2Action:83, QuickBillingBCHandler:415-418 |
| 185 | BC BPMH cannot be opened from Chart ▸ Forms. The seed registers `../formBPMH.do?…`, and `FormViewRoutes.resolveActionPath` maps only `/form/pharmaForms/formBPMH.jsp`. `EctDisplayForm2Action` therefore falls back to the raw `.do` URL, which no action matches. | Source: V1.0.2__bc_data.sql:22725, FormViewRoutes:255 |
| 186 | Forward on the MDS, CML and PathNet lab pages cannot submit: SelectProvider.jsp's Submit button is commented out. | Source: SelectProvider.jsp:169 |
| 187 | `GenerateTeleplanFile2Action` has no method guard, so opening its menu item (a GET) generates a batch: files, a billactivity row and a sequence number. | Source: the action checks `_billing` w and nothing else; the batch commit is from the review, unprobed |
| — | BC: the bill form lost `name="BillingCreateBillingForm"`, so its handlers throw; the BC delete actions have no method guard; the WCB entries point at `wcbForms.jsp`, `viewformwcb.do` and `billingTeleplanCorrectionWCB.jsp`; `DownloadBilling` is unmapped; MSP Reconciliation calls `validatePath(null)`; `SaveAssoc2Action` saves despite errors; the Teleplan menu labels are swapped | Review code reading |
| — | Chart: the notes filter panel has no opener; the issue-assignment controls are not rendered | Review code reading |
| — | Admin and CAISI: PMmodule results map to removed Tiles definitions; `Login2Action` returns an unmapped `patientIntake`; the log viewer serves `LOGGING_PATH` to `_admin.reporting` holders; the MCEDT password is stored in plaintext; menu links go to unmapped `SurveyManager`, `RecommitHSFO` and `/commons/omdDiseaseList.jsp` | Review code reading |

## 3. Shared interfaces (every check task consumes these)

- `runWorkflow(name, workflow, { openPatient })` from `scripts/lib/workflow-session.js`. `workflow(s)` gets `s.step(label, body)`, `s.sql.value(sql)`, `s.cleanup`, `s.marker`, `s.provider`, `s.recorder`, `s.context` and `s.config.baseUrl`.
- `runCheck(options)` from `scripts/lib/playwright-harness.js`, plus `appUrl`, `assert` and `sqlString`.
- `throwawayLoginFixture` and `submitLoginForm` from `scripts/lib/throwaway-login-fixture.js`; `authzReadFixture` and `seedPatientDomains` from `scripts/lib/authz-read-fixture.js`.
- `captureRequest`, `replayParams` and `isWafPage` from `scripts/lib/get-reject-probe.js`; `mutatorRoutes` and `routesForClass` from `scripts/lib/mutator-routes.js`.
- `watchFrontDoor` and `csrfToken` from `scripts/lib/front-door-checks.js`.
- `payload`, `inspect`, `Findings`, `Seeder` and `fieldIds` from `scripts/lib/xss-poison-helpers.js`; `seedPatient` from `scripts/lib/xss-poison-patient.js`.
- New in Task 2: `assertRefused(s, { response, table, where, before })` in `scripts/lib/playwright-harness.js`, and the manifest field `expectedFailure`.
- New in Task 3: the manifest field `mutates` and the runner flag `--residue-audit`.
- New in Task 40: the manifest field `profile` and the runner flag `--profile`.

### How every check task runs (steps referenced as S1–S4)

- **S1. Write the check.** Use the named file and assert exactly the bullet list in the task.
- **S2. Run it live.** Use the container that `scripts/deb-docker-validation.sh up` brings up for the task's province, through `docker exec … bash -lc 'source /root/suite-env.sh && node scripts/run-playwright-suite.js --only <name>'`. The result must match the task's **Expected** line. When it fails on a defect, add the finding row (Task 1 numbering) and the manifest `expectedFailure`.
- **S3. Register it.** Add the manifest entry and the npm alias, then run `node --test scripts/*.test.js`; expect 0 failures. Run `--residue-audit` on the container; expect "no residue".
- **S4. Commit.** `git add <files> && git commit -s -m "test: <name> …"`.

---

## 4. Tasks

### Phase 0: harness, bookkeeping, static contracts (no browser or any server)

### Task 1: Confirm and log the new defect candidates

**Files:** Modify `docs/ui-tests/app-findings-log.md` with a new subsection, "Found by the 2026.08 coverage mapping".

- [ ] Re-read the source for each §2 row. For rows marked "Review code reading", confirm at the cited lines or drop the row; never log an unconfirmed defect.
- [ ] Probe 182, 183 and 186 live on the ON container. Use a throwaway scratch script in the scratchpad, not a committed check. Record the observed status or row change. The BC rows (184, 185, 187 and the BC code-reading items) are out of scope: they are tracked in #4439 and are not logged here.
- [ ] Add rows 182 and up with evidence ("Live: …" or "Source: …") and status `open`.
- [ ] Run `node --test scripts/app-findings-log.test.js`; expect PASS. Commit with `docs: log findings 182–N from the coverage mapping`.

### Task 2: Known-failure bookkeeping and a shared refusal assertion

**Files:** Modify `scripts/lib/workflow-session.js` (`step()` records `failedStep` in the failure result), `scripts/lib/playwright-harness.js` (`assertRefused`), and `scripts/run-playwright-suite.js` (outcome classes and the JUnit message). Tests go in `scripts/playwright-suite-manifest.test.js`, which already imports the runner's `selectChecks` and `toJUnit`.
**Interfaces:**
- Produces the manifest field `expectedFailure: { finding: number, step: string }`.
- Produces the runner outcomes `known-fail`, `unexpected-pass` (reported, does not fail the run) and `failed-elsewhere` (fails the run).
- Produces `assertRefused(s, { response, table, where, before })`. It passes only on the app's 403/405/`securityError` body and an unchanged `COUNT(*)`.

- [ ] Write the failing tests:
  - `shouldClassifyKnownFail_whenFailedStepMatches` (finding 140, step "send from X's window")
  - `shouldFailRun_whenFailedElsewhere`
  - `shouldReportUnexpectedPass_whenExpectedFailurePasses`
  - In the manifest test: `expectedFailure.finding` must be a findings-log row whose status is not `fixed`.
  - For `assertRefused`: `shouldFailAssertRefused_whenResponseIsWafPage` (the message says "WAF refusal, not an application refusal"), `shouldFailAssertRefused_whenResponseIs404`, and `shouldFailAssertRefused_whenRowCountChanged`.
- [ ] Run `node --test scripts/playwright-suite-manifest.test.js`; expect FAIL.
- [ ] Implement it, then convert the 133 prose "fails on …" notes. A note that names a finding and a step becomes `expectedFailure`. Correct stale notes, such as `eform-groups` (it now fails on #4130's 302).
- [ ] Run the full meta-test suite; expect 0 failures. Commit.

### Task 3: Residue audit, `mutates`, and restoring `fax-configure`

**Files:** Modify `scripts/run-playwright-suite.js` (`--residue-audit`), `scripts/playwright-suite.json` (`mutates` on clinic-wide writers), `scripts/fax-configure-playwright-checks.js` (snapshot and restore the `fax_config` row), and `scripts/playwright-suite-manifest.test.js`. Create `scripts/lib/residue-audit.js`.
**Interfaces:**
- Produces `auditResidue({ sql, since }) → [{ table, count }]`. It checks marker-named rows in `demographic`, `provider`, `security`, `tickler`, `casemgmt_note`, `billing_on_cheader1`, `billingmaster`, `eform_data` and `document`; it also checks `fax_config`, the `property` and `UserProperty` rows the manifest's `mutates` lists, and `encounterForm` registrations.
- `mutates` is a `string[]` of clinic-wide objects.

- [ ] Write the failing tests: `shouldReportResidue_whenMarkerRowSurvives` (with a stubbed `sql`), and a manifest test requiring `mutates` on the known writers (`fax-configure`, `schedule-setting`, `eform-admin-crud`, `eform-image-delete`, `prevention-add-data`).
- [ ] Implement. `fax-configure` restores the row it found, or deletes the one it created, and asserts polling is off afterwards.
- [ ] On the ON container, run `--only fax-configure --residue-audit`. Expect a pass, no residue, and no further `FaxImporter.java:406` ERROR in the journal over the next 5 minutes. Commit.

### Task 4: Browser and front-door fragility

**Files:** Modify `scripts/double-submit-eform-playwright-checks.js` (click in the page before navigating, so `beforeunload` has a gesture), `scripts/export-content-eform-export-zip-playwright-checks.js` (assert the `Content-Disposition` header, not `suggestedFilename()`), `scripts/gap-provider-messenger-write-to-encounter-playwright-checks.js` (classify a WAF refusal with `isWafPage()`, then verify no row), and `scripts/run-playwright-suite.js` (`browserVersion` in the results and the JUnit properties).

- [ ] S1–S2: run all three with `EXPECT_FRONT_DOOR=true` on the ON container. Expected: PASS, or known-fail on their recorded findings, with no fragility failure.
- [ ] S3–S4.

### Task 5: Static contract tests

**Files:** Create:
- `scripts/waf-exclusion-routes.test.js`: every `@rx ^/carlos/<route>` in `debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf` resolves to a Struts action or a servlet mapping.
- `scripts/demo-dataset-contract.test.js`, with two checks. First, `secObjPrivilege` for every security object named in a `scripts/**/*-playwright-checks.js` source or in `scripts/lib/authz-read-routes.js` is identical across `database/mysql/migration/{on,bc}/V1.0.2__*_data.sql`, `.devcontainer/db/scripts/development.sql` and the additive artifact; the `_newCasemgmt.episode` row is `todo` until maintainers decide. Second, no `casemgmt_note_link` row points at a filtered tickler or document id (finding 143).
- `scripts/nginx-rate-limit.test.js`: `/ws/services` has a `limit_req`, marked `todo: 'finding 172'`.

Modify `debian/assets/tests/` to add a postinst hint test (174) and a paused-import guard test (175), both `todo`.

- [ ] Write the tests, then run `node --test` on them. Expect the routes and seed-diff checks to PASS and the finding pins to report `todo`.
- [ ] Commit.

### Task 6: Suite automation proposal and tier registration

**Files:**
- Create `docs/ui-tests/playwright-ci-proposal.md`, containing reviewed YAML for a maintainer to commit:
  - add `node scripts/run-playwright-suite.js --tier standalone` to `script-regressions.yml`;
  - add `playwright-smoke.yml` for pull requests, on the `tomcat-dev` and `mariadb-dev` images;
  - add a nightly `--tier core` run on `develop` and `release/**`, with JUnit upload.
- Modify `scripts/playwright-suite.json` to register `scripts/e2e/fax/*` as tier `manual` with `live-external` in `notes`.

- [ ] Run `node scripts/run-playwright-suite.js --tier standalone` locally; expect 4 pass. Record the time taken in the proposal.
- [ ] Run the meta-tests; expect 0 failures. Commit.

### Phase 1: P1 workflow checks (Ontario container unless stated)

### Task 7: `echart-two-patient-isolation`

**Files:** Create `scripts/echart-two-patient-isolation-playwright-checks.js`. Fixtures: two owned patients, each with one measurement and one form row.
- [ ] S1. Open A's chart, then B's chart in a second window, then return to A:
  - A's reloaded navbar, Measurements history, the SetupForm (Vascular Tracker) and the plot show only A's rows.
  - Every write made from A's window lands on A and leaves B's row counts unchanged: a measurement, a Dx, a CPP item, a prevention "+", a form save and a history Delete.
- [ ] S2. **Expected:** FAIL at "form opened from A's chart shows A" (cand. 182).
- [ ] S3, S4.

### Task 8: `echart-note-draft-recovery`

**Files:** Create `scripts/echart-note-draft-recovery-playwright-checks.js`.
- [ ] S1. Type a note and wait for the autosave POST. Then:
  - Dismissing Exit's confirm keeps the window and the text.
  - Accepting leaves exactly one `casemgmt_tmpsave` row holding the typed text.
  - Reopening restores the text, including after `context.close()` with no `beforeunload`.
  - Save writes `casemgmt_note` and removes the tmpsave row.
  - A second provider (throwaway login) never gets the draft.
- [ ] S2. **Expected:** PASS (no known defect).
- [ ] S3, S4.

### Task 9: `inbox-file-forward`

**Files:** Create `scripts/inbox-file-forward-playwright-checks.js`. Fixtures: a throwaway second provider, a synthetic HL7 lab (built the way `lab-upload` builds one) and an owned PDF.
- [ ] S1. Forward from the lab popup and from a preview card:
  - The recipient gets a `providerLabRouting` row with status N, and the sender's row is unchanged.
  - The lab appears in the recipient's Inbox.
- [ ] S1, continued. File:
  - Filing sets status F for this provider only and does not acknowledge.
  - Filing a document also closes `queue_document_link`.
  - A GET replay of `FileLabs` or `ReportReassign` passes `assertRefused`.
- [ ] S2. **Expected:** PASS, or a new finding.
- [ ] S3, S4.

### Task 10: `legacy-lab-display`

**Files:** Create `scripts/legacy-lab-display-playwright-checks.js`. Fixtures: one MDS lab, one old CML lab and one PathNet lab, seeded by SQL the way `pathnet-status` seeds them, plus a second provider.
- [ ] S1. For each type:
  - The display renders its values and the patient.
  - Acknowledge writes status A and the comment.
  - Forward creates the recipient's routing row.
- [ ] S2. **Expected:** FAIL at "forward" (cand. 186); display and acknowledge PASS.
- [ ] S3, S4.

### Task 11: `authz-write-role-matrix` and `csrf-negative-matrix`

**Files:** Create `scripts/authz-write-role-matrix-playwright-checks.js` and `scripts/csrf-negative-matrix-playwright-checks.js`. Modify `scripts/lib/authz-read-fixture.js` to add a write-restricted role holding `_demographic` r, `_appointment` w and `_eChart` r.
**Interfaces:** Consumes `captureRequest` and `replayParams` to capture each mutation from the full-privilege UI action.
- [ ] S1, authz. Log in as the restricted role through the login form. Then:
  - The top bar has no Administration or Billing.
  - The chart nav has no Rx, Bill or Allergy "+".
  - Replaying each captured mutation with a valid token passes `assertRefused`: tickler add, note save, Rx write, allergy add, ON bill save, demographic update, provider update.
  - The same request from the full session writes its row.
- [ ] S1, csrf. For each mutation family (appointment add, update and delete; demographic add and update; ON bill save; `eform/addEForm`; messenger send; consultation request; `AddPrevention`; measurement save), a tokenless replay passes `assertRefused`. A `text/plain` POST to a session-cookie `/ws/rs` mutation is refused.
- [ ] S2. **Expected:** PASS, or new findings.
- [ ] S3, S4.

### Task 12: `direct-response-contract`

**Files:** Create `scripts/direct-response-contract-playwright-checks.js`. Fixtures: a marker file in `backup_path`, a marker log under `LOGGING_PATH`, and an OHIP disk file in the configured directory.
- [ ] S1. One row per route: `/servlet/BackupDownload`, `admin/ViewAdminBackupDownload`, `admin/ViewOscarLogging` and `/servlet/OscarDownload` (homepath values `obecdownload` and the claim disk).
  - Doctor, nurse and receptionist get the app's 403.
  - The privileged control gets the listing and the exact bytes, with `Content-Disposition`.
  - `filename=../x` and a traversal `homepath` get 400 with no bytes.
  - No download body is an HTML error page.
- [ ] S2. **Expected:** PASS, or a finding if `_admin.reporting` reads the log viewer (cand.).
- [ ] S3, S4.

### Task 13: PHI in error bodies and logs

**Files:**
- Create `scripts/phi-in-error-pages-playwright-checks.js`. It provokes 400, 403, 404 and 405 for each route family and asserts the body contains no fixture HIN, FAKE- name or `demographic_no`.
- Create `scripts/deb-server-log-phi-scan.sh` and `scripts/deb-server-log-phi-scan.test.js`, following the `deb-server-log-audit` pair. It searches every journal and catalina level for this run's markers, FAKE-PW names and fixture HINs, and prints only counts and logger names.
- Modify `scripts/audit-log-chart-read-playwright-checks.js` to add the `CaseManagementEntry?method=history` path.
- [ ] Write the scan's test first, with fixture lines containing and not containing markers. Run it; expect FAIL, then implement and expect PASS.
- [ ] S2. **Expected:** `audit-log-chart-read` FAILs at the history step (finding 141). The log scan reports the finding 141 and 144 loggers.
- [ ] S3, S4.

### Task 14: `waf-clinical-text-corpus` (front-door tier)

**Files:** Create `scripts/waf-clinical-text-corpus-playwright-checks.js`. It is table-driven over the exclusion list's routes: tickler add and edit, Rx write and update, allergy add, measurement, prevention, manage document, lab status, messenger, appointment add and update, case-management entry, eForm add, and the billing comment routes (1134–1137). Reuse the `clinical-freetext` phrase corpus.
- [ ] S1. Each save, made through nginx, lands in the database with the exact text. A WAF 403 fails, naming the rule id.
- [ ] S2. Run with `EXPECT_FRONT_DOOR=true`. **Expected:** PASS, or a finding naming the missing exclusion.
- [ ] S3, S4.

### Task 15: Finding pins, batch A (safety, PHI, money)

**Files:** Modify only existing checks.

| Finding | Check | Pinned assertion |
|---|---|---|
| 150 | `oauth-rest-surfaces` | A scoped token calling an out-of-scope endpoint gets 403. |
| 149 | `lab-manual-entry-cumulative` | Hover the xss-poison Row Display tooltip (range 1100–1199): no `[data-xp]` element appears. |
| 156 | `patient-photo-upload` | With two photo popups open, Clear in the first keeps the second patient's `client_image`. |
| 163 | `admin-api-keygen` | GET `admin/ViewKeygenCreateKey?name=` adds no `publicKeys` row. |
| 165 | `pathnet-status` | A message with both F and P results is not listed as Final. |
| 173 | `billing-on-premium-payment-date` | An `radetail` row paid on the month's last day is in the report. |
| 176 | `flowsheet-patient-customization` | After customizing, a flowsheet-level decision-support message still shows. |
| 178 | `allergy-add-penicillin` | `regionalId` and `atc` are both set, on the amendment row too, with no `RxAddAllergy2Action` ERROR in the window. |
| 168 | `double-submit-eform` | A throwaway login without `_edoc` x submits with Add to documents and does not get the replay-409 text. |

- [ ] S1–S2, once per row. **Expected:** each FAILs at its new step, recorded with `expectedFailure`.
- [ ] S3, S4 (one commit).

### Task 16: `mcedt-mailbox-outbox` (ON, local half only)

**Files:** Create `scripts/mcedt-mailbox-outbox-playwright-checks.js`. It snapshots and restores `MCEDT_ACCOUNT_PASSWORD`, so it is EXCLUSIVE.
- [ ] S1. Generate the OHIP diskette the way `billing-on-group-disk-zero-total` does, then:
  - The H-file is copied to `ONEDT_OUTBOX` byte-identical and listed once; reopening does not duplicate it.
  - Add writes the fixture H-file. A non-OHIP/OBEC name or a traversal name is refused and nothing is written.
  - Delete removes only the owned file.
  - Change Password writes the property and never echoes it back.
  - GET or a tokenless POST to each mutator passes `assertRefused`, and a login without `_admin.billing` is refused.
  - Upload and Submit are never clicked.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 17: Upgrade path N-1 → N

**Files:**
- Modify `scripts/deb-docker-validation.sh` to add `upgrade <from-version>`. It installs the previous published packages with demo data, runs a set of mutating checks that keep their rows, snapshots, upgrades to the local build, then runs `deb-upgrade-verify.sh`, `--tier smoke` and the read-only core.
- Modify `scripts/deb-docker-validation.test.js`.
- Modify `scripts/deb-upgrade-verify.sh` to take its expected values from the `flyway_schema_history` diff instead of the a11→a12 defaults.

The populated-database leg of `db-schema-verify.yml` goes into Task 6's proposal.
- [ ] Write the failing test: `upgrade` without a version exits 2, and the expected values come from the history diff.
- [ ] Implement it, then run it on the ON container from alpha18 to the alpha19 build. **Expected:** verify PASS, smoke 12/12, and the read-only core matches the fresh-install results.
- [ ] Record the run in `docs/ui-tests/deb-install-validation.md`. Commit.

### Phase 2: British Columbia (container brought up with `CARLOS_SUITE_PROVINCE=BC`)

**Shared BC fixture (built in Task 18 and reused):** extend `scripts/lib/gap-billing-support.js` with `seedBcBillingFixture(s) → { demo, appt, providerNo, phn }`. It creates an owned FAKE patient with a synthetic BC PHN, an owned appointment, and a billing provider with MSP numbers. Cleanup removes `billing`, `billingmaster`, `wcb`, `bill_recipients` and `billing_history` rows by key.

### Task 18: `billing-bc-create-view`

**Files:** Create `scripts/billing-bc-create-view-playwright-checks.js`; modify `scripts/lib/gap-billing-support.js`.
- [ ] S1. Day sheet "B", then the code, dx and referral search popups, then Continue and Save Bill:
  - One `billing` row and one `billingmaster` row per item: status O, MSP, the PHN, fee = `billingservice` value × units, dx1–3 and the service date.
  - The appointment status becomes B.
  - "Another Bill" opens a clean form.
  - Day sheet "-B" sets status D and unbills the appointment, and is refused once the claim is submitted.
  - A GET to `BillingDeleteWithoutNo` or `BillingDeleteNoAppt` passes `assertRefused`.
  - No `pageerror` occurs on the form.
- [ ] S2. **Expected:** FAIL (cand.: the bill form's `name`; GET delete). Record each failing step.
- [ ] S3, S4.

### Task 19: `billing-bc-teleplan-file`

**Files:** Create `scripts/billing-bc-teleplan-file-playwright-checks.js`; modify `scripts/admin-index-links-playwright-checks.js` (`SKIP_ITEMS` for the GenerateTeleplanFile menu item on BC, citing cand. 187).
- [ ] S1:
  - Simulation lists exactly the owned O claims and writes nothing.
  - Generate writes one `billactivity` row and an MSP file in `HOME_DIR` whose records equal the seeded claims. It moves the claims to submitted and writes `log_teleplantx`.
  - A second simulation lists none of them.
  - The Activity List download returns the file's bytes.
  - A GET with no parameters writes no `billactivity` row.
  - Each menu label matches what its route does (the review reports "Generate" and "Simulate" swapped).
  - Send is never pressed.
- [ ] S2. **Expected:** FAIL at "GET writes nothing" (cand. 187) and at "download" (`DownloadBilling` unmapped, cand.).
- [ ] S3, S4.

### Task 20: BC billing GET refusal and role authorization

**Files:** Create `scripts/get-reject-bc-billing-playwright-checks.js`, which drives each mutator through its page and replays it as GET/HEAD. Its routes are `GenerateTeleplanFile`, `saveQuickBillingBC`, `BillingDeleteWithoutNo`, `BillingDeleteNoAppt`, `formwcb`, `billingTeleplanCorrectionWCB`, `billingAddCode`, `billingEditCode`, `saveAssocAction`, `AddReferralDoc`, `saveBillingPreferencesAction` and `receivePaymentAction`. Also create `scripts/authz-bc-billing-playwright-checks.js`: a role without `_billing` is refused every BC billing page.

Registering these classes in `MutatorActionGetRejectionContractUnitTest` belongs to the fix PR, not here, because it would fail the build.
- [ ] S1–S2. **Expected:** `get-reject-bc-billing` FAILs for every unguarded route; each gets a finding row. `authz-bc-billing` PASSes, or new findings.
- [ ] S3, S4.

### Task 21: `billing-bc-quick-billing`

**Files:** Create `scripts/billing-bc-quick-billing-playwright-checks.js`.
- [ ] S1. Enter two owned patients with codes, then Save:
  - Two `billing` and two `billingmaster` rows: O, MSP, C02, and the quick-billing internal comment.
  - The saved count is shown.
  - A GET passes `assertRefused`.
- [ ] S2. **Expected:** FAIL at Save (cand. 184).
- [ ] S3, S4.

### Task 22: `billing-bc-wcb`

**Files:** Create `scripts/billing-bc-wcb-playwright-checks.js`.
- [ ] S1. Billing Type WCB, then the WCB form with the body part, nature of injury, ICD-9 and fee item lookups, then Save and Save Bill:
  - A `wcb` row with the typed fields, and a `billingmaster` row linked by wcb id.
  - Reopening shows the same values.
  - The claim appears in the simulation, and a correction save updates it.
  - Chart ▸ Forms ▸ BC-WCB opens.
- [ ] S2. **Expected:** FAIL at the entry links (cand.: `wcbForms.jsp`, `viewformwcb.do`, `billingTeleplanCorrectionWCB.jsp`).
- [ ] S3, S4.

### Task 23: `billing-bc-private-bill`

**Files:** Create `scripts/billing-bc-private-bill-playwright-checks.js`.
- [ ] S1. Bill type Private, then Save & Print Receipt, then search for or add a bill-to and Save:
  - Pri `billing` and `billingmaster` rows, and a `bill_recipients` row.
  - The receipt shows the amounts.
  - Receive Payment writes `billing_history` and reduces the balance.
- [ ] S2. **Expected:** FAIL at Receive Payment (finding 164).
- [ ] S3, S4.

### Task 24: BC reports

**Files:** Create `scripts/billing-bc-account-reports-playwright-checks.js` and `scripts/billing-bc-report-center-playwright-checks.js`.
- [ ] S1, account reports:
  - The PDF starts with `%PDF`, and `pdftotext` shows the owned invoices and amounts.
  - CSV rows equal SQL over `billingmaster` for the range and payee.
  - The MSP, WCB, Private and ICBC boxes filter.
  - No HTML error page appears inside a download.
- [ ] S1, report center, entered by clicking from Schedule ▸ Report:
  - The Billed, OB and Flu modes each list the same rows as SQL.
  - Each row link opens the right bill.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 25: BC billing administration

**Files:** Create `scripts/billing-bc-codes-admin-playwright-checks.js` (EXCLUSIVE; marker codes) and `scripts/billing-bc-preferences-playwright-checks.js` (EXCLUSIVE).
- [ ] S1, code administration:
  - Code add, edit and delete, and a fee edit, write `billingservice` and are restored afterwards.
  - Association add, edit and remove write `ctl_servicecodes_dxcodes`, and the bill form then suggests the dx.
  - A duplicate association is refused without saving.
  - Referral add writes `billingreferral`.
- [ ] S1, preferences: the `billing_preferences` row is written and restored, and the next bill form uses its defaults.
- [ ] S2. **Expected:** FAIL at "duplicate refused" and at "Create New Association" (cand.).
- [ ] S3, S4.

### Task 26: BC forms

**Files:** Create `scripts/form-bcar2020-newborn-playwright-checks.js` and `scripts/form-bpmh-playwright-checks.js`. Both use marker registrations, so both are EXCLUSIVE.
- [ ] S1, BC-AR 2020, pages 1–5:
  - The `formBCAR2020`, `formBCAR2020Data` and `formBCAR2020Text` rows share one form id.
  - Moving between pages keeps unsaved data, and reopening shows every page.
  - `BCAR2020_<id>.pdf` starts with `%PDF` and carries the marker for each selected page.
- [ ] S1, BC-NewBorn 2008: save, reopen and print.
- [ ] S1, BPMH:
  - It opens from Chart ▸ Forms.
  - It lists the patient's active drugs and care team.
  - Save writes `formBPMH`, and `method=fetch` restores it.
  - The print PDF names the drugs.
- [ ] S2. **Expected:** BC-AR and NewBorn PASS. BPMH FAILs at "opens from Chart ▸ Forms" (cand. 185).
- [ ] S3, S4.

### Task 27: BC core pass and province reclassification

**Files:** Modify `scripts/playwright-suite.json` (`provinces` corrections) and `docs/ui-tests/deb-install-validation.md` (BC core record).
- [ ] Run `--tier core --province BC` on the BC container, with finding 146's checks marked `expectedFailure`.
- [ ] Classify every failure. A check whose route is Ontario-only gets `provinces: ["ON"]`; a real BC defect gets a finding row.
- [ ] Run the meta-tests; expect 0 failures. Commit.

### Phase 3: P2, Ontario container

### Task 28: `echart-note-verify-appointment-status`

**Files:** Create `scripts/echart-note-verify-appointment-status-playwright-checks.js`.
- [ ] S1:
  - Verify & Sign writes a signed note carrying the verify signature line.
  - `appointment.status` gains S after Sign and V after Verify, and the day-sheet icon matches.
  - A note with no appointment changes no appointment.
  - A back-dated `observation_date` persists and orders the note.
  - A future date is refused with `encounter.futureDate.Msg`.
  - `encounter_type` persists.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 29: `echart-issues-filter`

**Files:** Create `scripts/echart-issues-filter-playwright-checks.js`. Fixtures: the issues from `demo-issue-codes.sql` and two owned notes.
- [ ] S1. Assign issue 250 to one note through CPP ▸ Assign Issues, then Sign & Save:
  - A `casemgmt_issue` row linked through `casemgmt_issue_notes`.
  - The Issues module lists the issue.
  - Clicking it shows only the linked note, and the heading brings every note back.
  - Resolving moves it to Resolved Issues (`resolved=1`).
- [ ] S2. **Expected:** FAIL (cand.: the filter has no opener and the assignment controls are not rendered).
- [ ] S3, S4.

### Task 30: `form-catalog-smoke` and the MH/Annual V2 rows

**Files:** Create `scripts/form-catalog-smoke-playwright-checks.js`. It uses marker registrations, so it is EXCLUSIVE. It is table-driven over the ON forms that have no save/reopen check: Rourke2006, Rourke (original), Letterhead (formConsultant), Lab Req (pre-2007), ADFv2, ALPHA, CESD, CHF, Caregiver, Caregiver-SF36, SF36, Cost Questionnaire, Falls History, HOME FAST, Grip Strength, ImmunAllergies, Intake Information, FDI Disability, FDI Function, Position Hazard, Risk Assessment, Self Efficacy, Self Management, Treatment Preference, Health Passport and Patient Encounter Worksheet. Rourke2020 and the growth charts are Task 31. Also modify `scripts/clinical-forms-save-reopen-playwright-checks.js` to add MH Form 14, MH Form 42, Annual V2 (male and female) and the Mental Health chain.
- [ ] S1. Per form:
  - It opens with no error page, `pageerror` or asset 404.
  - Saving writes one row with the typed value, and reopening shows it.
  - Print gives a page or `%PDF`.
  - The registration is removed afterwards.
- [ ] S2. **Expected:** PASS per form, or one finding per broken form.
- [ ] S3, S4.

### Task 31: `form-rourke2020-growth`

**Files:** Create `scripts/form-rourke2020-growth-playwright-checks.js`. Fixtures: an owned infant patient and marker registrations (EXCLUSIVE).
- [ ] S1:
  - A `formRourke2020`, `formGrowthChart` or `formGrowth0_36` row with the exact values.
  - Reopening shows them on every page.
  - Weight, length and head circumference are imported into `measurements`.
  - Print starts with `%PDF` and `pdftotext` finds the marker.
  - The graph is a non-empty PNG.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 32: `lab-to-flowsheet`

**Files:** Create `scripts/lab-to-flowsheet-playwright-checks.js`.
- [ ] S1. Create Lab with CML 3767 (HbA1c), then Dx 250, then the Diabetes flowsheet and the Health Tracker:
  - A measurement with `type=A1C` and `measurementsExt.lab_no`.
  - The flowsheet A1C row shows the value and date, flags it out of range and links to the lab.
  - An unmapped code does not appear.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 33: `get-reject-consult-config`

**Files:** Create `scripts/get-reject-consult-config-playwright-checks.js`. It snapshots and restores the global flags, so it is EXCLUSIVE.
- [ ] S1:
  - Each consultation config write is driven through its page: AddService, DelService, EnableConRequestResponse, UpdateInstitutionDepartment, UpdateServiceSpecialists.
  - A GET/HEAD replay on owned rows passes `assertRefused`.
  - A throwaway login holding only `_con` r is refused.
- [ ] S2. **Expected:** FAIL per findings 107/108 (they become live-confirmed).
- [ ] S3, S4.

### Task 34: `login-facility-chooser`

**Files:** Create `scripts/login-facility-chooser-playwright-checks.js`. Fixtures: a second Facility, `provider_facility` rows for a throwaway login, and `facility_message` rows.
- [ ] S1:
  - The chooser appears unprompted, listing exactly the provider's facilities.
  - Before choosing, typed schedule and chart URLs return to the chooser, and logout works.
  - The pick writes one `log` row with `facilityId=<id>`.
  - The banner shows only that facility's message.
  - A one-facility login skips the chooser.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 35: `lab-recall-delegate`

**Files:** Create `scripts/lab-recall-delegate-playwright-checks.js`. Fixtures: a throwaway login with `_lab` w, a delegate provider and a synthetic HL7 lab.
- [ ] S1:
  - The `labRecall*` property rows are saved and shown on reopen.
  - Recall appears only once a delegate is set.
  - The message is prefilled with the subject.
  - The saved tickler goes to the delegate at the chosen priority and is linked to the lab.
  - A GET save passes `assertRefused`.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 36: `clinic-address-admin`

**Files:** Create `scripts/clinic-address-admin-playwright-checks.js`. It snapshots and restores the clinic row, so it is EXCLUSIVE.
- [ ] S1:
  - Update writes the clinic row exactly, punctuation included.
  - The Rx preview and the consultation PDF (via `pdftotext`) show the new address, phone and fax.
  - A GET `method=update` passes `assertRefused`.
  - The row is restored byte-exact.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 37: `billing-on-report-center-clerk` and `report-obec-file`

**Files:** Create both scripts.
- [ ] S1, report center, as a receptionist throwaway:
  - The Unsettled, OB and FLU modes each list the same rows as SQL.
  - OB2 shows the owned claim's header and items.
- [ ] S1, OBEC file:
  - One `OBEC01`+HIN(10)+version(2) line per in-window appointment, CR/LF-framed.
  - Out-of-window appointments are excluded.
  - An empty window produces no file.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 38: `dialog-confirmations`

**Files:** Create `scripts/dialog-confirmations-playwright-checks.js`.
- [ ] S1. For appointment delete and cancel, tickler delete, Unbill, document delete and eForm delete:
  - Dismissing the confirm leaves the row unchanged.
  - Accepting changes it.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 39: Finding pins, batch B

**Files:** Modify only existing checks and node tests.

| Finding | Check or test | Pinned assertion |
|---|---|---|
| 154 | `dashboard-display` | Drive the picker; don't type the server's format. |
| 152 | `demographic-measurement-modal.test.js` | Under `TZ=America/Vancouver` at 23:00. |
| 161 | `report-cdm` | A measurement created today, with end date = today. |
| 157 | `dx-registry-quicklist` | Visit the chart registry first; the report adds only its code. |
| 159 | `document-upload` | A replayed additional review with a foreign `extraReviewerId` is stored as the logged-in user. |
| 151 | `rx-med-history` | The same drug prescribed twice shows once in the E-Chart. |
| 166 | `framingham-ukpds-calculator.test.js` | HDL ≥ total cholesterol. |
| 136 | `schedule-views` | The Month week-number link does not 404. |
| 142 | `authz-read-role-matrix` | `ViewTabAlertsRefresh` answers 200 without `_msg`. |
| 143 | `check-demo-additive.sh` | No orphan tickler links. |
| 167 | `gap-encounter-rx-drug-info-on-host` | Rx Info for an unknown DIN still offers a name search. |
| 169 | `double-submit-eform` | The text reads "patient's eForms". |
| 170 | `measurement-stylesheet-delete` | An unused-stylesheet delete step. |
| 177 | `eform-render` | The letterhead `doctor_contact_*` values are non-empty. |

- [ ] S1–S2 per row. **Expected:** each FAILs at its new step with `expectedFailure`, or `todo` in node tests.
- [ ] S3, S4.

### Phase 4: configuration profiles

### Task 40: Runner `--profile`

**Files:** Create `scripts/lib/property-profiles.js`:
- `PROFILES`: `address-lock` (`login_lock=false`), `no-pin` (`mfa.legacy.pin.enable=false`), `multisite` (`multisites=on`, `rma_enabled=true`), `caisi-admin` (`caisi=on`, `caisi.search.workflow=false`), `legacy-contacts` (`NEW_CONTACTS_UI=false`) and `private-consent` (`privateConsentEnabled=true`, with the fixture program in `privateConsentPrograms`). The `multisite` profile also sets `moh_file_management_enabled=true`.
- `applyProfile(name)` writes `/etc/carlos-emr/carlos.properties`, restarts `carlos-emr.service` and waits for `/status`.
- `restoreProfile()` restores the snapshot.

Modify `scripts/run-playwright-suite.js` to add `--profile`, so only checks whose manifest `profile` matches run. Modify `scripts/deb-docker-validation.sh` so that `suite`'s restart detector allows the profile's own restart. Test in `scripts/property-profiles.test.js`.
- [ ] Write the failing tests: `shouldRestoreSnapshot_whenCheckFails`, `shouldRejectUnknownProfile_forTypo`, `shouldSkipWithReason_whenProfileInactive` (the check is reported as skipped, citing the profile, never as passed), and a manifest test that every `profile` value is a key of `PROFILES`.
- [ ] Implement, then on the ON container run `--profile no-pin --tier smoke`. **Expected:** PASS, and properties restored byte-exact afterwards.
- [ ] Commit.

### Task 41: Address-lock and no-PIN profiles

**Files:** Create `scripts/account-lockout-address-mode-playwright-checks.js` (profile `address-lock`). Set `profile: "no-pin"` on a copy of the login smoke entry.
- [ ] S1. Failed logins lock by address, as configured; `admin/UnLock` clears the lock; a second account from the same address follows the documented behaviour.
- [ ] S2. **Expected:** FAIL per finding 162.
- [ ] S3, S4.

### Task 42: Multisite profile

**Files:** Create `scripts/admin-manage-sites-playwright-checks.js`, `scripts/rx-satellite-address-playwright-checks.js`, `scripts/multisite-schedule-visibility-playwright-checks.js` and `scripts/billing-on-moh-files-playwright-checks.js`. Extend `admin-sites-clinic-numbers` with the clinic NBR codes. All use profile `multisite`.
- [ ] S1:
  - A site's create and edit write `site` and `providersite`; Add Appointment offers the site; the schedule shows only the provider's sites.
  - The Rx preview address follows Set Default, and the printed prescription shows it.
  - A `clinic_nbr` add, edit and delete is offered by the ON bill form and stored in `billing_on_cheader1`.
  - View MOH Files moves an owned file from the inbox to the archive, and a GET passes `assertRefused`.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 43: Legacy contacts profile

**Files:** Create `scripts/demographic-relations-legacy-playwright-checks.js` (profile `legacy-contacts`). The BC remittance check is out of scope (#4439).
- [ ] S1, relations:
  - A `relationships` row with the relation, both flags and the creator.
  - It shows on both patients.
  - Delete removes it or flags it.
  - A GET passes `assertRefused`.
- [ ] S2. **Expected:** PASS, or findings.
- [ ] S3, S4.

### Task 44: CAISI admin profile

**Files:** Create `scripts/caisi-admin-authoring-playwright-checks.js`, `scripts/report-caisi-reports-playwright-checks.js` and `scripts/caisi-mode-smoke-playwright-checks.js`. Set `profile: "caisi-admin"` on `admin-messages` and `admin-issue-editor`.
- [ ] S1:
  - A `default_issue` row is added and removed, and a new program-client encounter gets that issue.
  - A facility message created in the editor, with its expiry picked in the popup, shows on the day-sheet banner.
  - CDS-4, MIS and Population each show a report or their documented empty state.
  - Each PMmodule entry point lands on a real page.
- [ ] S2. **Expected:** authoring and reports PASS, or findings. `caisi-mode-smoke` FAILs (cand.: Tiles results). Before relying on it, ask maintainers whether the CAISI client mode stays.
- [ ] S3, S4.

### Phase 5: P3 and hardening

### Task 45: P3 extensions to existing checks

**Files:** Modify existing checks.

| Check | Added coverage |
|---|---|
| `patient-letters-envelopes` | Delete removes only the owned template, and GET is refused. |
| `billing-on-invoice-third-party` | The letterhead logo returns the uploaded bytes; with no logo, no broken image. |
| `eform-apcache-renderer` | Approve downloads `%PDF` with no duplicate `eform_data`, and token replay is refused. |
| `diagnosis-flowsheet` | Deleting a reading moves exactly that reading to `measurementsDeleted`. |
| `antenatal-annual-review-planner` | The risk/checklist editor Save needs `_form` w and POST; the file round-trips and markup renders as text; the file is restored (EXCLUSIVE). |

- [ ] S1–S4 per row.

### Task 46: P3 new small checks

**Files:** Create:
- `scripts/echart-cpp-copy-position-playwright-checks.js`: Copy to current note lands and saves; Position reorders the box.
- `scripts/echart-forms-list-paging-playwright-checks.js`: the Forms heading's paging changes the rows shown.
- `scripts/chart-informed-consent-playwright-checks.js`: the `informedConsent` extension is set and the banner is gone. It runs under profile `private-consent` (Task 40).
- `scripts/echart-pregnancy-episode-playwright-checks.js`: create writes an Episode with SnomedCore 72892002; complete moves it to the past list.

- [ ] S1–S2. **Expected:** Pregnancy FAILs at "create" (cand. 183); the others PASS, or findings.
- [ ] S3, S4.

### Task 47: UI quality

**Files:**
- Create `scripts/responsive-hit-test-playwright-checks.js`: `elementFromPoint` on each primary control at 1280×720 and 1366×768, on the login page, schedule, master record, chart, Rx, inbox, bill form and admin.
- Create `scripts/accessibility-smoke-playwright-checks.js`, which is report-only and adds the `axe-core` dev dependency.
- Create `scripts/i18n-locale-walk-playwright-checks.js`, which runs the page-health engine under fr, es, pl and pt_BR and reports `???key???`.

- [ ] S2, on the ON container. **Expected:** the hit-test passes on Ontario (finding 146 is BC-only, #4439); accessibility reports findings without failing; the i18n walk reports missing keys.
- [ ] S3, S4.

### Task 48: Signal-safe cleanup for the remaining 57 checks

**Files:** Modify the 57 manifest checks that have no signal-safe cleanup, starting with the 11 that assert database rows: move each to `runCheck`, `runWorkflow` or `cleanupOwnedWorkflow`.
- [ ] Write a failing manifest test: every `assertsDatabase: true` check uses a signal-safe entry point.
- [ ] Migrate each check, then run it live, interrupted with SIGINT halfway. **Expected:** no residue (`--residue-audit`).
- [ ] Commit in batches of about 10.

---

## 5. Dead-route candidates (not tasks: one maintenance issue to retire or map them)

The reviews found about 150 routes with no live opener. The main groups are:
- Report designer chain: `report/ViewReportForm*`, `ViewReportFilter`, `ViewReportResult`.
- ClinicalReports: `RunClinicalReport` and related routes.
- ON and BC billing corrections: `BillingCorrection*`.
- BC private statement: `PrivateBillingController` is unmapped.
- `billingShortcutPg*`.
- `adminFlowsheet/ViewFlowsheet*`.
- The antenatal planner: `decision/antenatal/*`.
- The legacy `encounter/ViewIndex2` family, including `report/ViewReportecharthistory`, which is reachable only from `Index2.jsp`.
- About 22 `casemgmt/View*` fragment gates.
- The old provider pages: `Provider/showPersonal`, `Edit*`, `setProviderColour`.
- The PMmodule program and facility managers.

Menu links pointing at unmapped routes are defects, not dead routes, and go to Task 1: `SurveyManager`, `RecommitHSFO`, `/commons/omdDiseaseList.jsp`, `/Pregnancy`, `DownloadBilling` and the WCB entries. The full lists are in the appendix, `2026-10-08-playwright-coverage-gaps-reviews.md`.

## 6. Already covered (routes the measurement missed)

The route measurement under-counts dynamic, relative and property-built callers. The reviews matched about 60 "untouched" routes to existing checks. Examples:
- `encounter/display*` → `echart-navbar-modules`
- payment types → `billing-payment-types`
- RA summary and error reports → `billing-on-ra-import`
- RBT groups → `report-by-template-groups`
- `provider/AddStatus` → `appointment-lifecycle`
- `GroupNoAcl` → `admin-role-management`
- MFA → `login-mfa`

Twenty-odd planned items from `playwright-coverage-plan-2026.08.md` landed under other names (for example `billing-on-ohip-file-cycle`, `echart-note-lifecycle` and `lab-cumulative-requisition`). This plan does not repeat them.

## 7. Task index

| Phase | Tasks | Size | Container | Expected new failing checks |
|---|---|---|---|---|
| 0: harness and static | 1–6 | 2 L, 4 M | none, plus a short ON/BC probe | 0 (finding pins report `todo`) |
| 1: P1 Ontario | 7–17 | 4 L, 7 M | ON | 7, 10, 13, 15 (9 pins); 11, 12, 14, 16 possibly |
| 2: BC | 18–27 | 3 L, 7 M | BC | 18, 19, 20, 21, 22, 23, 25, 26 (BPMH) |
| 3: P2 Ontario | 28–39 | 2 L, 10 M | ON | 29, 33, 39 (14 pins) |
| 4: profiles | 40–44 | 1 L, 4 M | ON | 41, 44 (CAISI client) |
| 5: P3 and hardening | 45–48 | 2 L, 2 M | ON | 46 (Pregnancy) |

Each phase ends with a full `--tier core` run on its container. The residue audit must be clean and every failure must be `known-fail`. The phase's commits go to the branch, and the user reviews the PR before the next phase starts.
