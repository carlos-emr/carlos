# Summary quality experiments — 2026-09-15

This is an exploratory synthetic-fixture comparison, not a clinical validation or
an accuracy benchmark. The previous working browser draft processed NHSSYN001 in
five independent 10,000-byte passes: 98 claims, 20 evidence sources, 65.684 seconds
cold and 1.028 seconds cached. Review found repeated introductions, identity prose
with inconsistent age/DOB, and inadequate synthesis across the record.

## Current experimental choice

Qwen `qwen/qwen3.5-397b-a17b` via Venice, temperature 0.2, reasoning off,
50,000 serialized request bytes and up to 16,384 output tokens per pass. The whole
NHSSYN001 clinical input fits in one pass. These are hosted models; this choice
does not imply the model fits a laptop GPU. The application still supports smaller
Qwen models and local Ollama, with the latter retaining its smaller request budget.

The prompt asks for one clinical course, dated changes, all distinct meaningful
facts and qualifiers, explicit conflicting regimens, and no repeated identity or
presentation prose. Original evidence and citation checks remain separate from
model prose. Longer inputs are still split without source-text truncation, and
exhausted outputs are discarded and retried with smaller inputs.

The selected direct trial took **58.9 seconds**, generated **32 claims** from all
19 supplied clinical notes, and passed structural validation. It presented the
headache once, retained social/family history, laboratory abnormalities and their
management, and explicitly contrasted discharge nimodipine with the amlodipine
recorded in the GP communication. Claim count is not a measure of factual recall.

**Known limitations in that same output:** it described planned discharge as
completed, attached some observation times too broadly, did not retain every
negative finding or follow-up qualifier, and did not flag the GP contact's
inconsistent note/body dates. Some claims still combine several facts or repeat
investigation details. These are unresolved model-quality findings, not passing
clinical checks. Do not interpret the updated prompt or reference validation as
proof of accuracy or completeness. Real clinical use remains disabled.

The selected output is preserved in
[the direct trial artifact](quality/2026-09-15/selected-NHSSYN001-trial.json).
Its source IDs use the ordered committed seed corpus (`SyntheticNotes.notes`),
not the development database's assigned note numbers. The output can be checked
against the source text produced by `compare_openrouter.py`.

## What was compared

| Experiment | Observed result | Decision |
| --- | --- | --- |
| 9B, whole record, temperature 0 | About 23 seconds, 22 claims; omitted history and qualifiers | Reject as a completeness improvement |
| 9B, temperature 0.2 | Some calls rate-limited; smaller batches retained more detail but repeated the presentation | Insufficient improvement |
| 9B with Qwen's suggested non-thinking sampling | Lost several source-specific facts; coverage disagreed with citations | Reject |
| 9B, reasoning on, temperature 0 | Exhausted 16,384 output tokens after 106 seconds | Stopped trial; no partial output accepted |
| 27B, reasoning on, temperature 0.6 | About 155 seconds; retained both discharge drugs but omitted management details | Reject as a speed/quality improvement |
| 35B-A3B, temperatures 0/0.2, reasoning off | About 26–31 seconds; inconsistent history and medication-conflict retention | Reject |
| 35B-A3B, reasoning on | About 38 seconds; omitted qualifiers and management details | Reject |
| 397B-A17B, reasoning on | About 158 seconds; no consistent improvement, identity returned | Reject |
| 397B-A17B, reasoning off, revised prompt | About 54–60 seconds; less repetition and explicit discharge discrepancy | Current experimental readability choice, with limitations above |
| Earlier-claim memory between 9B batches | Copied earlier facts onto unrelated later citations | Removed from implementation |

Models, prompts and settings changed between exploratory trials, so these are not
controlled comparisons, repeated-run averages or estimates of accuracy. Provider
rate-limit waits are included in wall-clock durations; model inference time and
reported token usage are kept separately when available. Larger context alone
was not reliably better, and greater model size or reasoning did not guarantee
better retention. No model-generated prose was manually corrected for scoring.

