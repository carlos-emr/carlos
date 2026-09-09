# Clinical summary optimization harness

This synthetic-only harness compares prompt and generation settings against the
same prompt, output schema, and Python validator as the clinical-summary draft.
Safety gates run before latency ranking: a candidate is eligible only when every
run passes, and the fastest eligible candidate is selected.

The labelled cases cover medication conflict, repetitive discharge notes,
one-source claims, and adversarial administrative content. Checks require all
critical facts, at least 90% of all required facts, relevant citations, correct
sections and coverage, and no unsupported, excluded-source, or forbidden claims.
Each fact lists its clinically reasonable destination sections, so equivalent
placement is accepted without weakening medication and plan boundaries.
These are repeatable engineering checks over invented records. They do not
establish clinical validity.

The initial matrix separates output limits from prompt behavior: the first
three candidates vary claim, character, context, and generation budgets; the
`focused-12x180` candidate keeps the compact budgets and adds a final instruction
against unsupported missing-content filler. Add future candidates to the
campaign JSON rather than changing historical result files.

## Campaign tiers

Qwen 2B is an iteration proxy. Qwen 27B remains the authority for promotion:

```bash
# Inspect the complete experiment matrix without contacting Ollama.
python3 tools/ai-clinical-summary-eval/run_campaign.py --dry-run

# Fast local screening with the installed 2B model.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/smoke-2b.json

# Run only surviving candidates on the 27B smoke cases.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/smoke-27b.json \
  --candidate concise-16x200

# Run finalists three times over every case, normally overnight.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/full-27b.json \
  --candidate concise-16x200
```

Use `--case <id>` repeatedly to narrow a campaign. Runs are sequential. Each
new campaign writes to ignored `runs/<UTC timestamp>/` and preserves the request,
raw Ollama response, parsed draft, evaluation, metadata, JSON comparison, and
Markdown report. Run metadata pins the prompt, schema, case, and scorer hashes;
a changed scorer requires a new campaign so historical results are not silently
reinterpreted. To resume without repeating completed runs:

```bash
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/full-27b.json \
  --candidate concise-16x200 \
  --output tools/ai-clinical-summary-eval/runs/<timestamp> --resume
```

The runner uses numeric loopback, ignores HTTP proxies, and rejects cloud-backed
Ollama models. It never pulls or starts a model. Only invented records belong in
case files or run artifacts.
The 2B smoke profile records any row exceeding ten minutes as a failed candidate
and continues with the remaining matrix. It uses the medication-conflict and
one-source cases for quick screening; the 27B profiles add repetitive-note and
adversarial-source coverage.

## Candidate settings and promotion

Campaign JSON controls the model, seeds, repetitions, cases, and candidates.
Each candidate can vary `max_claims`, `max_chars`, `num_ctx`, `num_predict`, and
an optional `prompt_suffix`. Limits may only tighten the current 20-claim,
240-character, 65,536-context and 4,096-output-token runtime bounds.

The default staged search compares the current limits with 16-by-200 and
12-by-180 alternatives. One-source claims remain valid; a fact requires multiple
citations only when its labelled evidence includes multiple relevant sources.
Do not promote a 2B winner directly. A setting must pass the 27B smoke campaign
and three full-campaign repetitions before the runtime prompt, schema, validator,
and Java constants are changed together.

## Tests

```bash
python3 -m unittest discover -s tools/ai-clinical-summary-eval/tests -v
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -v
```
