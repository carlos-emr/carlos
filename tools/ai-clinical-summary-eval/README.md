# Clinical summary optimization harness

This synthetic-only harness compares prompt and generation settings against the
same prompt, output schema, and Python validator as the clinical-summary draft.
Safety gates run before latency ranking: a candidate is eligible only when every
run passes, and the fastest eligible candidate is selected.

The labelled cases cover medication conflict, repetitive discharge notes,
one-source claims, and adversarial administrative content. Checks require atomic
claims, all critical facts, at least 90% of all required facts, relevant citations,
correct sections and coverage, and no unsupported, excluded-source, or forbidden claims.
Each fact lists its clinically reasonable destination sections, so equivalent
placement is accepted without weakening medication and plan boundaries.
These are repeatable engineering checks over invented records. They do not
establish clinical validity.

The initial matrix separates output limits from prompt behavior: the first
three candidates vary claim, character, context, and generation budgets; the
`focused-12x180` candidate keeps the compact budgets and adds a final instruction
against unsupported missing-content filler. Add future candidates to the
campaign JSON rather than changing historical result files.

The separate `optimize-2b.json` matrix holds limits constant at 10 claims,
180 characters, 8,192 context tokens, and 1,024 output tokens. It isolates a
missing-content deletion checklist, a claim-boundary/conflict example, and their
combination from the budget change. This is a screening experiment only and is
not included in either 27B campaign.

`optimize-ledger-2b.json` is a second, explicitly experimental screen. Its
`sources_and_fact_ledger` input mode gives the model CARLOS's host-owned ledger
as a closed claim whitelist. This tests a possible contract change without
changing the production Java adapter or the source-only default. The candidate
and input mode are hashed into each run's identity.

`optimize-host-structured-2b.json` narrows the experimental model output to
atomic ledger IDs and clinical wording. The harness deterministically creates
claim IDs, section membership, citations, and complete source coverage, then
assembles and validates the full artifact with the same validator used by the
standalone runtime. Raw model output is preserved separately as
`model-output.json`. Semicolon splitting is only an experiment over the synthetic
ledger; it is not a general clinical assertion splitter.

`optimize-evidence-2b.json` tests a broader host-structured contract over all
four cases. The model may add explicit facts beyond the incomplete ledger only
when it returns an exact contiguous quotation from every cited source. The host
verifies quotations and derives citations and coverage before full-artifact
validation. This remains an experiment rather than a production contract.

`optimize-delta-2b.json` makes the host-rendered atomic ledger the baseline and
asks the model only for material details missing from it. Sources absent from
the ledger are not exposed to the model, while the host still records them in
coverage. Additions require verified exact quotations. This minimizes the 2B
generation workload without treating the current ledger as complete.
`optimize-delta-2b-final.json` keeps model-supplied section IDs only as schema
guidance. The host assigns accepted delta sections, discards imaging deltas,
aggregates separately emitted BP/oxygen observations, and removes a nonclinical
discharge-planning preamble from an otherwise supported follow-up claim.
Before inference, the host compares eligible measurement and follow-up signals
with the ledger and skips the model entirely when no supported delta is possible.
`optimize-delta-2b-stability.json` repeats that final candidate twice at three
seeds over every case; only rows with an eligible delta invoke the model.
`checkpoint-27b.json` is the next promotion gate. It compares a full-summary
control with the broader host-ledger/evidence-delta contract over three holdout
cases that were not used during 2B tuning. Both candidates use the same 8,192
context and 1,024 output-token budgets.

`optimize-normalized-delta-2b.json` and `checkpoint-normalized-27b.json` retain
the filtered-source, exact-quotation delta contract but move final section
placement and same-source physiological-observation grouping to deterministic
host code. The host accepts only recognizable results, medication actions, and
plans, rejects quotation/class mismatches, and suppresses near-duplicate ledger
facts. `validation-normalized-27b.json` exercises the same frozen candidate on
separate renal-monitoring and thyroid-adjustment cases.
`stability-normalized-27b.json` adds two further seeds over the three regression
cases after the single-seed checkpoint succeeds.