[Run records](quality/2026-09-15/exploratory-runs.json) and prompt variants in that
directory preserve accepted and rejected trials. Early trial failures do not all
include complete parameter metadata; filenames identify those exploratory variants.
The abandoned cross-batch memory implementation is not part of the final gateway.

## Reproducing a current trial

Follow [OpenRouter setup](OPENROUTER.md), then run:

```bash
python3 tools/ai-clinical-summary-draft/compare_openrouter.py \
  --fixture NHSSYN001 --temperature 0.2 --request-bytes 50000 \
  --output /tmp/nhssyn001-quality.json
```

The command uses the privately configured key and current prompt, forces the local
cache off, verifies source text against the committed synthetic corpus, and saves
an owner-only report with sources, output, prompt hash, settings, timing and token
usage/cost. It makes paid requests and does not change the running gateway.
Provider-side prompt caching may still occur; it is distinct from reusing a final
response. Repeat with NHSSYN002/003 and compare facts, dates, negation, medication
regimens, follow-up, omissions and citation support manually. Structural success
alone is not a clinical pass.

Coverage citation status is now computed from the returned claim references.
Every source must still have exactly one model review; unknown citations, unknown
or duplicate source reviews, invalid section references, malformed output and
missing source reviews remain errors. This corrects metadata only and does not
repair, discard or certify the clinical statements.

# Section passes vs. one whole-record pass — 2026-09-18

Another exploratory synthetic-fixture comparison on NHSSYN001, not a clinical
validation. Both trials used `qwen/qwen3.5-397b-a17b` via Venice at temperature
0.2, reasoning off, 50,000 request bytes and the same prompt
(`1890e1d0175060f8fdd8c34a64077340de79690f3fa43ce9445bb96a6d389c57`). The earlier
`selected-NHSSYN001-trial.json` predates the recorded prompt hash and settings, so
the control was re-run rather than reused.

| Trial | Passes | Wall | Claims | Cost | Result |
| --- | --- | --- | --- | --- | --- |
| A, one whole-record pass | 1 | 83.0 s | 26 | $0.0224 | Structurally valid |
| B, five section passes, 180 s timeout | 4 of 5 | 180.1 s | — | $0.0570 | Rejected |
| B2, five section passes, 300 s timeout | 5 | 121.1 s | 70 | $0.0761 | Structurally valid |

Trial B failed because a single pass exceeded the 180-second per-request timeout,
not the 540-second gateway budget. Raising `timeout_seconds` to the configured
maximum of 300 resolved it. Per-section durations confirm the longest-first
scheduling comment: results and observations 65.4 s, plan and follow-up 50.5 s,
clinical overview 37.9 s, active problems 32.7 s, medications and allergies 28.6 s.
Section passes cost about 3.4 times the control and took about 1.5 times its wall
time. Claim count is not a measure of factual recall.

## What section passes improved

- **Results and observations went from 3 to 27 claims.** The control dropped
  almost every daily vital-sign set and examination from 08–12 January; the
  section pass retained each day's heart rate, blood pressure, respiratory rate,
  temperature and oxygen saturation alongside the neurological, cardiovascular,
  respiratory and abdominal examinations.
- **Planned and completed actions stayed distinct.** The section pass separated
  nimodipine 60 mg "planned for initiation" at 16:30 in note-5/note-6 from
  "was started" in note-7, and consistently framed discharge as a plan. This was
  the first limitation recorded on 2026-09-15.
- **The two discharge-day GP contacts were no longer merged.** The control folded
  note-18 and note-19 into one claim and lost note-19's "no changes to meds or
  treatment plan"; the section pass kept them separate.
- Both trials flagged the nimodipine/amlodipine discharge conflict explicitly.

## What section passes regressed

- **A dated fact became wrong.** note-12 carries source date 2026-01-09, and the
  control rendered it correctly. The section pass rendered the same rehab finding
  as 12/01/26 in *both* active problems and results, while its own plan section
  said 09/01/26 — an internal contradiction from two independent passes. The host
  already knows each source's authoritative date, so letting the model restate
  dates is the root cause rather than a prompt wording problem.
