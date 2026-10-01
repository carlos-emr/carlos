# Local duplicate warnings — October 1, 2026

The review page now compares the edited draft with individual statements in the
accessible chart entries. A possible match shows the original chart passage and a
link to its full entry. It updates while typing, leaves approval unchecked, and
never removes or approves a suggestion. No model calls or external requests are
used. Each chart change still requires explicit approval.

The small English alias list recognizes HTN, OA, COPD, T2DM, GP, physio and
follow-up/review wording. Common filler and word order can differ. Written numbers
one through twelve and plural interval units are normalized. All remaining terms
must agree. Numerical signs, comparisons, decimals, dates and intervals remain
significant. This is a bounded text heuristic, not a clinical terminology engine.

## Checks

The matcher has 58 regression cases covering useful matches and deliberate
nonmatches, including:

| Draft | Chart passage | Expected result |
| --- | --- | --- |
| Hypertension | HTN | Warning |
| OA of the left knee | Left knee osteoarthritis | Warning |
| Neurology review in four weeks | Follow up with neurology in 4 weeks | Warning |
| Hypertension | Family history: HTN | No paraphrase warning |
| Asthma | No asthma / asthma ruled out, including wrapped lines | No paraphrase warning |
| Left knee OA | Right knee OA / bilateral knee OA | No paraphrase warning |
| Review in two weeks | Review in four weeks / two months | No paraphrase warning |
| Review on 2026-10-01 | Review on 2026-10-02 | No paraphrase warning |
| Asthma caused cough | Cough caused asthma | No paraphrase warning |

Exact whole entries and unblocked identical statements still match with their
qualifiers intact. Paraphrase matching skips recognized uncertain, family,
conditional, resolved or completed scopes; unknown headings; and compound statements, repeated laterality or
causal relationships that could associate a qualifier with the wrong fact.
Wrapped lines stay together. A changed draft is compared on its current text,
so an old source match does not keep warning about an unrelated edited entry.

The isolated browser suite covers the new warning, its exact quoted passage,
chart-entry navigation, edits, family/negation/laterality cases, and hostile
markup displayed as text. Approval remains unchecked, both suggestions remain
present, and there are no new chart entries or receipts. Existing approval,
replay, CSRF, stale chart/source, draft preservation and modal tests also pass.
Final CI results are recorded in PR #4065.

A final counterexample swapped knee/hip laterality across a comma-separated list.
The matcher now skips compound statements and repeated laterality; regression
cases cover commas, slashes and repeated sides with no conjunction.

## Limits

This does not measure clinical recall or precision. Many paraphrases will still
be missed: the dictionary is deliberately small, qualifiers are conservative,
and English scopes only are recognized. False warnings are possible, especially
with ambiguous abbreviations or complex language. The clinician must compare
both passages. Only accessible history/concerns and active reminders are included.
The backend still blocks exact normalized duplicate text and durable replays;
paraphrase warnings do not add a server-side block.

## Follow-up review

Review added patient-specific document access checks, signed-note filtering and
normal program-role filtering for reminders. A missing assignee role no longer
crashes that filter. Duplicate-save checks now compare complete normalized entries;
reminders also require the same date and assignee. Substrings in negated, family or
laterality-qualified entries cannot block a distinct new fact.

Source validation rejects quotations that omit adjacent wrapped qualifications.
The saved 21 quotations from ten synthetic cases are regression inputs. Regeneration
asks before replacing unsaved suggestions, including edits preserved after saving
another card. Non-English feature strings remain English with translation TODOs.

Follow-up validation completed:

- 58 matcher cases and 215 Python tests passed.
- All 127 targeted Java tests are covered by passing results. The first run found
  two test defects (an old exception expectation and a fixture missing its ID);
  the affected 17 tests passed after correction. Compilation initially caught a
  collection matcher type error, which was also corrected.
- All 17 synthetic browser scenarios passed, including confirmation after a draft
  survives saving another card. Locale key and UTF-8 checks passed.
- Offline replay verified unchanged writer/reviewer inputs and identical outputs
  for all ten saved cases: 21 proposals, zero new model calls.
- The updated isolated CARLOS trial passed the actual eChart/modal walkthrough on
  empty synthetic patient AIFACT005. Its 4,537-word attachment produced five
  suggestions; its 561-word attachment produced two. Both were cache hits. Original
  documents, source highlights, defaults and navigation worked; clinical entry and
  receipt counts stayed zero, and the chart lock was released.
- Independent review of the complete PR and final corrections found no remaining
  MUST or worthwhile unresolved SHOULD findings. The review covered all 60 fresh
  automated comments. Protected workflow/database paths were not changed.

The earlier successful CI applies to `89e845c71`. CI for the new review-fix commit
must complete after its push; no manual CI rerun was requested.