## Campaign tiers

Qwen 2B is an iteration proxy. Qwen 27B remains the authority for promotion:

```bash
# Inspect the complete experiment matrix without contacting Ollama.
python3 tools/ai-clinical-summary-eval/run_campaign.py --dry-run

# Fast local screening with the installed 2B model.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/smoke-2b.json

# Prompt-focused 2B screening before choosing anything for 27B.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-2b.json

# Experimental ledger-grounded 2B screening (not the production contract).
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-ledger-2b.json

# Experimental host-structured 2B screening (not the production contract).
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-host-structured-2b.json

# Evidence-grounded host structure across all four 2B cases.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-evidence-2b.json

# Lean host-ledger baseline plus quotation-backed additions.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-delta-2b.json

# Focused final 2B candidate over every case.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-delta-2b-final.json

# Repeat the final 2B candidate across seeds before considering 27B.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/optimize-delta-2b-stability.json

# Compare the full-summary and host-ledger contracts on unseen 27B holdouts.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/checkpoint-27b.json

# Check the host-normalized delta on the 27B regression cases.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/checkpoint-normalized-27b.json

# Run the two additional generalization cases.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/validation-normalized-27b.json

# Confirm the normalized candidate over two additional 27B seeds.
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/stability-normalized-27b.json

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
Markdown report. Run metadata pins the candidate, prompt, schema, case, and scorer
hashes. Resume recomputes and compares every identity field before reusing a
completed row; any change requires a new campaign so historical results are not
silently mixed or reinterpreted. To resume without repeating completed runs:

```bash
python3 tools/ai-clinical-summary-eval/run_campaign.py \
  --config tools/ai-clinical-summary-eval/campaigns/full-27b.json \
  --candidate concise-16x200 \
  --output tools/ai-clinical-summary-eval/runs/<timestamp> --resume
```

The runner uses numeric loopback, ignores HTTP proxies, and rejects cloud-backed
Ollama models. Duplicate-key or malformed responses are rejected and recorded as
failed rows without aborting the remaining matrix. After a transport error or
timeout, it writes a partial report and stops: Ollama can continue a request after
the client disconnects, so restart Ollama and begin a new campaign before
collecting more latency data. The runner never pulls or starts a model.
Each generation request uses `keep_alive: 0`, forcing Ollama to unload the model
after the response. This deliberately trades repeated load time for bounded
memory and independent timings on machines that cannot safely retain Qwen 27B
plus its prompt cache. Candidate ranking excludes model-load time while retaining
wall time in the report.
Only invented records belong in case files or run artifacts.
The 2B smoke profile records any row exceeding ten minutes as a failed candidate
and continues with the remaining matrix. It uses the medication-conflict and
one-source cases for quick screening; the 27B profiles add repetitive-note and
adversarial-source coverage.

## Candidate settings and promotion

Campaign JSON controls the model, seeds, repetitions, cases, and candidates.
Each candidate can vary `max_claims`, `max_chars`, `num_ctx`, `num_predict`, and
an optional `prompt_suffix`. Experimental campaigns can opt into
`sources_and_fact_ledger` or the paired `atomic_fact_ledger` and
`host_structured_claims` modes; ordinary candidates remain source-only. Limits may
only tighten the current 20-claim,
240-character, 65,536-context and 4,096-output-token runtime bounds.

The default staged search compares the current limits with 16-by-200 and
12-by-180 alternatives. One-source claims remain valid; a fact requires multiple
citations only when its labelled evidence includes multiple relevant sources.
Latency selection uses Ollama's prompt-evaluation plus generation durations so
candidate ordering and model reloads do not bias the winner. Wall time remains in
the report as the user-visible cold/warm operational measurement.
Do not promote a 2B winner directly. A setting must pass the 27B smoke campaign
and three full-campaign repetitions before the runtime prompt, schema, validator,
and Java constants are changed together.

## Tests

```bash
python3 -m unittest discover -s tools/ai-clinical-summary-eval/tests -v
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -v
```