- **Cross-section duplication appeared.** Eleven near-duplicate claim pairs across
  sections (token Jaccard at or above 0.30 with a shared cited source) versus none
  in the control. `pipeline.merge` only merges byte-identical normalized text, so
  differently worded restatements of one fact survive in two sections.
- **Date formats became inconsistent**, mixing `dd/mm/yy` and ISO between passes;
  the control used ISO throughout.
- **Staff-name leakage rose from three claims to five.** Both trials violate the
  prompt's instruction to exclude staff names and licence numbers, so this is a
  pre-existing prompt-compliance failure that section passes widen rather than
  cause. It should be enforced by the host, not requested of the model.
- **Coverage reasons became redundant**, repeating the source ID about six times
  per entry, because `label_source_reviews` prefixes each pass's reason and
  `merge` prefixes the assembled result again.

## What neither trial fixed

note-8 records its GP contact as `07/21/26` in the body while its source date is
2026-01-07. Both trials silently normalized to the source date and neither
surfaced the discrepancy, so the 2026-09-15 limitation about the GP contact's
inconsistent dates is unchanged.

## Decision

Section passes are a genuine completeness gain for results, observations and
plan-versus-done wording, but they are **not promotable as they stand**: they
introduce a factual date error the single pass got right, plus material
duplication, at roughly triple the cost. The next changes worth trying are
host-side rather than prompt-side — anchor every claim's date to the known source
date instead of accepting model-emitted dates, add semantic (not byte-identical)
duplicate suppression to `merge`, strip the duplicated source-ID labelling, and
enforce the staff-name prohibition in the host validator. No clinician has
reviewed any of this prose and real clinical use remains disabled.

Run records are in [quality/2026-09-18/exploratory-runs.json](quality/2026-09-18/exploratory-runs.json)
with the two rendered artifacts beside them. Raw reports embed the full source
text and stay owner-only and uncommitted (`*-raw.json`); the committed corpus
under `.devcontainer/db/scripts/nhs-synthetic/` remains the source of truth.

# Making 27B work — 2026-09-18

The eval harness names Qwen 27B the promotion authority
(`../ai-clinical-summary-eval/README.md`), but the 09-15 hosted sweep rejected it
in one row at reasoning on / temperature 0.6 and moved the gateway default to
397B-A17B. 27B was never retried at the reasoning-off, temperature-0.2 settings
that made 397B look good, so that rejection did not compare like with like.
This run retests 27B properly. Venice does not serve 27B; SiliconFlow, DeepInfra
and Phala each advertise structured outputs and appear in the ZDR catalogue.

Scoring is now deterministic. `score_nhssyn001.py` checks a 20-fact labelled
ledger (11 critical) plus four defect classes seen on 09-18: a date asserted by a
claim that none of its cited sources carries, staff-name and patient-identity
leakage, cross-section duplication, and mixed date formats. It reproduces the
manual 397B findings exactly. It measures none of readability, and a passing gate
is an engineering result on one invented record, not clinical validation.

| Run | Provider | Temp | Mode | Claims | Recall | Critical | Dates | Leaks | Dups | Gate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C1 committed prompt | SiliconFlow | 0.2 | single | 29 | 0.95 | 1.00 | 0 | 2 | 0 | fail |
| C2 role-only prompt | SiliconFlow | 0.2 | single | 31 | 0.95 | 1.00 | 0 | 0 | 0 | **pass** |
| C3 role-only prompt | Phala | 0 | single | 20 | 0.90 | 0.91 | 1 | 0 | 0 | fail |
| C4 role-only prompt | SiliconFlow | 0 | single | 29 | 1.00 | 1.00 | 0 | 0 | 0 | **pass** |
| C5 role-only prompt | Phala | 0.2 | single | 26 | 1.00 | 1.00 | 0 | 0 | 0 | **pass** |
| C6 C4 repeated | SiliconFlow | 0 | single | 27 | 1.00 | 1.00 | 0 | 0 | 0 | **pass** |
| C7 section passes | DeepInfra | 0 | sections | 53 | 1.00 | 1.00 | 0 | 4 | 14 | fail |
| C8 section passes | SiliconFlow | 0 | sections | — | — | — | — | — | — | rejected |

