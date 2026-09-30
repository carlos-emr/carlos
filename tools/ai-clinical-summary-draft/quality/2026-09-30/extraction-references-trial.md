# Chart extraction with source references — September 30, 2026

The subsequent [acceptance check](extraction-acceptance.md) adds ten-document
verification, cleaner numbered plans, mixed-plan filtering, related-suggestion
comparisons and safer surgery-relative dates. It supersedes the limitations fixed
there; the measurements below remain the earlier development results.

The isolated CARLOS trial at <http://localhost:8082/carlos/> now uses
**Qwen3.5-35B-A3B on Parasail through OpenRouter** for chart proposals. Search for
**FAKE-EMPTY-CHART**, chart **AIFACT005**, open eChart and choose **Review chart updates**.
The measured outputs are cached for inexpensive repeat review. No entries were approved.

## Results

| Document | History suggestions | Follow-up reminders | Total |
| --- | ---: | ---: | ---: |
| 43: 37-note record, 4,537 words | 3 | 2 | 5 |
| 44: longest note, 561 words | 2 | 0 | 2 |
| 40: NHSSYN001 | 1 | 1 | 2 |
| 41: NHSSYN002 | 1 | 0 | 1 |
| 42: NHSSYN003 | 1 | 1 | 2 |

All five responses passed the unchanged public evidence/output validator. The
large document includes past hypertension/osteoarthritis, two related diagnostic
impressions, neurology follow-up in two weeks, and GP follow-up in seven days.
The two impressions overlap but preserve different source qualifications (the fall
and the contribution of hyponatremia/dehydration); they still need clinician review.
These counts are suggestions, not a completeness score or a count of unique diagnoses.

NHSSYN001 includes the RCVS history and four-week neurology follow-up. NHSSYN002
includes the knee osteoarthritis/postoperative history; its inpatient review for
possible discharge is deliberately excluded from outpatient reminders. NHSSYN003
includes pneumomediastinum history and respiratory outpatient follow-up. Its quoted
follow-up retains the next source list marker (`4.`), a formatting limitation in
sentence segmentation; the passage remains verbatim and specifies no interval.

## What changed

- The model returns inclusive source-segment IDs and a proposed kind. The host
  copies the corresponding original substring. It no longer asks the hosted model
  to reproduce spelling, whitespace or quotations.
- Source segments preserve all original characters and can separate sentences on
  the same line. This lets the GP follow-up be quoted separately from its neighbouring
  prescription, while the full source remains available to both model passes.
- Whitespace-equivalent repeated selections of the same kind are removed locally.
  Recognized family-history sections and certain explicit family relationships are
  excluded from patient-history candidates. These are bounded lexical checks.
- A separate review sees the complete original document and every exact candidate
  quotation. Every candidate needs a valid keep/drop decision before output is
  returned. Missing, duplicate or unknown decisions fail the request.
- Explicit adjacent past-history headings are retained so CARLOS suggests
  **Medical History**, rather than interpreting bare bullets as current concerns.
- The isolated gateway caches validated results by source/contract, model settings
  and extractor implementation. It checks disclosure eligibility before cache access,
  revalidates cached evidence and uses each caller's fresh request ID.

The Java contract, 20-proposal limit, authorization, chart comparison and per-entry
approval remain unchanged. Extraction uses the original document, independently of
the evolving single-document summary output. The shared approach is host-resolved
references, and both operations use the same configured hosted model/provider.

## Browser verification

The [browser result](extraction-references-browser.json) records both empty-chart
attachments through the native eChart modal. It checked original downloads and
source hashes, every proposal's source highlighting, the proposed dates, unchecked
approvals, Back/Close navigation, unchanged tab count and released editing locks.
The final [screenshot](extraction-references-modal.png) shows the GP follow-up.

- Past hypertension/osteoarthritis defaults to **Medical History**.
- Current impressions default to **Concerns**.
- Neurology defaults to **2026-01-23**, GP to **2026-01-16**, anchored to the
  attachment's **2026-01-09** date. These are historical synthetic-document dates.
- Both reminders select the logged-in test clinician.
- Cached generation/display took **1,520 ms** and **1,205 ms** respectively.
- Notes, reminders, drugs, allergies and approval receipts all remained at **zero**
  on AIFACT005. This trial exercised generation and review, not approval writes.

The three original NHS documents were checked through the actual gateway extraction
operation; they were not part of this final two-document browser run. Their accepted
outputs are also in the private trial cache.

## Verification and cost

All **206 Python tests passed**, including 1,602-corpus-note source reconstruction,
source disclosure checks, invalid ranges, exact CRLF/typo preservation, sentence
selection, family-history suppression, historical headings, duplicate selections,
full-source review, malformed/incomplete decisions, and cache validation. The browser
script passed its syntax check and the live replay passed. No Java code changed.

```bash
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
CHART_EMPTY_FIXTURE=target/nhs-chart-update-morning/empty-chart-fixture.json \
  python3 target/nhs-chart-update-morning/run-browser.py \
  tools/ai-clinical-summary-draft/browser/empty_chart_check.cjs
```

[Measured model outputs and all development attempts](extraction-references-results.json)
record **24 completed model calls**, with total reported cost **$0.02623215** for
this extraction iteration, including the three original patients. The final writers
were reused from attempt 4; only reviews were repeated after the heading/review-input
adjustments. Browser replays made no model calls.

## Limits found during iteration

Earlier attempts exposed exact duplicate selections, a review request exceeding its
byte budget, and inconsistent review decisions when the reviewer had to resolve long
source ranges itself. The final reviewer receives full candidate quotations alongside
the unnumbered complete source, retaining context within the existing request budget.
Rejected and poorer attempts remain in the artifact rather than being counted as
successful final results.

Model review remains fallible: one development review accepted repeated diagnoses
mixed with treatment plans. Mechanical citation validation cannot establish clinical
eligibility, semantic deduplication or completeness. The final outputs were manually
compared with these five synthetic sources, but this is a small development trial.
Unknown headings, abbreviations, fragmented sentences, conflicting diagnoses and
partially overlapping history still require clinician judgment. No autonomous chart
updates or new clinical-completeness claims are introduced.
