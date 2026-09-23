# Single-document model comparison — 2026-09-23

Exploratory CARLOS EMR comparison of the current Qwen3.5-35B-A3B/Parasail stack with
Qwen3-30B-A3B-Instruct-2507/SiliconFlow and Nemotron-3-Nano-30B-A3B/Crusoe, through
OpenRouter. The running gateway configuration was not changed.

## Results

| Model / provider | Accepted drafts | Median latency, all attempts | Latency range | Cost for 12 attempts |
| --- | ---: | ---: | ---: | ---: |
| Qwen3.5-35B-A3B / Parasail (current) | 8/12 | 3.58 s | 1.52–9.11 s | $0.009568 |
| Qwen3-30B-A3B-Instruct-2507 / SiliconFlow | 9/12 | 24.22 s | 10.18–56.92 s | $0.003234 |
| Nemotron-3-Nano-30B-A3B / Crusoe | 5/12 | 2.36 s | 1.05–5.89 s | $0.002154 |

All 36 attempts returned complete outputs; every rejection was an output-validation failure.
The 36 measured attempts cost **$0.014956 total**. Median latency among accepted drafts was
3.35 s, 23.40 s, and 1.94 s respectively. Per-attempt measurements, usage, hashes, and errors
are preserved in [the metrics artifact](quality/2026-09-23/document-moe-metrics.json).

| Note | Current Qwen3.5 | Qwen3-30B | Nemotron |
| --- | ---: | ---: | ---: |
| Triage | 2/2 | 1/2 | 0/2 |
| Investigations | 2/2 | 2/2 | 1/2 |
| Multiline assessment | 0/2 | 0/2 | 0/2 |
| Medication plan | 2/2 | 2/2 | 2/2 |
| Postoperative plan | 2/2 | 2/2 | 2/2 |
| Discharge plan | 0/2 | 2/2 | 0/2 |

Keep the current default for now. Qwen3-30B had slightly better validation acceptance and lower
cost, but its hosted median latency was about 6.8 times higher, and manual review did not show a
clear quality advantage. Nemotron was fastest and cheapest but had the lowest acceptance and
an accepted draft that overstated a discharge plan. None reliably handled the multiline assessment.
These findings support further prompt/evidence work rather than declaring any model clinically
validated. Two repetitions on six notes do not establish a statistically reliable ranking.

## Method

Six complete notes from the committed NHS synthetic corpus, selected before the comparison,
cover triage, investigations, a multiline assessment, a short medication plan, a postoperative
plan, and discharge planning. Inputs range from 200 to 2,464 characters. Each model sees each
note twice, with model order rotated: 36 attempts. These are invented records, not patient data.

All use the production document prompt, JSON schema, and strict validator, temperature 0,
a 4,096-token output cap, a 90-second transport timeout, no output cache, and no repair or
prompt tuning. Providers are pinned with no fallback, `require_parameters=true`,
`data_collection=deny`, and `zdr=true`. Upstream input-prefix caching can still occur.

Qwen3 Instruct is non-reasoning. Its SiliconFlow route rejected the gateway's explicit
`reasoning.enabled=false` parameter; the evaluation transport omits that parameter for this
model only. Production code is unchanged. A default switch would require this compatibility
change. The other two models receive the normal reasoning-disabled request.

Latency includes gateway validation and transport. Different providers serve the models, so
these measurements cannot isolate architecture, GPU/CPU demand, or memory use. Costs are
OpenRouter's returned usage costs, including rejected drafts. Preliminary compatibility probes
are excluded from the comparison. This is a small smoke test, not a clinical accuracy study.

## Manual review

Review of first-repeat outputs against their source notes found:

- **Short medication plan:** all three preserved the planned IV-to-oral paracetamol switch,
  1 g four times daily, and ibuprofen 400 mg three times daily *if tolerated*. Both Qwen
  models produced three focused points. Nemotron added the pharmacist's name and registration
  number, contrary to the prompt.
- **Postoperative plan:** both Qwen models preserved review tomorrow for *potential* discharge.
  Nemotron changed this to “discharging the patient tomorrow” and omitted the numeric Hb result.
  Its output passed the validator: exact evidence plus lexical overlap does not prove that
  the summary preserves uncertainty or covers important facts. A follow-up check of Nemotron's
  second repeat found both the Hb result and potential-discharge wording preserved, but the
  summary expanded from 5 to 20 points, including staff identifiers. Temperature 0 did not
  make these hosted outputs identical.
- **Investigations:** the current Qwen model preserved completed CT, negative findings, and
  pending blood results. Nemotron omitted the explicit RCVS diagnosis. Qwen3-30B included the
  patient name in the overview and redundant staff identity points. All three included staff
  names despite instructions to use roles.
- **Multiline assessment:** both Qwen models described admission as completed where the source
  records acceptance by Neurology and a planned transfer. The source also has contradictory
  nausea statements; none explicitly highlighted that contradiction. Nemotron described CRP
  as normal, although the source gives 8 mg/L without explicitly labeling CRP normal. All three
  outputs were rejected, so these particular drafts would not be shown by the application.
- **Discharge plan:** the ambiguous source text “no OB” became “no other breathing issues” in
  the current Qwen output and “no other symptoms” in Nemotron's output. The latter broadens the
  claim. Altered excerpts also caused rejection in these two first-repeat drafts.

The current model is not an accuracy reference. Identifier restraint, uncertainty, contradictions,
and ambiguous source text need work across these models. Validation failure rates measure format
and provenance compliance; accepted drafts remain unverified.

## Reproduce

The existing owner-only OpenRouter runtime configuration supplies credentials. The command
incurs API charges and writes a private report containing synthetic source text and model output:

```bash
python3 tools/ai-clinical-summary-draft/compare_document_models.py \
  --output /path/to/private/document-moe-comparison.json
```

The script verifies that each request is exactly one complete committed synthetic note before
network access. It does not modify gateway settings. Its report includes source/prompt/schema
hashes, provider identities, usage costs, cached-token counts, individual validation failures,
and raw synthetic drafts. Credentials and reasoning traces are not written to the report.