## The name leak was a prompt contradiction, not a model limitation

The committed prompt said "Keep who contacted whom and what was communicated
accurate; omit staff names and license numbers." Those two clauses pull against
each other, and both 27B and 397B resolved them by naming people. Replacing the
line with an explicit role-only rule and a worked example removed the leak in
every single-pass run that used it (C2–C6, four passes, zero leaks). No model or
sampling change was needed, and 397B leaked under the same contradiction, so this
was never about model size.

## 27B is better than 397B here, and cheaper

On the same gate, 397B single-pass scored 3 name leaks and 397B section passes
scored 3 date errors, 5 leaks, 11 duplicates and mixed formats. 27B single-pass
with the role-only prompt passes cleanly at full labelled-fact recall, for
about $0.0092–0.0098 against 397B's $0.0224, in 95–150 seconds. C4 and C6 repeat
the same configuration with identical gate results.

**Recommended configuration:** `qwen/qwen3.5-27b`, SiliconFlow, temperature 0,
reasoning off, single whole-record pass, role-only prompt. Temperature 0 is also
the reproducible choice. Phala at temperature 0 (C3) was the one single-pass
failure, dropping a critical medication fact and misdating a claim, so provider
is a real variable and SiliconFlow is the current pick.

## Section passes fail at 27B too

C7 kept 4 name leaks and produced 14 cross-section duplicates, worse than 397B's
11. Note that C7's leaks are not a fair test of the fix: section passes read
`section-prompt.txt`, which `--prompt` does not touch, so that run still used the
old contradictory rule. C8 patched both prompts and was **rejected outright** —
all five passes returned, but the merged result failed with "Missing, unknown or
duplicate source review". The duplication is independent of prompt wording,
because `pipeline.merge` only merges byte-identical normalized text.

Section passes are therefore not a 397B-specific problem. They remain unsuitable
in both models, and the single whole-record pass is the better configuration.

## What this gate does not cover

Readability, and the plan-versus-completed distinction. C4 still writes "On
2026-01-12, the patient was discharged with a plan to take nimodipine...", which
reads as a completed discharge from what the source records as a discharge plan —
the first limitation recorded on 09-15, still unfixed and invisible to these
checks. The ledger is also not difficult enough to separate good candidates:
397B, and 27B in four configurations, all reach full or near-full recall. Neither
trial flags note-8's `07/21/26` body date. No clinician has reviewed any of this
prose, and real clinical use stays disabled behind both default-off flags.

# Validating 27B across all three patients — 2026-09-18

Tuning against NHSSYN001 alone risks fitting the prompt to one record, so this
round validates over all three synthetic patients: demographic-3001 (RCVS,
19 notes, medical), demographic-3002 (elective total knee replacement, 20 notes,
spanning a pre-op clinic two weeks before admission) and demographic-3003
(spontaneous pneumomediastinum, 17 notes, heavily abbreviated).

Two harness bugs blocked this. `compare_openrouter.py` labelled every fixture's
sources `demographic-3001`, so 002 and 003 would have been summarised under the
wrong patient, and note IDs were numbered from a global index so 002 began at
`note-20`. Each fixture now carries its own patient ID and numbers its notes from
one within its own chart.

`score_nhssyn001.py` is now `score_nhs_fixture.py` with a ledger per fixture under
`quality/facts/`, each 20 labelled facts. It adds a **forbidden assertion** check
for things the record does not support, which closes the plan-versus-completed
gap the 09-18 gate could not see.

## The gate caught the previous recommendation

Adding the forbidden-assertion check immediately failed C4, the configuration
recommended earlier the same day: it wrote "the patient **was discharged** with a
prescription for nimodipine". All three records end with the patient still
admitted — 001 plans "Discharge today as fit for home", 002 says "Review tomorrow
for potential discharge", 003 says "cleared for d/c tmrw" — and no note records
the event. The earlier gate was too weak, not the earlier result too good.

