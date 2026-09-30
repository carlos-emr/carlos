# Extraction acceptance check — September 30, 2026

This revision is intended for a **clinician-reviewed synthetic trial**. The stopping
standard is useful, source-preserving suggestions, no known unsafe default in the
tested cases, working review/approval controls, and passing automated checks. It is
not certification of clinical completeness or suitability for unattended chart changes.

## Additional fixes

- Numbered inline plans retain the correct number with each item. The respiratory
  follow-up no longer includes the next instruction's `4.` marker. Titles, initials
  and decimal measurements are preserved, and all 1,602 corpus notes reconstruct
  exactly from their segments.
- A ten-document test exposed a model-approved orthopedic reminder containing the
  whole plan, including inpatient care and a histology order. The host now separates
  clearly independent follow-up items from unqualified lists before model review.
  Shared conditions or dependencies prevent splitting; ambiguous candidates are
  omitted. The corrected reminder contains only the orthopedic outpatient follow-up.
- Unknown event dates no longer become document-relative defaults: “GP follow-up
  6 weeks post-surgery” stays blank. Java regression tests cover post-surgery,
  postoperative, postop, following surgery and since discharge wording.
- Related suggestion wording is flagged locally, with expandable peer drafts and
  their completion status. Clinicians can compare partially overlapping impressions
  without silently losing their different causal qualifications. This is a word
  comparison, not semantic deduplication or a diagnosis matcher.

## Live model cases

The two empty-chart attachments and the original three NHS documents were followed
by five additional cases selected **before generation**: the longest complete note
containing follow-up wording in each of NHSSYN006–010. All source texts passed the
gateway's committed-synthetic-source checks.

| Case | Final suggestions | Manual source review |
| --- | ---: | --- |
| 37-note NHSSYN005 compilation | 5 | Past history, two related impressions, neurology and GP follow-up |
| Longest NHSSYN005 note | 2 | Past history and current impression |
| NHSSYN001 | 2 | RCVS history and four-week neurology follow-up |
| NHSSYN002 | 1 | Knee OA/postoperative history; inpatient discharge review excluded |
| NHSSYN003 | 2 | Pneumomediastinum and respiratory outpatient follow-up; clean list boundary |
| NHSSYN006 | 3 | Hip OA, outpatient physiotherapy and GP follow-up; surgery-relative timing preserved |
| NHSSYN007 | 1 | Pneumonia/sepsis; paperwork for GP follow-up not treated as an appointment request |
| NHSSYN008 | 2 | CTEPH and two-week pulmonary clinic follow-up |
| NHSSYN009 | 1 | Scheduled outpatient physiotherapy; source date preserved verbatim |
| NHSSYN010 | 2 | Hip OA and six-week orthopedic follow-up; treatment/order list removed |

All ten final responses passed source/output validation. The final quotes were
compared with their sources. These are development cases; their counts do not
measure clinical completeness. A missing explicit appointment instruction can yield
no reminder, and ambiguous/unsupported date formats can still require manual entry.

[Model evidence](extraction-acceptance-results.json) retains the initial failure
and corrected results. This round used **21 completed model calls**, costing
**$0.00937435** according to provider usage. The unchanged writer selections and
nine unchanged reviews were reused; only the affected orthopedic review was rerun.
The final outputs are cached for repeat use in the isolated trial.

## Verification

- **115 targeted Java tests passed**, including date suggestions, authorization,
  proposal validation, review persistence, receipts and transaction integration.
- **211 Python tests passed**, including source boundaries, mixed-plan separation,
  conditional-plan omission, exact evidence, disclosure checks, review completeness
  and cache validation.
- **15 isolated browser scenarios passed**, covering related-draft comparison,
  safe text rendering, edits, approvals/replay, duplicate saves, CSRF, stale source,
  stale chart, modal navigation and draft preservation.
- Translation key parity passed; the new comparison notice has all five existing
  locale translations. JavaScript syntax and diff checks passed.
- The deployed [CARLOS browser replay](extraction-acceptance-browser.json) passed
  for both empty-chart attachments: exact source/highlighting, related-impression
  comparisons, Medical History/Concerns defaults, January 23/January 16 reminder
  dates, clinician assignee, unchecked approvals, Back/Close and released locks.
  [The comparison screenshot](extraction-related-suggestions.png) shows the notice.
- Notes, reminders, drugs, allergies and approval receipts on AIFACT005 remained
  at **zero**. The real replay performed no approvals and made no model calls.

The isolated instance at <http://localhost:8082/carlos/> is updated. Search for
**FAKE-EMPTY-CHART**, open eChart, then **Review chart updates**. Deployment changed
only the isolated trial and its loopback hosted gateway.

Full CI results are attached to the final revision in PR #4065. This report records
local and live checks separately so a pending CI run cannot be mistaken for a pass.

## Remaining scope

The feature remains disabled by default, requires individual clinician approval,
and the bundled hosted gateway accepts verified synthetic data only. Current-chart
comparison covers accessible history/concerns and active reminders using normalized
text. Neither it nor the related-suggestion warning proves clinical equivalence.
Source inconsistencies, unsupported date formats, omissions and model classification
errors remain possible. The original document and chart must stay part of review;
there is no new autonomous write path or claim of clinical validation.
