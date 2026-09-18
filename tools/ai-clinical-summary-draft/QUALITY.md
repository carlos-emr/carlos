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
