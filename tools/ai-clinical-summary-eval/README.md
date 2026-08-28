# CARLOS AI summarizer benchmark

This benchmark tests omission-sensitive, clinician-facing summarization using a
synthetic longitudinal patient record. `authoritative-facts.json` and
`validate_summary.py` provide deterministic checks for demographics, problems,
medication and allergy conflicts, dated results, explicit negatives, pending
actions, scheduled events, wrong-patient exclusion, citations, and source
coverage.

Run a model through Ollama:

```bash
python3 run_ollama_benchmark.py \
  mistral-small3.2:24b-instruct-2506-q4_K_M \
  --host http://host.docker.internal:11434 \
  --label mistral-small3.2-24b
```

For a lower-memory first pass, test the 6 GB Ministral model before attempting
the 15 GB Mistral Small model:

```bash
python3 run_ollama_benchmark.py \
  ministral-3:8b \
  --host http://host.docker.internal:11434 \
  --label ministral-3-8b \
  --num-predict 2000
```

The runner refuses to start if less than 6 GiB of container memory is available
or if Ollama already has a model loaded. This prevents benchmark inference from
competing with active builds or another model. The threshold can be raised with
`--min-available-memory-gib`; `--skip-preflight` is available for deliberately
managed environments.

The default generation settings are conservative and reproducible:

- temperature: `0.15`
- top-p: `0.85`
- presence penalty: `0.0`
- seed: `42`
- context: `4096`
- maximum generated tokens: `2500`

Each run preserves the raw Ollama response, parsed draft, deterministic
validation report, and runtime metadata. A zero-violation result means the
machine-checkable facts passed; it does not replace manual review for
unsupported claims, clinical relevance, or readability.

## Versioned pipeline campaign

The v1 campaign compares Qwen3.5 27B and Mistral Small 3.2 24B over three
seeds. It contains the baseline Evelyn Carter case and six paired
counterfactual tests. Thinking is explicitly disabled so Qwen cannot consume
the structured-output budget with hidden reasoning. Inspect the complete
matrix without inference:

```bash
python3 run_campaign.py --dry-run
```

Run only the six baseline generations first:

```bash
python3 run_campaign.py \
  --host http://host.docker.internal:11434 \
  --cases baseline
```

Run the paired campaign after the baseline artifacts pass inspection:

```bash
python3 run_campaign.py \
  --host http://host.docker.internal:11434 \
  --cases fairness
```

Every campaign creates a new UTC-stamped directory under `runs/`. It never
overwrites historical results. Each run stores the raw response, parsed draft,
deterministic findings, constrained repair, post-repair findings, and runtime
metadata. `report.md` summarizes model findings and repeatability;
`counterfactual-discordance.json` retains every pair-level atomic difference.

Transient Ollama failures are retried three times. Resume an interrupted
campaign without repeating completed model/seed runs:

```bash
python3 run_campaign.py \
  --host http://host.docker.internal:11434 \
  --cases fairness \
  --output runs/20260826T002438Z \
  --resume
```

The counterfactual fixtures are engineering probes. Their exact demographic
statement is the only allowed pair difference. Any other atomic difference is
flagged for review, not automatically classified as bias or clinical harm.

## Deterministic checks

Run the validator and retrieval-integrity mutation tests without Ollama:

```bash
python3 -m unittest discover -s tests -v
```

The retrieval manifest checker fails closed for missing, truncated, duplicate,
wrong-patient, wrong-encounter, undeclared, and context-truncated inputs. The
summary validator checks required facts, closed-world high-risk sections,
enums, citations, wrong-patient leakage, pending/completed actions, and source
coverage. These tests are diagnostic engineering evidence and are not a
clinical-safety certification.

## Known-issues prompt experiment

`prompt-v3.txt` explicitly addresses the observed completed-action,
accessibility, remission-qualifier, and unquoted-source-ID failures. It uses a
schema-constrained `social_context` section so presence and absence are handled
symmetrically and `in_remission` cannot be reduced to a confirmed active
problem.

Run the focused baseline/disability/substance-use experiment with:

```bash
python3 run_campaign.py \
  --config cases/campaign-prompt-v3.json \
  --host http://host.docker.internal:11434 \
  --cases all
```

This first iteration uses one seed and both candidate models (10 runs). Compare
it with the preserved v2 artifacts before promoting the prompt to a three-seed
campaign. Prompt wording, authoritative facts, counterfactual expectations, and
the output JSON Schema are separate versioned files.

### Qwen safety prompt and independent self-check

`prompt-v4.txt` adds source-by-source required-information bundles and a final
silent omission audit. `self-check-v4.txt` is a second, independently prompted
pass that re-reads the original records, the first draft, and deterministic
validator findings before returning a complete corrected summary. The final
summary is still rejected unless deterministic validation passes; model
self-certification is never the release gate.

```bash
python3 run_campaign.py \
  --config cases/campaign-prompt-v4.json \
  --host http://host.docker.internal:11434 \
  --cases all
```

The initial experiment runs five Qwen cases with one seed. Each case performs
two generations (draft and self-check), preserving both artifacts so omissions
fixed or introduced by self-review remain auditable.
