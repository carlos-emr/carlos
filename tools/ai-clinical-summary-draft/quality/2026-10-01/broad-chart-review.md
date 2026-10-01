# Broad chart extraction — October 1, 2026

The original source now supplies suggestions across the chart, with a suggested
section and exact quotation. No chart data is sent to the model for comparison.
The shared single-document agent transport is reused; this extraction does not
consume generated summary prose.

## Synthetic model trial

The existing AIFACT005 attachments were reused, with no new patient or source selection:

| Attachment | Words | Suggestions |
| --- | ---: | ---: |
| Document 43, NHSSYN005 compilation of 37 notes | 4,537 | 88 |
| Document 44, longest NHSSYN005 clerking note | 561 | 34 |

The long result contains 3 medical-history, 18 findings/care-plan, 7 social-history,
4 family-history, 5 risk/safety, 20 medication, 3 allergy, 2 demographic, 24 care-plan
reminder notes and 2 outpatient follow-up items. The shorter result contains 2 medical-history, 19 findings, 6 social,
3 family, 2 medication, 1 allergy and 1 care-reminder item. These counts include
overlapping facts and historical observations; they are not counts of distinct
current diagnoses or of records that should automatically be added.

The earlier broad reviewer falsely treated neighbouring social-history bullets as
if the first bullet contained all of them. Model-based duplicate suppression was
removed from the review instructions. The host removes exact repeated quotations
and identical heading variants. Local advisory comparisons and clinician review
handle overlap; some duplicate suggestions remain. Care-plan facts use the CPP Reminders section
when appropriate, which creates a reviewed note, not a timed tickler. Some
administrative plan text and mixed passages still need clinician dismissal or editing. Source spelling, negatives,
uncertainty, family relationships, conflicting doses and historical plans stay intact.

The selector's cached outputs were reused while extraction/review logic changed.
The long source required two review batches; each retained the entire source.
All candidate decisions are required before a result is accepted. The original strict Java source boundary check initially
rejected 15 excerpts with adjacent context missing. Extraction now retains those
negations and note headers; conditional discharge follow-up is not detached from
its condition as a tickler. All 122 final source quotations pass the same unchanged
boundary probe. Oversized
unstructured fallback sections are skipped without truncating source quotations.

[Saved model evidence](broad-chart-results.json) includes both sources and final outputs.
The broad-extraction experiments received 19 billable provider responses reporting
**US$0.06287430** in total, including rejected intermediate outputs. One short-document
review returned a provider error with no reported cost; no partial result was accepted.
Its retry succeeded. The two final outputs are cached for trial replay.

## Review and saving

All seven CPP note sections use the native signed-note transaction, source link,
editing lock and approval receipt. Section access is rechecked on every save.
Identical text in Family history cannot block a Medical history entry; comparison
entries retain and display their actual section membership.

Current-chart comparison additionally displays authorized current medications,
active allergies, measurements and prevention records through their normal managers.
Restricted records and other modules remain outside this comparison. Text matching
is advisory and cannot establish clinical equivalence or complete reconciliation.

Medication and allergy items use **dedicated-record review**, with no note-save
fallback. Their embedded forms are disabled: both landing pages replace shared Rx
session patient/stash state and could disrupt an existing prescription tab. Use the
corresponding normal form from eChart. Completing that embedded workflow safely is
blocked on patient-bound native prescription/allergy navigation and mutation paths.
Closing an item explicitly says it did not save a record.

Prevention and demographic items can open their normal patient-specific forms in a
nested modal with the quoted source alongside. The forms retain their native saves;
the review does not infer structured values, drug selections, doses or allergy codes.
Closing asks about unsaved edits. Tracking in-flight native AJAX saves is not added.
Measurements/results remain note facts because the legacy measurement editor also
uses shared eChart patient state.

## Verification

- 149 targeted Java tests passed, including real H2 saves to all seven CPP sections
  with issue, source link, hash audit and receipt, plus rollback/replay/access checks.
- 229 Python tests passed, including full-source review batching, complete decisions,
  source/list boundaries, social relatives, oversized inventory fallback and routing.
- 18 isolated browser scenarios passed. They exercise source highlighting, section
  comparison, native-review cards without direct writes, native modal patient fields,
  unsaved-close confirmation, all seven destination choices, approval, CSRF, replay,
  stale chart/source and preservation of other drafts.
- Locale key parity, JavaScript syntax and whitespace checks passed.
- The final 11 Java proposal tests passed, including every saved quotation from
  these two broad results and the earlier ten-document acceptance set. This adds
  one test to the earlier 149-test suite; the other Java production paths are unchanged.
- The [actual eChart replay](broad-chart-browser.json) passed on the isolated 8082
  trial: 88/34 cached suggestions, all exact source highlights, section/date/assignee
  defaults, unchecked approvals, navigation, native demographic form patient 3051,
  native close confirmation, no prescription/allergy iframe launch, and lock release.
  Notes, ticklers, drugs, allergies and approval receipts remained zero. No live model
  calls were needed by the walkthrough.
- The first live attempt started before Tomcat was ready. Two test-harness issues
  (waiting for the native frame's navigation and using the harness's sole dialog
  handler) were corrected. Only the abandoned synthetic test lock was removed,
  guarded by its exact row, patient, provider, note ID and acquisition time.
- Final independent review, commit and CI status are tracked in
  [PR #4065](https://github.com/carlos-emr/carlos/pull/4065) and the local supervisor ledger.

These are development fixtures and software checks, not clinical validation or a
precision/recall measurement. The feature remains off by default and each note or
reminder requires individual clinician approval. The hosted trial accepts only its
exact allowlisted synthetic sources. No production deployment or real-patient model
inference is authorized by this evidence.

## Try it

Open <http://localhost:8082/carlos/>, search for **FAKE-EMPTY-CHART**
(chart number **AIFACT005**, demographic **3051**), open eChart and choose
**Review chart updates**. Select the **37-note record / 4,537 words** attachment
for the broad trial. The 561-word clerking note remains available for comparison.
Only the isolated trial and its loopback synthetic gateway were updated; shared
port 8080 and production were not changed.
