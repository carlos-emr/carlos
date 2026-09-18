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