## Two more prompt defects, both fixed by worked examples

The prompt already forbade inferring discharge **three times** (lines 31, 34, 61)
and 27B still asserted it. Abstract repetition did not work; a concrete example
did, exactly as with the staff-name contradiction:

- **P3** added a worked discharge example. The model adopted the given phrasing
  for intermediate days but still wrote "discharged home" on the final day.
- **P4** stated plainly that these records end while the patient is admitted, that
  this holds even when the last note plans discharge for that same day, and that
  "post-discharge" remains correct for what is arranged afterwards. This fixed it.
- **P5** added physiologically impossible values, after 002 reported note-9's
  corrupted observations verbatim as "HR 2 bpm ... RR 1 br/min". A heart rate of
  2 is incompatible with life, and the existing "omit ambiguous or garbled text"
  instruction did not cover implausible *numbers*. P5 removed it on both providers.

## Calibrating the scorer against labelled pairs

Two "duplicates" turned out to be scorer false positives: a repeat chest X-ray
*result* beside the *plan* for a later one, and an Hb result beside the iron
prescribed for it. Both shared a condition name and date without restating a
fact. Measured over labelled pairs, the false ones sat at 0.50 containment while
genuine 397B restatements ran 0.60–0.83, so duplication now requires Jaccard ≥
0.30 **and** containment ≥ 0.60. This drops two borderline true positives; the
397B section-pass control still reports nine.

The date check was likewise too strict, flagging a correctly derived date when a
note planned something for "tomorrow" or "in 48 hours" (and 003 writes "tmrw").
It now accepts those derivations. The 397B control still shows its three real
misdatings, so both refinements preserve the defects they were built to catch.

## Result

Nine P5 runs across three patients:

| Fixture | SiliconFlow | Phala | DeepInfra |
| --- | --- | --- | --- |
| 3001 RCVS | pass, pass | fail (1 date error) | — |
| 3002 knee replacement | pass | pass | pass |
| 3003 pneumomediastinum | pass | pass | — |

Eight of nine pass with full critical-fact recall. The single failure is Phala at
temperature 0 on 3001, claiming an MRI date its only cited note does not carry —
the second time Phala has failed where SiliconFlow passed. SiliconFlow is 4/4 at
full labelled-fact recall. DeepInfra also passed and was fastest at 62 seconds.

**Recommended configuration is unchanged except for the prompt:**
`qwen/qwen3.5-27b`, SiliconFlow, temperature 0, reasoning off, single
whole-record pass, at roughly $0.008–0.015 and 60–210 seconds per record. P5 is
now the committed `prompt.txt`, its Java mirror and `section-prompt.txt`, and
run `G1` confirms the committed prompt passes with no override.

## What is still not covered

The checks remain lexical. "The patient was discharged" is caught; "the patient
went home" would not be. Claim text still sometimes cites source IDs in prose
("note-17 documents..."), which the prompt discourages and the gate does not
measure, and readability is still unmeasured. The 003 ledger's
`conservative-management` fact is missed in some runs, so recall of 0.95 there is
run-to-run variation on a non-critical fact rather than a stable gap. Reporting a
physiologically impossible value is treated as a defect that must be omitted;
flagging it explicitly as a data-quality problem may be the better clinical
behaviour and is currently scored the same as asserting it. No clinician has
reviewed any of this prose, all three records are invented, and real clinical use
remains disabled behind both default-off flags.

# Correcting a harmful rule: never judge clinical plausibility — 2026-09-18

The P5 rule added earlier today told the model that a "physiologically impossible"
number, such as a heart rate of 2, should be dropped. **That rule was wrong and
made the output less safe.** It is withdrawn.

It fails on principle first. Deciding which recorded values are too extreme to
report is a clinical judgement, and the extreme values are frequently the ones
that matter: a rate of 25 in complete heart block, a respiratory rate of 4 in
opioid toxicity, a temperature of 28 °C in hypothermia. A rule phrased as
"implausible" hands the model an unbounded, unauditable licence to delete exactly
the observations most likely to signal a deteriorating patient. Nothing in the
prompt defined a boundary, because no defensible boundary exists.

