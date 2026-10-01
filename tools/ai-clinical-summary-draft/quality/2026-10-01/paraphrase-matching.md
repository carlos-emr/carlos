# Local duplicate warnings — October 1, 2026

The review page now compares the edited draft with individual statements in the
accessible chart entries. A possible match shows the original chart passage and a
link to its full entry. It updates while typing, leaves approval unchecked, and
never removes or approves a suggestion. No model calls or external requests are
used. Extraction, source highlighting and server save protections are unchanged.

The small English alias list recognizes HTN, OA, COPD, T2DM, GP, physio and
follow-up/review wording. Common filler and word order can differ. Written numbers
one through twelve and plural interval units are normalized. All remaining terms
must agree. Numerical signs, comparisons, decimals, dates and intervals remain
significant. This is a bounded text heuristic, not a clinical terminology engine.

## Checks

The matcher has 48 regression cases covering useful matches and deliberate
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
conditional, resolved or completed scopes; unknown headings; and conjunctions or
causal relationships that could associate a qualifier with the wrong fact.
Wrapped lines stay together. A changed draft is compared on its current text,
so an old source match does not keep warning about an unrelated edited entry.

The isolated browser suite covers the new warning, its exact quoted passage,
chart-entry navigation, edits, family/negation/laterality cases, and hostile
markup displayed as text. Approval remains unchecked, both suggestions remain
present, and there are no new chart entries or receipts. Existing approval,
replay, CSRF, stale chart/source, draft preservation and modal tests also pass.
Final CI results are recorded in PR #4065.

## Limits

This does not measure clinical recall or precision. Many paraphrases will still
be missed: the dictionary is deliberately small, qualifiers are conservative,
and English scopes only are recognized. False warnings are possible, especially
with ambiguous abbreviations or complex language. The clinician must compare
both passages. Only accessible history/concerns and active reminders are included.
The backend still blocks exact normalized duplicate text and durable replays;
paraphrase warnings do not add a server-side block.
