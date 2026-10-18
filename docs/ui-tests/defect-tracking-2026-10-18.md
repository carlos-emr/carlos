# Defect tracking audit — 18 October 2026

All **28 findings** in this coverage campaign now have a specific GitHub issue:
**21 previously untracked findings are covered by 13 new tickets**, and seven
findings reuse existing open tickets. Four of the existing tickets have open,
unmerged PRs targeting `release/2026.08`. No existing issue was duplicated,
closed or marked fixed, and no application fix was applied.

The scope is findings 284–308 plus independently reconfirmed findings 82, 139
and 144. Of these, 27 were reproduced live; 287 remains source-confirmed and
blocked by 285. The passing Rx Qty/price subcase of historical finding 99 is
not a new defect and was not refiled. Finding numbers below belong to the local
coverage register, not to GitHub's issue-number sequence.

The tests, including passing checks, remain saved on local branch
`codex/ui-workflow-coverage-2026-08`, based on `release/2026.08` at
`a7611d07b4ae9b237b4a5d252fa938e09208eb85`. This branch has not been pushed.
New tickets state this explicitly and include test names and run commands;
they do not link to unpublished commits as though those commits were online.

## Existing tracking retained

| Finding | Specific issue | Matching PR | Audit result |
|---|---|---|---|
| 82 | [#4517](https://github.com/carlos-emr/carlos/issues/4517) — [Bug] Saving a patient set twice duplicates its membership | No matching active fix found | Same symptom and affected path; reuse ticket. |
| 139 | [#4542](https://github.com/carlos-emr/carlos/issues/4542) — [Bug] Deleting a pharmacy with link history logs "UNKNOWN PHARMACY TO UNLINK" at ERROR | No matching active fix found | Same symptom and affected path; reuse ticket. |
| 144 | [#4543](https://github.com/carlos-emr/carlos/issues/4543) — [Security] Rx debug lines logged at ERROR, one with prescription text | No matching active fix found | Same symptom and affected path; reuse ticket. |
| 294 | [#4929](https://github.com/carlos-emr/carlos/issues/4929) — Manage Letters shows raw message keys instead of its title, labels, column headings and buttons | [#4932](https://github.com/carlos-emr/carlos/pull/4932) — open, unmerged | Same symptom and affected path; reuse ticket. |
| 305 | [#4799](https://github.com/carlos-emr/carlos/issues/4799) — A prescription pick-up time between 12:00 and 12:59 is saved twelve hours early (12:30 becomes 00:30) | [#4800](https://github.com/carlos-emr/carlos/pull/4800) — open, unmerged | Same symptom and affected path; reuse ticket. |
| 306 | [#4861](https://github.com/carlos-emr/carlos/issues/4861) — Rx static script: a pick-up date saved without a time prints "12:00 a.m." | [#4862](https://github.com/carlos-emr/carlos/pull/4862) — open, unmerged | Covers both static-script and drug-record date-only display. |
| 307 | [#4915](https://github.com/carlos-emr/carlos/issues/4915) — Rx drug search: "None found" can be picked, and the staging messages are English-only | [#4959](https://github.com/carlos-emr/carlos/pull/4959) — open, unmerged | Same symptom and affected path; reuse ticket. |

PR #4862 explicitly includes `DisplayRxRecord.jsp` as well as the static script,
so the strengthened two-page assertion for finding 306 needs no additional
ticket. A pending PR is tracking, not evidence of a fix in the validated build.

## New tickets and grouping

| Findings | Ticket | Grouping rationale |
|---|---|---|
| 284, 285, 287, 288, 290 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) — RH workflow: broken form/injection links, empty injection panel and clinical data in error logs | One RH clinical workflow with blocking entry/render/save dependencies; the entity-conversion failure also causes the clinical-data log leak. 287 is explicitly source-only. |
| 286, 289, 293 | [#4993](https://github.com/carlos-emr/carlos/issues/4993) — Ticklers: status changes leave stale list controls/results and Erase Completely keeps tasks in Deleted | Related status, opener refresh and deletion tasks across the main and patient tickler queues; each has its own failing assertion. |
| 291 | [#4994](https://github.com/carlos-emr/carlos/issues/4994) — Waiting-list booking loses the patient after Next Month or changing the calendar provider | Next Month and provider switching omit the same patient parameters. |
| 292, 295 | [#4996](https://github.com/carlos-emr/carlos/issues/4996) — Rx favourite selection loses saved multiline directions and PRN on the prescribing card | Two saved clinical values are lost at the same favourite-to-card handoff. |
| 296 | [#4997](https://github.com/carlos-emr/carlos/issues/4997) — Episode editor fails to reopen descriptions containing an apostrophe | A focused workflow/action with a distinct failure and acceptance criteria. |
| 297 | [#4999](https://github.com/carlos-emr/carlos/issues/4999) — Overdue consultation Add Tickler opens a task with no patient or suggested message | A focused workflow/action with a distinct failure and acceptance criteria. |
| 298 | [#5000](https://github.com/carlos-emr/carlos/issues/5000) — Rx Indication: choosing Patient Dx Registry puts the description in the ICD9 code field | A focused workflow/action with a distinct failure and acceptance criteria. |
| 299 | [#5001](https://github.com/carlos-emr/carlos/issues/5001) — Rx Hide CPP leaves a false checked state and no error when the save is refused | A focused workflow/action with a distinct failure and acceptance criteria. |
| 300 | [#5002](https://github.com/carlos-emr/carlos/issues/5002) — Rx optional pickup fields show invalid-format alerts when intentionally cleared | A focused workflow/action with a distinct failure and acceptance criteria. |
| 301, 302 | [#5003](https://github.com/carlos-emr/carlos/issues/5003) — Rx input validation: impossible written dates are committed and non-numeric inline doses return HTTP 500 | Impossible dates and non-numeric inline bounds are independent cases of Rx input validation; all three checks remain separate. |
| 303 | [#5004](https://github.com/carlos-emr/carlos/issues/5004) — Rx timeline: clearing all medications checks and graphs every product again | A focused workflow/action with a distinct failure and acceptance criteria. |
| 304 | [#5005](https://github.com/carlos-emr/carlos/issues/5005) — Rx correction after dismissing Save And Print preview leaves both old and new doses active | A focused workflow/action with a distinct failure and acceptance criteria. |
| 308 | [#5006](https://github.com/carlos-emr/carlos/issues/5006) — Rx Info missing-DIN fallback emits DrugRef NoResultException errors for an ordinary empty lookup | A focused workflow/action with a distinct failure and acceptance criteria. |

The optional-pickup clearing ticket links the existing noon-parsing and date-only
display tickets as related work; those existing fixes do not cover clearing the
optional controls. The preview-correction ticket is distinct from replaying an
identical save or ReRx: those controls pass while editing an acknowledged card
still leaves two active doses. The DrugRef ticket preserves the successful Info
fallback and addresses the newly asserted server exception separately.

## Complete finding-to-test map

| Finding | Issue | Verification | Executable evidence |
|---|---|---|---|
| 82 | [#4517](https://github.com/carlos-emr/carlos/issues/4517) | Live | `patient-set-repeat-save` |
| 139 | [#4542](https://github.com/carlos-emr/carlos/issues/4542) | Live | `pharmacy-editor-workflow; current-boot server-log audit` |
| 144 | [#4543](https://github.com/carlos-emr/carlos/issues/4543) | Live | `Rx selected workflows; current-boot server-log audit` |
| 284 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) | Live | `rh-workflow-queue` |
| 285 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) | Live | `rh-injection-workflow` |
| 286 | [#4993](https://github.com/carlos-emr/carlos/issues/4993) | Live | `tickler-bulk-state-actions` |
| 287 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) | Source only; blocked by 285 | `rh-injection-workflow (downstream save assertion, blocked by 285)` |
| 288 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) | Live | `rh-injection-existing` |
| 289 | [#4993](https://github.com/carlos-emr/carlos/issues/4993) | Live | `tickler-bulk-actions` |
| 290 | [#4992](https://github.com/carlos-emr/carlos/issues/4992) | Live | `rh-injection-log-privacy` |
| 291 | [#4994](https://github.com/carlos-emr/carlos/issues/4994) | Live | `waiting-list-booking-navigation`; `waiting-list-booking-provider` |
| 292 | [#4996](https://github.com/carlos-emr/carlos/issues/4996) | Live | `rx-favourite-multiline-directions` |
| 293 | [#4993](https://github.com/carlos-emr/carlos/issues/4993) | Live | `tickler-patient-erase` |
| 294 | [#4929](https://github.com/carlos-emr/carlos/issues/4929) | Live | `patient-letter-manager-labels` |
| 295 | [#4996](https://github.com/carlos-emr/carlos/issues/4996) | Live | `rx-favourite-dose-roundtrip` |
| 296 | [#4997](https://github.com/carlos-emr/carlos/issues/4997) | Live | `episode-literal-description` |
| 297 | [#4999](https://github.com/carlos-emr/carlos/issues/4999) | Live | `consultation-overdue-tickler` |
| 298 | [#5000](https://github.com/carlos-emr/carlos/issues/5000) | Live | `rx-indication-patient-registry` |
| 299 | [#5001](https://github.com/carlos-emr/carlos/issues/5001) | Live | `rx-cpp-visibility-save-refusal` |
| 300 | [#5002](https://github.com/carlos-emr/carlos/issues/5002) | Live | `rx-renewal-pickup` |
| 301 | [#5003](https://github.com/carlos-emr/carlos/issues/5003) | Live | `rx-written-date-impossible-correction` |
| 302 | [#5003](https://github.com/carlos-emr/carlos/issues/5003) | Live | `rx-inline-minimum-validation`; `rx-inline-maximum-validation` |
| 303 | [#5004](https://github.com/carlos-emr/carlos/issues/5004) | Live | `rx-timeline-clear-selection` |
| 304 | [#5005](https://github.com/carlos-emr/carlos/issues/5005) | Live | `rx-preview-close-return-edit`; `rx-preview-escape-return-edit` |
| 305 | [#4799](https://github.com/carlos-emr/carlos/issues/4799) | Live | `rx-card-options` |
| 306 | [#4861](https://github.com/carlos-emr/carlos/issues/4861) | Live | `rx-static-script-pickup` |
| 307 | [#4915](https://github.com/carlos-emr/carlos/issues/4915) | Live | `rx-search-keyboard` |
| 308 | [#5006](https://github.com/carlos-emr/carlos/issues/5006) | Live | `gap-encounter-rx-drug-info-on-host` |

## Search record and adjacent work

The audit used **47 targeted issue searches and 50 targeted PR searches**,
covering open and closed results, followed by direct reads of matching tickets,
PR descriptions and affected-path details. Searches used the workflow names,
symptoms and source symbols, including `RHWorkFlow`, `addRhInjection`,
`PreventionData`, `Erase Completely`, `ticklerEdit.jsp`, `scheduleflipview.jsp`,
`PRN`, favourite/favorite, `episodeForm.jsp`, `SelectReason`, `Hide CPP`, pickup,
written dates, `updatePropertyLocked`, `chartDrugProfile`, acknowledged preview,
`getDrugIdFromDIN`, `ManageLetters`, and the existing issue numbers. The evidence
archive retains query strings and result-number/title lists. Searches are a
dated audit, not a guarantee about future tickets or PRs.

Adjacent work was reviewed rather than assumed to cover a matching module:

- #3798 / merged #3783 concern RH save ownership and failure handling; #3047 /
  #2698 concern identifier logging, not this full-entity exception leak.
- Merged #604 concerns the encounter tickler sidebar; #3322 concerns schedule
  navigation. #2152 covers spacing and consultation window behavior, not the
  missing patient/message handoff or the new task-list failures.
- #4142 concerns waiting-list entry points; #4811 shared session state. The
  reproduced booking failure instead drops patient parameters during navigation.
- Closed #4041 and #4177 concern missing favourites/Add to Favorites failures,
  not successful favourite selection changing directions or PRN.
- #3687 and closed #2164 concern episode authorization/other encoding paths,
  not the unescaped episode description in this JSP.
- Closed #196 was an unverified dosage-review candidate referring to a different
  file. Merged #3908 validates legacy dates; the new invalid-date check exercises
  the modern prescribing card.
- Closed #3503/#3334 and merged #3908 concern save replay and ReRx. The new
  accepted-preview correction is an intentional edit with different directions.
- Closed #4554 / #4705 restore Info fallback; #2331 covers CARLOS DrugrefUtil
  debug logs. Neither covers the bundled DrugRef missing-result exception.

Every new issue was read back after creation: its title/body matched the saved
draft and its state was open. The seven reused tickets and four matching PRs
were also read directly. Unrelated concurrent additions #4995 and #4998 were
checked and do not duplicate these findings.

## Validation boundary

The installed Ubuntu 26.04.1 packages were built from the pinned target source.
The 94 selected Rx checks have 79 passes and 15 exact known failures; all eight
fixture marker counts are zero and package health passes. The independent
server-error audit still fails on four signatures mapped to findings 302, 307
and 308, with no new suppression. Passing browser checks do not clear those
defects. The development debs omit the saved-eForm renderer; no external
administration workflows, real pharmacy delivery or physical printing are
claimed as covered.

See the [validation report](ui-path-coverage-2026-10-18.md),
[Rx action matrix](rx-workflow-coverage-2026-10-18.md),
[31-round ledger](workflow-expansion-rounds-2026-10-18.md), and
[complete application findings](app-findings-log.md).