It also failed in practice. On NHSSYN002, note-9 records `HR 2, BP 124/78, RR 1,
Temp 36.8, SpO2 98`:

| Prompt | Provider | What happened to that observation set |
| --- | --- | --- |
| P4, before the rule | Phala | Reported in full: "HR 2 bpm, BP 124/78 mmHg, RR 1 br/min" |
| P5, with the rule | SiliconFlow | Reported the rest and flagged the two values — the best behaviour |
| P5, with the rule | Phala | **The entire observation set disappeared** |
| P5, with the rule | DeepInfra | **The entire observation set disappeared** |

Two of three providers silently deleted a complete set of vital signs, and the
gate scored all three as passing, because the check penalised *mentioning* the
values rather than *losing* them.

## The corrected rule and check

**P6** replaces suppression with its opposite: never delete, round, correct or
replace a recorded number because it looks extreme, never substitute or carry a
value over, and where a value is inconsistent with the rest of the same
observation set, report the value **and** say it is inconsistent. Judging
plausibility is explicitly not licensed.

The check is inverted to match. `garbled-vitals` is gone; `ward-round-vitals-retained`
is now a **critical fact**, so losing the values fails the gate and reporting
them, flagged or not, passes. Re-scored under it, the two P5 deletions fail and
both faithful renderings pass.

## A second, more serious conflict this surfaced

Investigating the vitals exposed something the ledger had missed entirely. On
05/01/26 NHSSYN002 records **two thromboprophylaxis agents**: tinzaparin 4,500
units SC once daily in note-7/note-8, and "start enoxaparin 40 mg SC once daily"
in note-9, with no note recording either being stopped. Under P6 one provider
flagged the conflict, another listed both regimens as unrelated routine claims,
and DeepInfra asserted the patient "was switched to Enoxaparin" — inventing a
resolution the record does not document, which the prompt already forbade.

**P7** adds the generic pattern, deliberately not naming this fixture's drugs:
two notes prescribing different drugs for the same purpose, with no recorded
stop, are an unresolved conflict even when the later note says "start"; both must
appear in one claim that says the records conflict; "switched to", "changed to"
and "replaced by" assert an undocumented substitution. Under P7, SiliconFlow
produces:

> Medication records conflict regarding thromboprophylaxis: note-9 and note-12
> prescribe enoxaparin 40 mg SC OD, while note-7 and note-8 prescribe tinzaparin
> 4,500 units SC OD starting the evening of 05/01/26, with no documented
> discontinuation of either agent.

`thromboprophylaxis-conflict` and `assumed-anticoagulant-switch` are now a
critical fact and a forbidden assertion, so this cannot silently regress.

## Final state

Committed prompt (P7), `qwen/qwen3.5-27b`, SiliconFlow, temperature 0, reasoning
off, single whole-record pass:

| Patient | Claims | Fact recall | Critical recall | Cost | Time | Gate |
| --- | --- | --- | --- | --- | --- | --- |
| 3001 RCVS | 28 | 1.00 | 1.00 | $0.0093 | 125 s | pass |
| 3002 knee replacement | 62 | 1.00 | 1.00 | $0.0151 | 176 s | pass |
| 3003 pneumomediastinum | 29 | 0.95 | 1.00 | $0.0085 | 91 s | pass |

**DeepInfra is no longer recommended.** It consistently returns far shorter
summaries — 26 to 28 claims where SiliconFlow returns 52 to 62 on the same record
— and lost both the observation set and the anticoagulant conflict. It is the
fastest endpoint and the least complete; speed was hiding omission.

## Remaining limits

The corrected rule asks for inconsistency to be *noted*, and runs vary in whether
they do: the gate only enforces that values are retained, not that inconsistency
is called out. Claim text still cites source IDs in prose ("note-9 and note-12
prescribe..."), which the prompt discourages and nothing measures. The checks stay
lexical. The P7 conflict rule was written as a generic pattern rather than from
this fixture's drugs, but it was still authored after seeing the failure, so it
needs an unseen record to count as validated. No clinician has reviewed any of
this prose, all three records are invented, and both flags remain default-off.

# The prompt did not fit the request budget — 2026-09-18

Promoting the corrected prompt broke the default generation path, and the Python
suite caught it. The prompt is a fixed cost paid on every pass, so it competes
with clinical text for the per-request budget, which was 10,000 bytes in both
`ClinicalSummaryAgent.requestBytes()` and `pipeline.REQUEST_BYTES`. Every trial
here had run through OpenRouter at 50,000 bytes, so none of it surfaced.

Measured against the committed fixtures at 10,000 bytes:

| Prompt | 3001 | 3002 | 3003 |
| --- | --- | --- | --- |
| pre-session, 6,230 B | 13 passes | 17 passes | 9 passes |
| corrected, 7,976 B | 30 passes | **fails** | **fails** |

`pipeline.split` will not divide a source below 1024 characters, so a note too
large to fit beside the prompt but too small to split cannot be planned at all.
The worst real case is NHSSYN003 note-12 at 985 characters, which capped the
prompt at about 7,318 bytes — below what the corrected prompt needs.

## Condensing the prompt was the wrong fix

Three attempts to fit the budget all lost clinical content, because what looked
like redundancy was reinforcement this model depends on:

| Prompt | Size | Claims on 3002 | Outcome |
| --- | --- | --- | --- |
| P7 corrected | 7,976 B | 56–62 | passes, exceeds budget |
| P9 consolidated | 6,572 B | 51 | lost the anticoagulant conflict |
| P10 | 6,650 B | 29 | lost the conflict and the vitals |
| P11 | 6,963 B | 24 | lost both; 3003 refused outright |

Merging the duplicated medication-conflict block and the repeated completeness
instructions cut claim counts by more than half. The repetition was load-bearing.

## Raising the budget instead

`ClinicalSummaryAgent.requestBytes()` now defaults to 16,000, mirrored by
`pipeline.REQUEST_BYTES`. That is roughly 4,000 tokens against the Ollama
adapter's configured 16,384-token context less its 4,096-token output budget, so
it fits comfortably. It also cuts passes sharply: 4, 5 and 3 for the three charts
rather than 13, 17 and 9 at the old budget with the shorter prompt, which means
fewer prompt re-sends per summary.

The change was verified by inspection rather than execution, because `mvn test`
fails in this environment on an unrelated dependency-lock integrity mismatch.
Each Java reference to the old value was checked: the assertion at
`AiClinicalSummaryPrototypeAgentUnitTest:179` covers the **HTTP** adapter's
`http.requestBytes` property default and is unaffected; `:160` calls the real
default without asserting it, and 16,000 remains inside the permitted range;
`AiClinicalSummaryPrototypePipelineUnitTest` uses the pipeline floor constant and
explicit values. **The Java tests still need to be run once the lock issue is
resolved.**

The protocol floor stays at 10,000 so existing HTTP adapter configurations remain
valid, which leaves a real edge: an operator who configures 10,000 with this
prompt gets a failure. It now says so — the error names the budget and the prompt
instead of reporting an unexplained "minimal source portion" problem — and a
regression test plans all three committed fixtures at the default budget, so
future prompt growth fails in the suite rather than at runtime.

## Final validated configuration

`qwen/qwen3.5-27b`, SiliconFlow, temperature 0, reasoning off, single
whole-record pass, committed prompt, 16,000-byte budget:

| Patient | Claims | Fact recall | Critical recall | Cost | Gate |
| --- | --- | --- | --- | --- | --- |
| 3001 RCVS | 28 | 1.00 | 1.00 | $0.0093 | pass |
| 3002 knee replacement | 56 | 1.00 | 1.00 | $0.0150 | pass |
| 3003 pneumomediastinum | 31 | 0.95 | 1.00 | $0.0086 | pass |
